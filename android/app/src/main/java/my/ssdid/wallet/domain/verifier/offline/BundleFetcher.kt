package my.ssdid.wallet.domain.verifier.offline

import my.ssdid.wallet.domain.transport.RegistryApi
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Fetches DID documents from the registry and caches them as VerificationBundles
 * for offline credential verification. Bundles have a 7-day TTL.
 */
class BundleFetcher(
    private val registryApi: RegistryApi,
    private val bundleStore: BundleStore
) {
    companion object {
        private const val BUNDLE_TTL_DAYS = 7L
    }

    /**
     * Resolve an issuer's DID document and cache it as a VerificationBundle.
     * Returns null on network errors (offline verification is best-effort).
     */
    suspend fun fetchAndCache(issuerDid: String): VerificationBundle? {
        return try {
            val didDoc = registryApi.resolveDid(issuerDid)
            val now = Instant.now()
            val bundle = VerificationBundle(
                issuerDid = issuerDid,
                didDocument = didDoc,
                fetchedAt = now.toString(),
                expiresAt = now.plus(BUNDLE_TTL_DAYS, ChronoUnit.DAYS).toString()
            )
            bundleStore.saveBundle(bundle)
            bundle
        } catch (_: Exception) {
            null // Network error — offline verification is best-effort
        }
    }
}
