package my.ssdid.wallet.domain.recovery.social

import kotlinx.serialization.Serializable

@Serializable
data class Guardian(
    val id: String,
    val name: String,
    val did: String,
    val shareIndex: Int,
    val enrolledAt: String
)

@Serializable
data class SocialRecoveryConfig(
    val did: String,
    val threshold: Int,
    val totalShares: Int,
    val guardians: List<Guardian>,
    val createdAt: String
)
