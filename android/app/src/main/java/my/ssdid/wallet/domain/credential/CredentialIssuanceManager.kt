package my.ssdid.wallet.domain.credential

import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.CredentialAcceptRequest
import my.ssdid.wallet.domain.transport.dto.CredentialOfferResponse
import my.ssdid.wallet.domain.vault.Vault

@Deprecated("Use OpenId4VciHandler for OID4VCI flows. This class handles legacy custom issuer API flows only.")
class CredentialIssuanceManager(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient
) {
    suspend fun fetchOffer(issuerUrl: String, offerId: String): Result<CredentialOfferResponse> = runCatching {
        val api = httpClient.issuerApi(issuerUrl)
        api.getOffer(offerId)
    }

    suspend fun acceptOffer(
        issuerUrl: String,
        offerId: String,
        identity: Identity
    ): Result<VerifiableCredential> = runCatching {
        val api = httpClient.issuerApi(issuerUrl)
        val acceptance = "accept:$offerId".toByteArray()
        val sig = vault.sign(identity.keyId, acceptance).getOrThrow()
        val resp = api.acceptOffer(offerId, CredentialAcceptRequest(
            did = identity.did,
            key_id = identity.keyId,
            signed_acceptance = Multibase.encode(sig)
        ))
        vault.storeCredential(resp.credential).getOrThrow()
        resp.credential
    }
}
