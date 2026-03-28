package my.ssdid.sdk.domain.rotation

import kotlinx.serialization.Serializable

@Serializable
data class RotationEntry(
    val timestamp: String,
    val oldKeyIdFragment: String,
    val newKeyIdFragment: String
)
