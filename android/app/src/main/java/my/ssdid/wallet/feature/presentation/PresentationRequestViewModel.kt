package my.ssdid.wallet.feature.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.domain.oid4vp.AuthorizationRequest
import my.ssdid.wallet.domain.oid4vp.MatchResult
import my.ssdid.wallet.domain.oid4vp.NoMatchingCredentialsException
import my.ssdid.wallet.domain.oid4vp.OpenId4VpHandler
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultStorage
import javax.inject.Inject

@HiltViewModel
class PresentationRequestViewModel @Inject constructor(
    private val handler: OpenId4VpHandler,
    private val vault: Vault,
    private val vaultStorage: VaultStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class CredentialMatch(
            val verifierId: String,
            val credentialType: String,
            val claims: List<ClaimUiItem>,
            val authRequest: AuthorizationRequest,
            val matchResult: MatchResult,
            val storedVc: StoredSdJwtVc
        ) : UiState()
        object Submitting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
        object NoCredentials : UiState()
    }

    data class ClaimUiItem(
        val name: String,
        val required: Boolean,
        val available: Boolean,
        val selected: Boolean
    )

    private val uri: String = savedStateHandle["uri"] ?: ""
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        processRequest()
    }

    private fun processRequest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val storedVcs = vaultStorage.listSdJwtVcs()

                val result = handler.processRequest(uri, storedVcs)
                result.fold(
                    onSuccess = { processed ->
                        val firstMatch = processed.matchResults.firstOrNull()
                        if (firstMatch == null) {
                            _state.value = UiState.NoCredentials
                            return@fold
                        }

                        // Find the StoredSdJwtVc that matched
                        val matchedVc = storedVcs.firstOrNull { it.id == firstMatch.credentialId }
                        if (matchedVc == null) {
                            _state.value = UiState.Error("Matched credential not found in storage")
                            return@fold
                        }

                        val claims = firstMatch.availableClaims.map { (_, info) ->
                            ClaimUiItem(
                                name = info.name,
                                required = info.required,
                                available = info.available,
                                selected = info.required
                            )
                        }

                        _state.value = UiState.CredentialMatch(
                            verifierId = processed.authRequest.clientId,
                            credentialType = firstMatch.credentialType,
                            claims = claims,
                            authRequest = processed.authRequest,
                            matchResult = firstMatch,
                            storedVc = matchedVc
                        )
                    },
                    onFailure = { error ->
                        when (error) {
                            is NoMatchingCredentialsException -> _state.value = UiState.NoCredentials
                            else -> _state.value = UiState.Error(error.message ?: "Failed to process request")
                        }
                    }
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Unexpected error")
            }
        }
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
        // Atomically capture state and transition to Submitting
        var captured: UiState.CredentialMatch? = null
        _state.update { current ->
            if (current !is UiState.CredentialMatch) return@update current
            captured = current
            UiState.Submitting
        }
        val current = captured ?: return

        val authRequest = current.authRequest
        val matchResult = current.matchResult
        val storedVc = current.storedVc
        val selectedClaims = current.claims
            .filter { it.selected && it.available }
            .map { it.name }
            .toSet()
        val subjectDid = storedVc.subject

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Find the identity matching the credential's subject DID
                val identities = vault.listIdentities()
                val identity = identities.firstOrNull { it.did == subjectDid }
                    ?: throw IllegalStateException("No identity found for credential subject: $subjectDid")

                // Determine JWA algorithm name from the identity's algorithm
                val algorithmName = algorithmToJwa(identity.algorithm)

                // Create the signer using vault.sign
                val keyId = identity.keyId
                val signer: (ByteArray) -> ByteArray = { data ->
                    runBlocking {
                        vault.sign(keyId, data).getOrThrow()
                    }
                }

                val result = handler.submitPresentation(
                    authRequest = authRequest,
                    matchResult = matchResult,
                    storedVc = storedVc,
                    selectedClaims = selectedClaims,
                    algorithm = algorithmName,
                    signer = signer
                )

                result.fold(
                    onSuccess = { _state.value = UiState.Success },
                    onFailure = { _state.value = UiState.Error(it.message ?: "Submission failed") }
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun decline() {
        val current = _state.value
        if (current !is UiState.CredentialMatch) return

        viewModelScope.launch(Dispatchers.IO) {
            handler.declineRequest(current.authRequest)
        }
    }

    fun retry() {
        _state.value = UiState.Loading
        processRequest()
    }

    companion object {
        /**
         * Maps Algorithm enum to JWA algorithm name for SD-JWT KB-JWT signing.
         */
        private fun algorithmToJwa(algorithm: my.ssdid.wallet.domain.model.Algorithm): String {
            return when (algorithm) {
                my.ssdid.wallet.domain.model.Algorithm.ED25519 -> "EdDSA"
                my.ssdid.wallet.domain.model.Algorithm.ECDSA_P256 -> "ES256"
                my.ssdid.wallet.domain.model.Algorithm.ECDSA_P384 -> "ES384"
                my.ssdid.wallet.domain.model.Algorithm.KAZ_SIGN_128 -> "KAZ128"
                my.ssdid.wallet.domain.model.Algorithm.KAZ_SIGN_192 -> "KAZ192"
                my.ssdid.wallet.domain.model.Algorithm.KAZ_SIGN_256 -> "KAZ256"
                my.ssdid.wallet.domain.model.Algorithm.ML_DSA_44 -> "ML-DSA-44"
                my.ssdid.wallet.domain.model.Algorithm.ML_DSA_65 -> "ML-DSA-65"
                my.ssdid.wallet.domain.model.Algorithm.ML_DSA_87 -> "ML-DSA-87"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_128S -> "SLH-DSA-SHA2-128s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_128F -> "SLH-DSA-SHA2-128f"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_192S -> "SLH-DSA-SHA2-192s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_192F -> "SLH-DSA-SHA2-192f"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_256S -> "SLH-DSA-SHA2-256s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHA2_256F -> "SLH-DSA-SHA2-256f"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_128S -> "SLH-DSA-SHAKE-128s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_128F -> "SLH-DSA-SHAKE-128f"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_192S -> "SLH-DSA-SHAKE-192s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_192F -> "SLH-DSA-SHAKE-192f"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_256S -> "SLH-DSA-SHAKE-256s"
                my.ssdid.wallet.domain.model.Algorithm.SLH_DSA_SHAKE_256F -> "SLH-DSA-SHAKE-256f"
            }
        }
    }
}
