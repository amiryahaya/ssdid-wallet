package my.ssdid.wallet.feature.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val requestedClaims: List<ClaimRequest> = run {
        val raw = savedStateHandle.get<String>("requestedClaims") ?: ""
        if (raw.isBlank()) emptyList()
        else try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
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

    // Map of claim key -> whether it's currently selected
    private val _selectedClaims = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val selectedClaims = _selectedClaims.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName = _serverName.asStateFlow()

    init {
        viewModelScope.launch {
            val allIdentities = vault.listIdentities()
            val filtered = if (acceptedAlgorithmNames.isEmpty()) allIdentities
                else allIdentities.filter { it.algorithm.name in acceptedAlgorithmNames }
            _identities.value = filtered
            if (filtered.isNotEmpty()) _selectedIdentity.value = filtered.first()

            // All claims start selected
            _selectedClaims.value = requestedClaims.associate { it.key to true }

            _state.value = ConsentState.Ready
        }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
    }

    fun toggleClaim(key: String) {
        val claim = requestedClaims.find { it.key == key } ?: return
        if (claim.required) return
        val current = _selectedClaims.value.toMutableMap()
        val isSelected = current.containsKey(key)
        if (isSelected) current.remove(key) else current[key] = true
        _selectedClaims.value = current
    }

    fun approve(biometricUsed: Boolean) {
        val identity = _selectedIdentity.value ?: return
        viewModelScope.launch {
            _state.value = ConsentState.Submitting
            try {
                val serverApi = httpClient.serverApi(serverUrl)

                // Fetch challenge
                val challengeResp = serverApi.getAuthChallenge()
                _serverName.value = challengeResp.server_name

                // Sign the challenge
                val signatureBytes = vault.sign(identity.keyId, challengeResp.challenge.toByteArray()).getOrThrow()
                val signedChallenge = Multibase.encode(signatureBytes)

                // Build shared claims
                val sharedClaims = mutableMapOf<String, String>()
                val credential = vault.getCredentialForDid(identity.did)
                val claims = credential?.credentialSubject?.claims ?: emptyMap()
                for ((key, selected) in _selectedClaims.value) {
                    if (selected && claims.containsKey(key)) {
                        sharedClaims[key] = claims[key]!!
                    }
                }

                // Build AMR
                val amr = mutableListOf("hwk")
                if (biometricUsed) amr.add("bio")

                // Submit to service
                val resp = serverApi.verifyAuth(
                    AuthVerifyRequest(
                        did = identity.did,
                        key_id = identity.keyId,
                        signed_challenge = signedChallenge,
                        shared_claims = sharedClaims,
                        amr = amr,
                        session_id = sessionId.ifEmpty { null }
                    )
                )

                // Mutual auth
                val verified = verifier.verifyChallengeResponse(
                    resp.server_did, resp.server_key_id,
                    resp.session_token, resp.server_signature
                ).getOrThrow()
                if (!verified) throw SecurityException("Service authentication failed")

                _state.value = ConsentState.Success(resp.session_token)
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.value = ConsentState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    suspend fun requireBiometric(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return when (biometricAuth.authenticate(activity)) {
            is BiometricResult.Success -> true
            else -> false
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
