package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.VerifiableCredential

@Serializable
data class CredentialOfferResponse(
    val offer_id: String,
    val issuer_did: String,
    val credential_type: String,
    val claims: Map<String, String>,
    val expires_at: String? = null
)

@Serializable
data class CredentialAcceptRequest(
    val did: String,
    val key_id: String,
    val signed_acceptance: String
)

@Serializable
data class CredentialAcceptResponse(
    val credential: VerifiableCredential
)
