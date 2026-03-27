package my.ssdid.sdk.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val name: String,
    val did: String,
    val keyId: String,
    val algorithm: Algorithm,
    val publicKeyMultibase: String,
    val createdAt: String,
    val isActive: Boolean = true,
    val recoveryKeyId: String? = null,
    val hasRecoveryKey: Boolean = false,
    val preRotatedKeyId: String? = null,
    val profileName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false
) {
    fun claimsMap(): Map<String, String> = buildMap {
        profileName?.let { put("name", it) }
        email?.let { put("email", it) }
    }
}
