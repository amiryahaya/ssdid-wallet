package my.ssdid.wallet.domain.verifier.offline

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.revocation.StatusListCredential
import my.ssdid.wallet.domain.revocation.StatusListFetcher
import my.ssdid.wallet.domain.verifier.Verifier
import java.time.Duration
import java.time.Instant

/**
 * Manages pre-fetching and caching of verification bundles for offline use.
 * Bundles include DID Documents and optional status list snapshots.
 */
class BundleManager(
    private val verifier: Verifier,
    private val statusListFetcher: StatusListFetcher,
    private val bundleStore: BundleStore,
    private val bundleTtl: Duration = Duration.ofHours(24)
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
            expiresAt = now.plus(bundleTtl).toString()
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
                Instant.now().isAfter(Instant.parse(bundle.expiresAt))
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
            Instant.now().isBefore(Instant.parse(bundle.expiresAt))
        } catch (_: Exception) { false }
    }
}
