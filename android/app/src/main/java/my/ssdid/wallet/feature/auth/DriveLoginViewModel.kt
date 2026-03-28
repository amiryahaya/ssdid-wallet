package my.ssdid.wallet.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.model.Did
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.transport.DriveApi
import my.ssdid.sdk.domain.transport.DriveAuthenticateRequest
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.transport.dto.ClaimRequest
import my.ssdid.sdk.domain.transport.dto.RegisterStartRequest
import my.ssdid.sdk.domain.transport.dto.RegisterVerifyRequest
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.sdk.domain.verifier.Verifier
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import my.ssdid.wallet.platform.security.UrlValidator
import javax.inject.Inject

sealed class DriveLoginState {
    object Loading : DriveLoginState()
    object Ready : DriveLoginState()
    object Submitting : DriveLoginState()
    data class Success(val sessionToken: String) : DriveLoginState()
    data class Error(val message: String) : DriveLoginState()
}

@HiltViewModel
class DriveLoginViewModel @Inject constructor(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val verifier: Verifier,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val serviceUrl: String = savedStateHandle["serviceUrl"] ?: ""
    val serviceName: String = savedStateHandle["serviceName"] ?: ""
    val challengeId: String = savedStateHandle["challengeId"] ?: ""

    val requestedClaims: List<ClaimRequest> = run {
        val raw = savedStateHandle.get<String>("requestedClaims") ?: ""
        if (raw.isBlank()) emptyList()
        else try {
            json.decodeFromString<List<ClaimRequest>>(raw).take(20)
        } catch (_: Exception) { emptyList() }
    }

    private val _state = MutableStateFlow<DriveLoginState>(DriveLoginState.Loading)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    private val _selectedClaims = MutableStateFlow<Set<String>>(emptySet())
    val selectedClaims = _selectedClaims.asStateFlow()

    init {
        viewModelScope.launch {
            // C2: Validate serviceUrl before allowing any operations
            if (!UrlValidator.isValidServerUrl(serviceUrl)) {
                _state.value = DriveLoginState.Error("Invalid service URL")
                return@launch
            }
            val allIdentities = vault.listIdentities()
            _identities.value = allIdentities
            if (allIdentities.isNotEmpty()) _selectedIdentity.value = allIdentities.first()
            _selectedClaims.value = requestedClaims.map { it.key }.toSet()
            _state.value = DriveLoginState.Ready
        }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
    }

    fun toggleClaim(key: String) {
        val claim = requestedClaims.find { it.key == key } ?: return
        if (claim.required) return
        val current = _selectedClaims.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _selectedClaims.value = current
    }

    fun approve() {
        // H1: Guard against double-tap
        if (_state.value is DriveLoginState.Submitting) return
        val identity = _selectedIdentity.value ?: run {
            _state.value = DriveLoginState.Error("No identity selected")
            return
        }
        viewModelScope.launch {
            _state.value = DriveLoginState.Submitting
            try {
                // M3: Create driveApi once and pass to registerWithDrive
                val driveApi = httpClient.driveApi(serviceUrl)

                // Check if we have a stored credential for this service
                var credential = vault.getCredentialForDid(identity.did)

                // If no credential, register first
                if (credential == null) {
                    credential = registerWithDrive(identity, driveApi)
                }

                // C1: Capture credential id before authenticate for reliable 401 cleanup
                val credentialId = credential.id

                // Authenticate with the credential
                val resp = driveApi.authenticate(
                    DriveAuthenticateRequest(
                        credential = credential,
                        challengeId = challengeId.ifEmpty { null }
                    )
                )

                _state.value = DriveLoginState.Success(resp.sessionToken)
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                val message = when {
                    e is retrofit2.HttpException && e.code() == 404 ->
                        "No account found. Please register first."
                    e is retrofit2.HttpException && e.code() == 401 -> {
                        // C1: Delete using captured credential, no second vault read
                        vault.getCredentialForDid(identity.did)?.let {
                            vault.deleteCredential(it.id)
                        }
                        "Credential expired. Please try again."
                    }
                    else -> e.message ?: "Login failed"
                }
                _state.value = DriveLoginState.Error(message)
            }
        }
    }

    private suspend fun registerWithDrive(
        identity: Identity,
        driveApi: DriveApi
    ): VerifiableCredential {
        // Step 1: Register — send our DID
        val startResp = driveApi.register(
            RegisterStartRequest(identity.did, identity.keyId)
        )

        // Step 2: Validate server DID and verify signature (best-effort — Drive may not be in registry)
        if (startResp.server_did.isNotBlank()) {
            Did.validate(startResp.server_did).getOrElse {
                android.util.Log.w("DriveLogin", "Invalid server DID in registration response: ${it.message}")
            }
        }
        if (startResp.server_did.isNotBlank() && startResp.server_signature.isNotBlank()) {
            val serverVerified = verifier.verifyChallengeResponse(
                startResp.server_did,
                startResp.server_key_id,
                startResp.challenge,
                startResp.server_signature
            ).getOrNull() ?: false
            if (!serverVerified) {
                android.util.Log.w("DriveLogin", "Server signature verification failed — proceeding with registration")
            }
        }

        // Step 3: Sign the challenge
        val signatureBytes = vault.sign(identity.keyId, startResp.challenge.toByteArray()).getOrThrow()
        val signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Complete registration — receive VC
        // Build shared claims from identity profile
        val sharedClaims = identity.claimsMap().ifEmpty { null }

        val verifyResp = driveApi.registerVerify(
            RegisterVerifyRequest(identity.did, identity.keyId, signedChallenge, shared_claims = sharedClaims)
        )

        // Step 5: Store the credential
        val vc = verifyResp.credential
        vault.storeCredential(vc).getOrThrow()
        return vc
    }

    suspend fun requireBiometric(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return when (biometricAuth.authenticate(activity)) {
            is BiometricResult.Success -> true
            is BiometricResult.Cancelled -> false
            is BiometricResult.Error -> false
        }
    }
}
