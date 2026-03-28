package my.ssdid.sdk.domain.device

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val platform: String,
    val keyId: String,
    val enrolledAt: String,
    val isPrimary: Boolean
)
