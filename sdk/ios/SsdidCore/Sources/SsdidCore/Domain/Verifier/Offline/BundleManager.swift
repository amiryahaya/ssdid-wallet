import Foundation

/// Manages pre-fetching and caching of verification bundles for offline use.
/// Bundles include DID Documents and optional status list snapshots.
/// Bundle TTL is driven by TtlProvider.
public final class BundleManager: @unchecked Sendable {
    private let verifier: Verifier
    private let statusListFetcher: StatusListFetcher
    private let bundleStore: BundleStore
    private let ttlProvider: TtlProvider

    public     init(
        verifier: Verifier,
        statusListFetcher: StatusListFetcher,
        bundleStore: BundleStore,
        ttlProvider: TtlProvider
    ) {
        self.verifier = verifier
        self.statusListFetcher = statusListFetcher
        self.bundleStore = bundleStore
        self.ttlProvider = ttlProvider
    }

    /// Pre-fetch and cache a verification bundle for an issuer.
    /// Resolves the DID Document online and optionally fetches the status list.
    public     func prefetchBundle(issuerDid: String, statusListUrl: String? = nil) async -> Result<VerificationBundle, Error> {
        do {
            let didDocument = try await verifier.resolveDid(did: issuerDid)
            let now = Date()
            let formatter = ISO8601DateFormatter()

            var statusList: StatusListCredential?
            if let url = statusListUrl {
                statusList = try? await statusListFetcher.fetch(url: url)
            }

            let bundle = VerificationBundle(
                issuerDid: issuerDid,
                didDocument: didDocument,
                statusList: statusList,
                fetchedAt: formatter.string(from: now),
                expiresAt: formatter.string(from: now.addingTimeInterval(ttlProvider.ttl))
            )
            try await bundleStore.saveBundle(bundle)
            return .success(bundle)
        } catch {
            return .failure(error)
        }
    }

    /// Refresh all cached bundles that are stale or about to expire.
    /// Returns count of successfully refreshed bundles.
    public     func refreshStaleBundles() async -> Int {
        guard let bundles = try? await bundleStore.listBundles() else { return 0 }
        var refreshed = 0
        for bundle in bundles {
            let isStale = ttlProvider.isExpired(fetchedAt: bundle.fetchedAt)
            if isStale {
                let statusListUrl = bundle.statusList?.id
                let result = await prefetchBundle(issuerDid: bundle.issuerDid, statusListUrl: statusListUrl)
                if case .success = result { refreshed += 1 }
            }
        }
        return refreshed
    }

    /// Check if a bundle exists and is still fresh for the given issuer.
    public     func hasFreshBundle(issuerDid: String) async -> Bool {
        guard let bundle = await bundleStore.getBundle(issuerDid: issuerDid) else { return false }
        return !ttlProvider.isExpired(fetchedAt: bundle.fetchedAt)
    }
}
