package my.ssdid.sdk.api

import my.ssdid.sdk.domain.oid4vci.CredentialOffer
import my.ssdid.sdk.domain.oid4vci.CredentialOfferReview
import my.ssdid.sdk.domain.oid4vci.IssuanceResult
import my.ssdid.sdk.domain.oid4vci.IssuerMetadata
import my.ssdid.sdk.domain.oid4vci.OpenId4VciHandler

class IssuanceApi internal constructor(private val handler: OpenId4VciHandler) {
    fun processOffer(uri: String): Result<CredentialOfferReview> = handler.processOffer(uri)

    suspend fun acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigId: String,
        txCode: String? = null,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: CredentialSigner
    ): Result<IssuanceResult> = handler.acceptOffer(
        offer, metadata, selectedConfigId, txCode, walletDid, keyId, algorithm
    ) { data -> signer.sign(data) }
}
