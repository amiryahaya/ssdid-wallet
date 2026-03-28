package my.ssdid.sdk.domain.verifier.offline

import my.ssdid.sdk.domain.revocation.StatusListFetcher
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.verifier.Verifier
import java.time.Instant

/**
 * Manages pre-fetching and caching of verification bundles for offline use.
 * Bundles include DID Documents and optional status list snapshots.
 * Bundle TTL is driven by TtlProvider.
 */
class BundleManager(
    private val verifier: Verifier,
    private val statusListFetcher: StatusListFetcher,
    private val bundleStore: BundleStore,
    private val ttlProvider: TtlProvider
) {
    /**
     * Pre-fetch and cache a verification bundle for an issuer.
     * Resolves the DID Document online and optionally fetches the status list.
     */
    suspend fun prefetchBundle(
        issuerDid: String,
        statusListUrl: String? = null
    ): Result<VerificationBundle> = runCatching {
        val didDocument = verifier.resolveDid(issuerDid).getOrThrow()
        val now = Instant.now()

        val statusList = statusListUrl?.let {
            statusListFetcher.fetch(it).getOrNull()
        }

        val bundle = VerificationBundle(
            issuerDid = issuerDid,
            didDocument = didDocument,
            statusList = statusList,
            fetchedAt = now.toString(),
            expiresAt = now.plus(ttlProvider.getTtl()).toString()
        )
        bundleStore.saveBundle(bundle)
        bundle
    }

    /**
     * Refresh all cached bundles that are stale or about to expire.
     * Returns count of successfully refreshed bundles.
     */
    suspend fun refreshStaleBundles(): Int {
        val bundles = bundleStore.listBundles()
        var refreshed = 0
        for (bundle in bundles) {
            val isStale = try {
                ttlProvider.isExpired(bundle.fetchedAt)
            } catch (_: Exception) { true }

            if (isStale) {
                val statusListUrl = bundle.statusList?.id
                val result = prefetchBundle(bundle.issuerDid, statusListUrl)
                if (result.isSuccess) refreshed++
            }
        }
        return refreshed
    }

    /**
     * Check if a bundle exists and is still fresh for the given issuer.
     */
    suspend fun hasFreshBundle(issuerDid: String): Boolean {
        val bundle = bundleStore.getBundle(issuerDid) ?: return false
        return try {
            !ttlProvider.isExpired(bundle.fetchedAt)
        } catch (_: Exception) { false }
    }
}
