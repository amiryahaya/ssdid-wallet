package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClaimRequest(
    val key: String,
    val required: Boolean = false
)

@Serializable
data class AuthChallengeResponse(
    val challenge: String,
    val server_name: String,
    val server_did: String,
    val server_key_id: String
)

@Serializable
data class AuthVerifyRequest(
    val did: String,
    val key_id: String,
    val signed_challenge: String,
    val shared_claims: Map<String, String>,
    val amr: List<String>,
    val session_id: String? = null
)

@Serializable
data class AuthVerifyResponse(
    val session_token: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String
)
