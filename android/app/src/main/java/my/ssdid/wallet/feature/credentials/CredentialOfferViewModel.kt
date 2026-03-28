package my.ssdid.wallet.feature.credentials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.wallet.domain.oid4vci.CredentialOffer
import my.ssdid.wallet.domain.oid4vci.CredentialOfferReview
import my.ssdid.wallet.domain.oid4vci.IssuanceResult
import my.ssdid.wallet.domain.oid4vci.OpenId4VciHandler
import my.ssdid.sdk.domain.vault.Vault
import javax.inject.Inject

sealed class CredentialOfferUiState {
    object Loading : CredentialOfferUiState()
    data class ReviewingOffer(
        val issuerName: String,
        val credentialTypes: List<String>,
        val selectedConfigId: String,
        val requiresPin: Boolean,
        val pinDescription: String?,
        val pinLength: Int,
        val pinInputMode: String,
        val identities: List<Identity>,
        val selectedIdentity: Identity?,
        val review: CredentialOfferReview
    ) : CredentialOfferUiState()
    data class PinEntry(
        val description: String?,
        val length: Int,
        val inputMode: String,
        val review: CredentialOfferReview,
        val selectedIdentity: Identity,
        val selectedConfigId: String
    ) : CredentialOfferUiState()
    object Processing : CredentialOfferUiState()
    object Success : CredentialOfferUiState()
    data class Deferred(val transactionId: String) : CredentialOfferUiState()
    data class Error(val message: String) : CredentialOfferUiState()
}

@HiltViewModel
class CredentialOfferViewModel @Inject constructor(
    private val handler: OpenId4VciHandler,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val issuerUrl: String = savedStateHandle["issuerUrl"] ?: ""
    val offerId: String = savedStateHandle["offerId"] ?: ""

    private val _state = MutableStateFlow<CredentialOfferUiState>(CredentialOfferUiState.Loading)
    val state: StateFlow<CredentialOfferUiState> = _state.asStateFlow()

    init {
        if (issuerUrl.isNotBlank() || offerId.isNotBlank()) {
            processOffer(buildOfferUri(issuerUrl, offerId))
        }
    }

    fun processOffer(rawUri: String) {
        _state.value = CredentialOfferUiState.Loading
        viewModelScope.launch {
            handler.processOffer(rawUri).fold(
                onSuccess = { review ->
                    val identities = vault.listIdentities()
                    _state.value = CredentialOfferUiState.ReviewingOffer(
                        issuerName = review.metadata.credentialIssuer,
                        credentialTypes = review.credentialConfigNames,
                        selectedConfigId = review.credentialConfigNames.firstOrNull() ?: "",
                        requiresPin = review.offer.txCode != null,
                        pinDescription = review.offer.txCode?.description,
                        pinLength = review.offer.txCode?.length ?: 0,
                        pinInputMode = review.offer.txCode?.inputMode ?: "numeric",
                        identities = identities,
                        selectedIdentity = identities.firstOrNull(),
                        review = review
                    )
                },
                onFailure = { e ->
                    _state.value = CredentialOfferUiState.Error(
                        e.message ?: "Failed to process credential offer"
                    )
                }
            )
        }
    }

    fun selectIdentity(identity: Identity) {
        val current = _state.value
        if (current is CredentialOfferUiState.ReviewingOffer) {
            _state.value = current.copy(selectedIdentity = identity)
        }
    }

    fun selectConfigId(configId: String) {
        val current = _state.value
        if (current is CredentialOfferUiState.ReviewingOffer) {
            _state.value = current.copy(selectedConfigId = configId)
        }
    }

    fun acceptOffer() {
        val current = _state.value
        if (current !is CredentialOfferUiState.ReviewingOffer) return
        val identity = current.selectedIdentity ?: return

        if (current.requiresPin) {
            _state.value = CredentialOfferUiState.PinEntry(
                description = current.pinDescription,
                length = current.pinLength,
                inputMode = current.pinInputMode,
                review = current.review,
                selectedIdentity = identity,
                selectedConfigId = current.selectedConfigId
            )
            return
        }

        executeIssuance(current.review, identity, current.selectedConfigId, txCode = null)
    }

    fun submitPin(pin: String) {
        val current = _state.value
        if (current !is CredentialOfferUiState.PinEntry) return

        executeIssuance(current.review, current.selectedIdentity, current.selectedConfigId, txCode = pin)
    }

    fun decline() {
        _state.value = CredentialOfferUiState.Error("Declined by user")
    }

    fun retry(rawUri: String) {
        processOffer(rawUri)
    }

    private fun executeIssuance(
        review: CredentialOfferReview,
        identity: Identity,
        selectedConfigId: String,
        txCode: String?
    ) {
        _state.value = CredentialOfferUiState.Processing
        viewModelScope.launch {
            handler.acceptOffer(
                offer = review.offer,
                metadata = review.metadata,
                selectedConfigId = selectedConfigId,
                txCode = txCode,
                walletDid = identity.did,
                keyId = identity.keyId,
                algorithm = identity.algorithm.toJwaName(),
                // runBlocking is necessary here: ProofJwtBuilder.build() takes a synchronous
                // signer lambda (ByteArray) -> ByteArray, but vault.sign() is a suspend fun.
                // This is safe because we're inside viewModelScope.launch which runs on
                // Dispatchers.Main, and vault.sign dispatches to IO internally.
                signer = { data ->
                    runBlocking { vault.sign(identity.keyId, data).getOrThrow() }
                }
            ).fold(
                onSuccess = { result ->
                    _state.value = when (result) {
                        is IssuanceResult.Success -> CredentialOfferUiState.Success
                        is IssuanceResult.Deferred -> CredentialOfferUiState.Deferred(result.transactionId)
                        is IssuanceResult.Failed -> CredentialOfferUiState.Error(result.error)
                    }
                },
                onFailure = { e ->
                    _state.value = CredentialOfferUiState.Error(
                        e.message ?: "Credential issuance failed"
                    )
                }
            )
        }
    }

    private fun buildOfferUri(issuerUrl: String, offerId: String): String {
        return "openid-credential-offer://?credential_offer=" +
            """{"credential_issuer":"$issuerUrl","credential_configuration_ids":["$offerId"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"$offerId"}}}"""
    }
}
