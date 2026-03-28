package my.ssdid.sdk.domain.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupPackage(
    val version: Int = 1,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
    val algorithms: List<String>,
    val dids: List<String>,
    val createdAt: String,
    val hmac: String
)

@Serializable
data class BackupPayload(
    val identities: List<BackupIdentity>
)

@Serializable
data class BackupIdentity(
    val keyId: String,
    val did: String,
    val name: String,
    val algorithm: String,
    val privateKey: String,
    val publicKey: String,
    val createdAt: String
)
