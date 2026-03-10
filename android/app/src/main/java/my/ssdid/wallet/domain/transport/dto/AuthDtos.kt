package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaimRequest(
    val key: String,
    val required: Boolean = false
)

@Serializable
data class AuthChallengeResponse(
    val challenge: String,
    @SerialName("server_name") val serverName: String,
    @SerialName("server_did") val serverDid: String,
    @SerialName("server_key_id") val serverKeyId: String
)

@Serializable
data class AuthVerifyRequest(
    val did: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("signed_challenge") val signedChallenge: String,
    @SerialName("shared_claims") val sharedClaims: Map<String, String>,
    val amr: List<String>,
    @SerialName("session_id") val sessionId: String? = null
)

@Serializable
data class AuthVerifyResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("server_did") val serverDid: String,
    @SerialName("server_key_id") val serverKeyId: String,
    @SerialName("server_signature") val serverSignature: String
)
