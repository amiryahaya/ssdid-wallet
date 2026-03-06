package my.ssdid.mobile.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.mobile.domain.model.DidDocument
import my.ssdid.mobile.domain.model.Proof

@Serializable
data class RegisterDidRequest(
    val did_document: DidDocument,
    val proof: Proof
)

@Serializable
data class RegisterDidResponse(
    val did: String,
    val status: String
)

@Serializable
data class ChallengeResponse(
    val challenge: String,
    val expires_at: String? = null
)
