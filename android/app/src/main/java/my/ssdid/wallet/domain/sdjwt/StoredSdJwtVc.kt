package my.ssdid.wallet.domain.sdjwt

import kotlinx.serialization.Serializable

/**
 * Metadata for a stored SD-JWT VC, alongside the compact SD-JWT string.
 */
@Serializable
data class StoredSdJwtVc(
    val id: String,
    val compact: String,
    val issuer: String,
    val subject: String,
    val type: String,
    val claims: Map<String, String>,
    val disclosableClaims: List<String>,
    val issuedAt: Long,
    val expiresAt: Long? = null
)
