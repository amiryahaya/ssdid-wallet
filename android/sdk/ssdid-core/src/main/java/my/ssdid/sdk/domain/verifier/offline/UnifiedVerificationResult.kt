package my.ssdid.sdk.domain.verifier.offline

import java.time.Duration

data class UnifiedVerificationResult(
    val status: VerificationStatus,
    val checks: List<VerificationCheck>,
    val source: VerificationSource,
    val bundleAge: Duration? = null
)

enum class VerificationStatus { VERIFIED, VERIFIED_OFFLINE, FAILED, DEGRADED }
enum class VerificationSource { ONLINE, OFFLINE }

data class VerificationCheck(
    val type: CheckType,
    val status: CheckStatus,
    val message: String
)

enum class CheckType { SIGNATURE, EXPIRY, REVOCATION, BUNDLE_FRESHNESS }
enum class CheckStatus { PASS, FAIL, UNKNOWN }
