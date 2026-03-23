import Foundation

/// Fetches DID documents from the registry and caches them as VerificationBundles
/// for offline credential verification. Bundles have a 7-day TTL.
final class BundleFetcher {
    private let registryApi: RegistryApi
    private let bundleStore: BundleStore

    private static let bundleTtlSeconds: TimeInterval = 7 * 86400 // 7 days

    init(registryApi: RegistryApi, bundleStore: BundleStore) {
        self.registryApi = registryApi
        self.bundleStore = bundleStore
    }

    /// Resolve an issuer's DID document and cache it as a VerificationBundle.
    /// Returns nil on network errors (offline verification is best-effort).
    func fetchAndCache(issuerDid: String) async -> VerificationBundle? {
        do {
            let didDoc = try await registryApi.resolveDid(did: issuerDid)
            let formatter = ISO8601DateFormatter()
            let now = Date()
            let bundle = VerificationBundle(
                issuerDid: issuerDid,
                didDocument: didDoc,
                fetchedAt: formatter.string(from: now),
                expiresAt: formatter.string(from: now.addingTimeInterval(Self.bundleTtlSeconds))
            )
            try await bundleStore.saveBundle(bundle)
            return bundle
        } catch {
            return nil // Network error — offline verification is best-effort
        }
    }
}
