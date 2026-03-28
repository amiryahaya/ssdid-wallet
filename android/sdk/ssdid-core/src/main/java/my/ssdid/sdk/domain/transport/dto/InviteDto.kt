package my.ssdid.sdk.domain.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class InvitationDetailsResponse(
    @SerialName("tenant_name") val tenantName: String,
    @SerialName("inviter_name") val inviterName: String? = null,
    val email: String,
    val role: String,
    val status: String,
    val message: String? = null,
    @SerialName("short_code") val shortCode: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AcceptWithWalletRequest(
    val credential: JsonElement,
    val email: String
)

@Serializable
data class AcceptWithWalletResponse(
    @SerialName("session_token") val sessionToken: String,
    val did: String,
    @SerialName("server_did") val serverDid: String,
    @SerialName("server_key_id") val serverKeyId: String,
    @SerialName("server_signature") val serverSignature: String
)
