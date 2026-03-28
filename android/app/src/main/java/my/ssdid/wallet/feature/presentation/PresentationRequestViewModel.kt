package my.ssdid.wallet.feature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.oid4vp.AuthorizationRequest
import my.ssdid.wallet.domain.oid4vp.MatchResult
import my.ssdid.wallet.domain.oid4vp.OpenId4VpHandler
import my.ssdid.wallet.domain.oid4vp.PresentationReviewResult
import my.ssdid.sdk.domain.vault.Vault
import javax.inject.Inject

@HiltViewModel
class PresentationRequestViewModel @Inject constructor(
    private val handler: OpenId4VpHandler,
    private val vault: Vault
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class CredentialMatch(
            val verifierName: String,
            val claims: List<ClaimItem>,
            val matchResult: MatchResult,
            val authRequest: AuthorizationRequest
        ) : UiState()
        object Submitting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }

    data class ClaimItem(
        val name: String,
        val value: String,
        val required: Boolean,
        val selected: Boolean
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun processRequest(rawUri: String) {
        viewModelScope.launch {
            val result = handler.processRequest(rawUri)
            result.fold(
                onSuccess = { review -> setReviewResult(review) },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Unknown error") }
            )
        }
    }

    fun setReviewResult(review: PresentationReviewResult) {
        val match = review.matches.first()
        val claims = match.requiredClaims.map { name ->
            ClaimItem(name, match.credential.claims[name] ?: "", required = true, selected = true)
        } + match.optionalClaims.map { name ->
            ClaimItem(name, match.credential.claims[name] ?: "", required = false, selected = false)
        }
        _state.value = UiState.CredentialMatch(
            verifierName = review.authRequest.clientId,
            claims = claims,
            matchResult = match,
            authRequest = review.authRequest
        )
    }

    fun toggleClaim(claimName: String) {
        _state.update { current ->
            if (current !is UiState.CredentialMatch) return@update current
            current.copy(claims = current.claims.map { item ->
                if (item.name == claimName && !item.required) item.copy(selected = !item.selected)
                else item
            })
        }
    }

    fun approve() {
        var captured: UiState.CredentialMatch? = null
        _state.update { current ->
            if (current !is UiState.CredentialMatch) return@update current
            captured = current
            UiState.Submitting
        }
        val current = captured ?: return

        viewModelScope.launch {
            val selectedClaims = current.claims.filter { it.selected }.map { it.name }
            val identity = vault.listIdentities().firstOrNull() ?: run {
                _state.value = UiState.Error("No identity available")
                return@launch
            }

            val result = handler.submitPresentation(
                authRequest = current.authRequest,
                matchResult = current.matchResult,
                selectedClaims = selectedClaims,
                algorithm = identity.algorithm.toJwaName(),
                // runBlocking is necessary here: VpTokenBuilder.build() takes a synchronous
                // signer lambda (ByteArray) -> ByteArray, but vault.sign() is a suspend fun.
                // This is safe because we're inside viewModelScope.launch which runs on
                // Dispatchers.Main, and vault.sign dispatches to IO internally.
                signer = { data ->
                    kotlinx.coroutines.runBlocking { vault.sign(identity.keyId, data).getOrThrow() }
                }
            )

            result.fold(
                onSuccess = { _state.value = UiState.Success },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Submission failed") }
            )
        }
    }

    fun decline() {
        _state.value = UiState.Error("Declined by user")
    }
}
