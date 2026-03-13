package my.ssdid.wallet.feature.invite

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import my.ssdid.wallet.domain.profile.ProfileManager
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
    private val profileManager: ProfileManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val token: String = savedStateHandle["token"] ?: ""
    val callbackUrl: String = savedStateHandle["callbackUrl"] ?: ""

    private val _state = MutableStateFlow(InviteAcceptUiState())
    val state = _state.asStateFlow()

    init {
        loadInvitation()
    }

    private fun loadInvitation() {
        viewModelScope.launch {
            try {
                if (!UrlValidator.isValidServerUrl(serverUrl)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Invalid server URL"
                    )
                    return@launch
                }

                val api = httpClient.serverApi(serverUrl)
                val invitation = api.getInvitationByToken(token)

                val claims = profileManager.getProfileClaims()
                val walletEmail = claims["email"] ?: ""

                val emailMatch = walletEmail.isNotBlank() &&
                    walletEmail.equals(invitation.email, ignoreCase = true)

                _state.value = _state.value.copy(
                    isLoading = false,
                    invitation = invitation,
                    emailMatch = emailMatch,
                    walletEmail = walletEmail,
                    error = if (!emailMatch && walletEmail.isNotBlank())
                        "Email mismatch: invitation is for ${invitation.email} but your wallet email is $walletEmail"
                    else if (walletEmail.isBlank())
                        "No email configured in wallet profile"
                    else null
                )
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                val message = when {
                    e is retrofit2.HttpException && e.code() == 404 ->
                        "Invitation not found or expired"
                    else -> e.message ?: "Failed to load invitation"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = message
                )
            }
        }
    }

    fun accept() {
        if (_state.value.isAccepting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isAccepting = true, error = null)
            try {
                val credentials = vault.listCredentials()
                if (credentials.isEmpty()) {
                    _state.value = _state.value.copy(
                        isAccepting = false,
                        error = "No credentials found in wallet. Please register with a service first."
                    )
                    return@launch
                }

                val credential = credentials.first()
                val credentialJson = json.encodeToJsonElement(
                    my.ssdid.wallet.domain.model.VerifiableCredential.serializer(),
                    credential
                )

                val api = httpClient.serverApi(serverUrl)
                val response = api.acceptWithWallet(
                    token,
                    AcceptWithWalletRequest(
                        credential = credentialJson,
                        email = _state.value.walletEmail
                    )
                )

                // Verify server signature — blank fields = fatal (possible MITM)
                if (response.serverDid.isBlank() || response.serverSignature.isBlank()) {
                    _state.value = _state.value.copy(
                        isAccepting = false,
                        error = "Server did not provide identity proof. Please try again."
                    )
                    return@launch
                }

                val verified = verifier.verifyChallengeResponse(
                    response.serverDid,
                    response.serverKeyId,
                    response.sessionToken,
                    response.serverSignature
                ).getOrThrow()

                if (!verified) {
                    _state.value = _state.value.copy(
                        isAccepting = false,
                        error = "Server identity verification failed. Please try again."
                    )
                    return@launch
                }

                val callbackUri = if (callbackUrl.isNotBlank()) {
                    Uri.parse("$callbackUrl?session_token=${Uri.encode(response.sessionToken)}&status=success")
                } else null

                _state.value = _state.value.copy(
                    isAccepting = false,
                    acceptSuccess = true,
                    sessionToken = response.sessionToken,
                    callbackUri = callbackUri
                )
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.value = _state.value.copy(
                    isAccepting = false,
                    error = e.message ?: "Failed to accept invitation"
                )
            }
        }
    }

    fun decline() {
        val callbackUri = if (callbackUrl.isNotBlank()) {
            Uri.parse("$callbackUrl?status=cancelled")
        } else null
        _state.value = _state.value.copy(callbackUri = callbackUri)
    }

    fun buildErrorCallbackUri(message: String): Uri? {
        return if (callbackUrl.isNotBlank()) {
            Uri.parse("$callbackUrl?status=error&message=${Uri.encode(message)}")
        } else null
    }
}
