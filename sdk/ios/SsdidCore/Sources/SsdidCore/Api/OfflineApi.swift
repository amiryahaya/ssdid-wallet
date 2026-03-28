import Foundation

/// Facade for offline verification and bundle management.
public struct OfflineApi {
    let offlineVerifier: OfflineVerifier
    let bundleManager: BundleManager
    let orchestrator: VerificationOrchestrator

    public func prefetchBundle(issuerDid: String, statusListUrl: String? = nil) async -> Result<VerificationBundle, Error> {
        await bundleManager.prefetchBundle(issuerDid: issuerDid, statusListUrl: statusListUrl)
    }

    public func refreshStaleBundles() async -> Int {
        await bundleManager.refreshStaleBundles()
    }

    public func hasFreshBundle(issuerDid: String) async -> Bool {
        await bundleManager.hasFreshBundle(issuerDid: issuerDid)
    }

    public func verifyOffline(_ credential: VerifiableCredential) async -> OfflineVerificationResult {
        await offlineVerifier.verifyCredential(credential)
    }

    public func verify(_ credential: VerifiableCredential) async -> UnifiedVerificationResult {
        await orchestrator.verify(credential: credential)
    }
}
