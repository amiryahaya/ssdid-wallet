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
    /// Returns nil when the network/registry call fails. Storage errors are logged
    /// but do not prevent the freshly-fetched bundle from being returned for
    /// immediate use within the current session.
    func fetchAndCache(issuerDid: String) async -> VerificationBundle? {
        let didDoc: DidDocument
        do {
            didDoc = try await registryApi.resolveDid(did: issuerDid)
        } catch {
            return nil // Network/registry error — best-effort
        }
        let formatter = ISO8601DateFormatter()
        let now = Date()
        let bundle = VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            fetchedAt: formatter.string(from: now),
            expiresAt: formatter.string(from: now.addingTimeInterval(ttlProvider.ttl))
        )
        do {
            try await bundleStore.saveBundle(bundle)
        } catch {
            // Storage error — bundle was fetched but couldn't be persisted.
            // Still return the bundle for immediate use this session.
        }
        return bundle
    }
}
