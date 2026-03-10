package my.ssdid.wallet.feature.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.AuthVerifyRequest
import my.ssdid.wallet.domain.transport.dto.ClaimRequest
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import javax.inject.Inject

sealed class ConsentState {
    object Loading : ConsentState()
    object Ready : ConsentState()
    object Submitting : ConsentState()
    data class Success(val sessionToken: String = "") : ConsentState()
    data class Error(val message: String) : ConsentState()
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val verifier: Verifier,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val callbackUrl: String = savedStateHandle["callbackUrl"] ?: ""
    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val hasCallback: Boolean get() = callbackUrl.isNotEmpty()
    val isWebFlow: Boolean get() = sessionId.isNotEmpty()

    companion object {
        private const val MAX_REQUESTED_CLAIMS = 20
    }

    val requestedClaims: List<ClaimRequest> = run {
        val raw = savedStateHandle.get<String>("requestedClaims") ?: ""
        if (raw.isBlank()) emptyList()
        else try {
            json.decodeFromString<List<ClaimRequest>>(raw).take(MAX_REQUESTED_CLAIMS)
        } catch (_: Exception) { emptyList() }
    }

    private val acceptedAlgorithmNames: List<String> = run {
        val raw = savedStateHandle.get<String>("acceptedAlgorithms") ?: ""
        if (raw.isBlank()) emptyList()
        else try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private val _state = MutableStateFlow<ConsentState>(ConsentState.Loading)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    private val _selectedClaims = MutableStateFlow<Set<String>>(emptySet())
    val selectedClaims = _selectedClaims.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName = _serverName.asStateFlow()

    private var cachedChallenge: String? = null

    @Suppress("OPT_IN_USAGE")
    val hasAllRequiredClaims: StateFlow<Boolean> = _selectedIdentity
        .flatMapLatest { identity ->
            if (identity == null) flowOf(false)
            else flow {
                val requiredKeys = requestedClaims.filter { it.required }.map { it.key }
                if (requiredKeys.isEmpty()) {
                    emit(true)
                } else {
                    try {
                        val credential = vault.getCredentialForDid(identity.did)
                        val claims = credential?.credentialSubject?.claims ?: emptyMap()
                        emit(requiredKeys.all { !claims[it].isNullOrBlank() })
                    } catch (_: Exception) {
                        emit(false)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            val allIdentities = vault.listIdentities()
            val filtered = if (acceptedAlgorithmNames.isEmpty()) allIdentities
                else allIdentities.filter { it.algorithm.name in acceptedAlgorithmNames }
            _identities.value = filtered
            if (filtered.isNotEmpty()) _selectedIdentity.value = filtered.first()

            // All claims start selected
            _selectedClaims.value = requestedClaims.map { it.key }.toSet()

            // Fetch challenge early so server name shows before user approves
            if (serverUrl.isNotEmpty()) {
                try {
                    val serverApi = httpClient.serverApi(serverUrl)
                    val challengeResp = serverApi.getAuthChallenge()
                    _serverName.value = challengeResp.serverName
                    cachedChallenge = challengeResp.challenge
                } catch (_: Exception) {
                    // Challenge fetch failed — will retry on approve
                }
            }

            _state.value = ConsentState.Ready
        }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
        // Re-evaluate hasAllRequiredClaims by triggering the flow
    }

    fun toggleClaim(key: String) {
        val claim = requestedClaims.find { it.key == key } ?: return
        if (claim.required) return
        val current = _selectedClaims.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _selectedClaims.value = current
    }

    fun approve(biometricUsed: Boolean) {
        val identity = _selectedIdentity.value ?: run {
            _state.value = ConsentState.Error("No identity selected")
            return
        }
        viewModelScope.launch {
            _state.value = ConsentState.Submitting
            try {
                val serverApi = httpClient.serverApi(serverUrl)

                // Consume cached challenge (single-use nonce) or fetch fresh
                val challenge = cachedChallenge?.also { cachedChallenge = null } ?: run {
                    val resp = serverApi.getAuthChallenge()
                    _serverName.value = resp.serverName
                    resp.challenge
                }

                // Sign the challenge
                val signatureBytes = vault.sign(identity.keyId, challenge.toByteArray()).getOrThrow()
                val signedChallenge = Multibase.encode(signatureBytes)

                // Build shared claims — check required claims are present
                val sharedClaims = mutableMapOf<String, String>()
                val credential = vault.getCredentialForDid(identity.did)
                val claims = credential?.credentialSubject?.claims ?: emptyMap()

                val missingRequired = requestedClaims
                    .filter { it.required }
                    .filter { claims[it.key].isNullOrBlank() }
                if (missingRequired.isNotEmpty()) {
                    val missing = missingRequired.joinToString(", ") { it.key }
                    throw IllegalStateException("Missing required claims: $missing")
                }

                for (key in _selectedClaims.value) {
                    val value = claims[key]
                    if (value != null) sharedClaims[key] = value
                }

                // Build AMR
                val amr = mutableListOf("hwk")
                if (biometricUsed) amr.add("bio")

                // Submit to service
                val resp = serverApi.verifyAuth(
                    AuthVerifyRequest(
                        did = identity.did,
                        keyId = identity.keyId,
                        signedChallenge = signedChallenge,
                        sharedClaims = sharedClaims,
                        amr = amr,
                        sessionId = sessionId.ifEmpty { null }
                    )
                )

                // Mutual auth
                val verified = verifier.verifyChallengeResponse(
                    resp.serverDid, resp.serverKeyId,
                    resp.sessionToken, resp.serverSignature
                ).getOrThrow()
                if (!verified) throw SecurityException("Service authentication failed")

                _state.value = ConsentState.Success(resp.sessionToken)
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.value = ConsentState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    suspend fun requireBiometric(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return when (biometricAuth.authenticate(activity)) {
            is BiometricResult.Success -> true
            is BiometricResult.Cancelled -> false
            is BiometricResult.Error -> false
        }
    }

    fun buildCallbackUri(sessionToken: String): Uri? {
        if (callbackUrl.isEmpty()) return null
        return Uri.parse(callbackUrl).buildUpon()
            .appendQueryParameter("session_token", sessionToken)
            .appendQueryParameter("did", _selectedIdentity.value?.did ?: "")
            .build()
    }

    fun buildDeclineCallbackUri(): Uri? {
        if (callbackUrl.isEmpty()) return null
        return Uri.parse(callbackUrl).buildUpon()
            .appendQueryParameter("error", "user_declined")
            .build()
    }
}
