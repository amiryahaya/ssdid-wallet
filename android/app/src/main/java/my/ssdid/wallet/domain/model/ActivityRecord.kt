package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ActivityRecord(
    val id: String,
    val type: ActivityType,
    val did: String,
    val serviceDid: String? = null,
    val serviceUrl: String? = null,
    val timestamp: String,
    val status: ActivityStatus,
    val details: Map<String, String> = emptyMap()
)

@Serializable
enum class ActivityType {
    IDENTITY_CREATED, IDENTITY_DEACTIVATED, KEY_ROTATED, DEVICE_ENROLLED, DEVICE_REMOVED,
    SERVICE_REGISTERED, AUTHENTICATED, TX_SIGNED,
    CREDENTIAL_RECEIVED, CREDENTIAL_PRESENTED, BACKUP_CREATED
}

@Serializable
enum class ActivityStatus { SUCCESS, FAILED }
