package my.ssdid.wallet.domain.verifier.offline

import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.revocation.RevocationStatus
import my.ssdid.wallet.domain.verifier.Verifier
import java.io.IOException
import java.time.Duration
import java.time.Instant

class VerificationOrchestrator(
    private val onlineVerifier: Verifier,
    private val offlineVerifier: OfflineVerifier,
    private val bundleStore: BundleStore
) {

    suspend fun verify(credential: VerifiableCredential): UnifiedVerificationResult {
        val onlineResult = onlineVerifier.verifyCredential(credential)

        onlineResult.onSuccess { valid ->
            return if (valid) {
                UnifiedVerificationResult(
                    status = VerificationStatus.VERIFIED,
                    checks = listOf(
                        VerificationCheck(CheckType.SIGNATURE, CheckStatus.PASS, "Signature verified online"),
                        VerificationCheck(CheckType.EXPIRY, CheckStatus.PASS, "Credential is not expired"),
                        VerificationCheck(CheckType.REVOCATION, CheckStatus.PASS, "Revocation status confirmed online")
                    ),
                    source = VerificationSource.ONLINE
                )
            } else {
                UnifiedVerificationResult(
                    status = VerificationStatus.FAILED,
                    checks = listOf(
                        VerificationCheck(CheckType.SIGNATURE, CheckStatus.FAIL, "Signature verification failed online")
                    ),
                    source = VerificationSource.ONLINE
                )
            }
        }

        onlineResult.onFailure { error ->
            if (!isNetworkError(error)) {
                return UnifiedVerificationResult(
                    status = VerificationStatus.FAILED,
                    checks = listOf(
                        VerificationCheck(CheckType.SIGNATURE, CheckStatus.FAIL, "Verification error: ${error.message}")
                    ),
                    source = VerificationSource.ONLINE
                )
            }
        }

        // Fall back to offline verification
        return verifyOffline(credential)
    }

    private suspend fun verifyOffline(credential: VerifiableCredential): UnifiedVerificationResult {
        val offlineResult = offlineVerifier.verifyCredential(credential)
        val bundle = bundleStore.getBundle(credential.issuer)
        val bundleAge = bundle?.let {
            try {
                Duration.between(Instant.parse(it.fetchedAt), Instant.now())
            } catch (_: Exception) {
                null
            }
        }

        val expiryFailed = credential.expirationDate?.let {
            try { java.time.Instant.parse(it).isBefore(java.time.Instant.now()) }
            catch (e: Exception) { false }
        } ?: false

        val status = when {
            offlineResult.error != null -> VerificationStatus.FAILED
            expiryFailed -> VerificationStatus.FAILED
            !offlineResult.signatureValid -> VerificationStatus.FAILED
            offlineResult.revocationStatus == RevocationStatus.REVOKED -> VerificationStatus.FAILED
            !offlineResult.bundleFresh -> VerificationStatus.DEGRADED
            offlineResult.revocationStatus == RevocationStatus.UNKNOWN -> VerificationStatus.DEGRADED
            else -> VerificationStatus.VERIFIED_OFFLINE
        }

        val checks = buildList {
            // Signature check
            add(
                VerificationCheck(
                    type = CheckType.SIGNATURE,
                    status = if (offlineResult.signatureValid) CheckStatus.PASS else CheckStatus.FAIL,
                    message = if (offlineResult.signatureValid) "Signature valid (offline)" else "Signature invalid (offline)"
                )
            )

            // Expiry check — uses the expiryFailed computed above
            add(
                VerificationCheck(
                    type = CheckType.EXPIRY,
                    status = if (expiryFailed) CheckStatus.FAIL else CheckStatus.PASS,
                    message = if (expiryFailed) offlineResult.error ?: "Credential expired" else "Not expired"
                )
            )

            // Revocation check
            val revocationCheckStatus = when (offlineResult.revocationStatus) {
                RevocationStatus.VALID -> CheckStatus.PASS
                RevocationStatus.REVOKED -> CheckStatus.FAIL
                RevocationStatus.UNKNOWN -> CheckStatus.UNKNOWN
            }
            add(
                VerificationCheck(
                    type = CheckType.REVOCATION,
                    status = revocationCheckStatus,
                    message = when (offlineResult.revocationStatus) {
                        RevocationStatus.VALID -> "Not revoked"
                        RevocationStatus.REVOKED -> "Credential is revoked"
                        RevocationStatus.UNKNOWN -> "Revocation status unknown"
                    }
                )
            )

            // Bundle freshness check
            add(
                VerificationCheck(
                    type = CheckType.BUNDLE_FRESHNESS,
                    status = if (offlineResult.bundleFresh) CheckStatus.PASS else CheckStatus.FAIL,
                    message = if (offlineResult.bundleFresh) "Bundle is fresh" else "Bundle is stale"
                )
            )
        }

        return UnifiedVerificationResult(
            status = status,
            checks = checks,
            source = VerificationSource.OFFLINE,
            bundleAge = bundleAge
        )
    }

    private fun isNetworkError(error: Throwable): Boolean {
        if (error is IOException) return true
        if (error is retrofit2.HttpException && error.code() in 500..599) return true
        return false
    }
}
