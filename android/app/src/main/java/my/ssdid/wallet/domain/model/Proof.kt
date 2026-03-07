package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Proof(
    val type: String,
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String,
    val proofValue: String,
    val domain: String? = null,
    val challenge: String? = null
)
