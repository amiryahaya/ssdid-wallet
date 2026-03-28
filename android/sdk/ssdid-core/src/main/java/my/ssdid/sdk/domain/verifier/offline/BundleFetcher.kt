package my.ssdid.sdk.domain.verifier.offline

import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.transport.RegistryApi
import java.time.Instant

/**
 * Fetches DID documents from the registry and caches them as VerificationBundles
 * for offline credential verification. Bundle TTL is driven by TtlProvider.
 */
class BundleFetcher(
    private val registryApi: RegistryApi,
    private val bundleStore: BundleStore,
    private val ttlProvider: TtlProvider
) {
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
                expiresAt = now.plus(ttlProvider.getTtl()).toString()
            )
            bundleStore.saveBundle(bundle)
            bundle
        } catch (_: Exception) {
            null // Network error — offline verification is best-effort
        }
    }
}
