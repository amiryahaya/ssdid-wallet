package my.ssdid.wallet.feature.invite

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import my.ssdid.wallet.domain.model.Did
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.AcceptWithWalletRequest
import my.ssdid.wallet.domain.transport.dto.InvitationDetailsResponse
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.platform.security.UrlValidator
import javax.inject.Inject

data class InviteAcceptUiState(
    val isLoading: Boolean = true,
    val invitation: InvitationDetailsResponse? = null,
    val emailMatch: Boolean = false,
    val walletEmail: String = "",
    val error: String? = null,
    val isAccepting: Boolean = false,
    val acceptSuccess: Boolean = false,
    val sessionToken: String? = null,
    val callbackUri: Uri? = null
)

@HiltViewModel
class InviteAcceptViewModel @Inject constructor(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val verifier: Verifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val token: String = savedStateHandle["token"] ?: ""
    val callbackUrl: String = savedStateHandle["callbackUrl"] ?: ""

    private val _state = MutableStateFlow(InviteAcceptUiState())
    val state = _state.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    init {
        loadInvitation()
    }

    private fun loadInvitation() {
        viewModelScope.launch {
            try {
                if (!UrlValidator.isValidServerUrl(serverUrl)) {
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Invalid server URL"
                    ) }
                    return@launch
                }

                val api = httpClient.serverApi(serverUrl)
                val invitation = api.getInvitationByToken(token)

                val identities = vault.listIdentities()
                val firstIdentity = identities.firstOrNull()
                _selectedIdentity.value = firstIdentity
                val walletEmail = firstIdentity?.email ?: ""

                val emailMatch = walletEmail.isNotBlank() &&
                    walletEmail.equals(invitation.email, ignoreCase = true)

                _state.update { it.copy(
                    isLoading = false,
                    invitation = invitation,
                    emailMatch = emailMatch,
                    walletEmail = walletEmail,
                    error = if (!emailMatch && walletEmail.isNotBlank())
                        "Email mismatch: invitation is for ${invitation.email} but your wallet email is $walletEmail"
                    else if (walletEmail.isBlank())
                        "No email configured in wallet profile"
                    else null
                ) }
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                val message = when {
                    e is retrofit2.HttpException && e.code() == 404 ->
                        "Invitation not found or expired"
                    else -> e.message ?: "Failed to load invitation"
                }
                _state.update { it.copy(
                    isLoading = false,
                    error = message
                ) }
            }
        }
    }

    fun accept() {
        if (_state.value.isAccepting) return

        viewModelScope.launch {
            _state.update { it.copy(isAccepting = true, error = null) }
            try {
                val selectedDid = _selectedIdentity.value?.did
                    ?: run {
                        _state.update { it.copy(isAccepting = false, error = "No identity selected") }
                        return@launch
                    }

                val credential = vault.getCredentialForDid(selectedDid)
                    ?: run {
                        _state.update { it.copy(
                            isAccepting = false,
                            error = "No credential found for selected identity. Please register with a service first."
                        ) }
                        return@launch
                    }
                val credentialJson = json.encodeToJsonElement(
                    my.ssdid.wallet.domain.model.VerifiableCredential.serializer(),
                    credential
                )

                val api = httpClient.serverApi(serverUrl)
                val response = api.acceptWithWallet(
                    token,
                    AcceptWithWalletRequest(
                        credential = credentialJson,
                        email = _selectedIdentity.value?.email ?: _state.value.walletEmail
                    )
                )

                // Validate server DID format
                Did.validate(response.serverDid).onFailure {
                    _state.update { s -> s.copy(
                        isAccepting = false,
                        error = "Invalid server DID: ${it.message}"
                    ) }
                    return@launch
                }

                // Verify server signature — blank fields = fatal (possible MITM)
                if (response.serverDid.isBlank() || response.serverSignature.isBlank()) {
                    _state.update { it.copy(
                        isAccepting = false,
                        error = "Server did not provide identity proof. Please try again."
                    ) }
                    return@launch
                }

                val verified = verifier.verifyChallengeResponse(
                    response.serverDid,
                    response.serverKeyId,
                    response.sessionToken,
                    response.serverSignature
                ).getOrThrow()

                if (!verified) {
                    _state.update { it.copy(
                        isAccepting = false,
                        error = "Server identity verification failed. Please try again."
                    ) }
                    return@launch
                }

                val callbackUri = if (callbackUrl.isNotBlank()) {
                    Uri.parse("$callbackUrl?session_token=${Uri.encode(response.sessionToken)}&status=success")
                } else null

                _state.update { it.copy(
                    isAccepting = false,
                    acceptSuccess = true,
                    sessionToken = response.sessionToken,
                    callbackUri = callbackUri
                ) }
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.update { it.copy(
                    isAccepting = false,
                    error = e.message ?: "Failed to accept invitation"
                ) }
            }
        }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
        _state.update { it.copy(
            walletEmail = identity.email ?: "",
            emailMatch = !identity.email.isNullOrBlank() &&
                identity.email.equals(_state.value.invitation?.email, ignoreCase = true),
            error = if (!identity.email.isNullOrBlank() &&
                !identity.email.equals(_state.value.invitation?.email, ignoreCase = true))
                "Email mismatch: invitation is for ${_state.value.invitation?.email} but identity email is ${identity.email}"
            else if (identity.email.isNullOrBlank())
                "No email configured for this identity"
            else null
        ) }
    }

    fun decline() {
        val callbackUri = if (callbackUrl.isNotBlank()) {
            Uri.parse("$callbackUrl?status=cancelled")
        } else null
        _state.update { it.copy(callbackUri = callbackUri) }
    }

    fun buildErrorCallbackUri(message: String): Uri? {
        return if (callbackUrl.isNotBlank()) {
            Uri.parse("$callbackUrl?status=error&message=${Uri.encode(message)}")
        } else null
    }
}
