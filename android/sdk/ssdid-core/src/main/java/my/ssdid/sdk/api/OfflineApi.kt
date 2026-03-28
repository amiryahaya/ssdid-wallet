package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.OfflineVerificationResult
import my.ssdid.sdk.domain.verifier.offline.OfflineVerifier
import my.ssdid.sdk.domain.verifier.offline.UnifiedVerificationResult
import my.ssdid.sdk.domain.verifier.offline.VerificationBundle
import my.ssdid.sdk.domain.verifier.offline.VerificationOrchestrator

class OfflineApi internal constructor(
    private val offlineVerifier: OfflineVerifier,
    private val bundleManager: BundleManager,
    private val orchestrator: VerificationOrchestrator
) {
    suspend fun prefetchBundle(issuerDid: String, statusListUrl: String? = null): Result<VerificationBundle> =
        bundleManager.prefetchBundle(issuerDid, statusListUrl)
    suspend fun refreshStaleBundles(): Int = bundleManager.refreshStaleBundles()
    suspend fun hasFreshBundle(issuerDid: String): Boolean = bundleManager.hasFreshBundle(issuerDid)
    suspend fun verifyOffline(credential: VerifiableCredential): OfflineVerificationResult =
        offlineVerifier.verifyCredential(credential)
    suspend fun verify(credential: VerifiableCredential): UnifiedVerificationResult =
        orchestrator.verify(credential)
}
