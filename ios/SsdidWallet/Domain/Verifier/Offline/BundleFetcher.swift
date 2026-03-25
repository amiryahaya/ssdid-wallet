import Foundation

/// Fetches DID documents from the registry and caches them as VerificationBundles
/// for offline credential verification. The TTL is determined by TtlProvider.
final class BundleFetcher {
    private let registryApi: RegistryApi
    private let bundleStore: BundleStore
    private let ttlProvider: TtlProvider

    init(registryApi: RegistryApi, bundleStore: BundleStore, ttlProvider: TtlProvider = TtlProvider()) {
        self.registryApi = registryApi
        self.bundleStore = bundleStore
        self.ttlProvider = ttlProvider
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
                expiresAt: formatter.string(from: now.addingTimeInterval(ttlProvider.ttl))
            )
            try await bundleStore.saveBundle(bundle)
            return bundle
        } catch {
            return nil // Network error — offline verification is best-effort
        }
    }
}
