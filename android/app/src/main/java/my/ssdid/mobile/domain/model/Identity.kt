package my.ssdid.mobile.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val name: String,
    val did: String,
    val keyId: String,
    val algorithm: Algorithm,
    val publicKeyMultibase: String,
    val createdAt: String,
    val isActive: Boolean = true
)
