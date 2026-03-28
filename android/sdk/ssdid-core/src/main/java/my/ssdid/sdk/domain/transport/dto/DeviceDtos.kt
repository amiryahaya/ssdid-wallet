package my.ssdid.sdk.domain.transport.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairingInitRequest(
    val did: String,
    val challenge: String,
    val primary_key_id: String
)

@Serializable
data class PairingInitResponse(val pairing_id: String)

@Serializable
data class PairingJoinRequest(
    val pairing_id: String,
    val public_key: String,
    val signed_challenge: String,
    val device_name: String,
    val platform: String
)

@Serializable
data class PairingJoinResponse(val status: String)

@Serializable
data class PairingApproveRequest(
    val did: String,
    val key_id: String,
    val signed_approval: String
)

@Serializable
data class PairingStatusResponse(
    val status: String,
    val device_name: String? = null,
    val public_key: String? = null,
    val signed_challenge: String? = null
)
