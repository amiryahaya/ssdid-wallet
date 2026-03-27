package my.ssdid.wallet.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerificationMethod
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.transport.RegistryApi
import org.junit.Test
import java.io.IOException

class BundleFetcherTest {

    private val registryApi: RegistryApi = mockk()

    private fun makeFetcher(store: BundleStore, ttlDays: Int = 7): BundleFetcher {
        val settings = FakeFetcherSettingsRepository(bundleTtlDays = ttlDays)
        val ttlProvider = TtlProvider(settings)
        return BundleFetcher(registryApi, store, ttlProvider)
    }

    @Test
    fun `fetchAndCache stores bundle for valid DID`() = runTest {
        val store = InMemoryBundleStore()
        val fetcher = makeFetcher(store)

        coEvery { registryApi.resolveDid("did:ssdid:test") } returns DidDocument(
            id = "did:ssdid:test",
            controller = "did:ssdid:test",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "did:ssdid:test#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:test",
                    publicKeyMultibase = "uABC"
                )
            ),
            authentication = listOf("did:ssdid:test#key-1"),
            assertionMethod = listOf("did:ssdid:test#key-1")
        )

        val bundle = fetcher.fetchAndCache("did:ssdid:test")

        assertThat(bundle).isNotNull()
        assertThat(bundle!!.issuerDid).isEqualTo("did:ssdid:test")
        assertThat(store.getBundle("did:ssdid:test")).isNotNull()
    }

    @Test
    fun `fetchAndCache returns null on network error`() = runTest {
        val store = InMemoryBundleStore()
        val fetcher = makeFetcher(store)

        coEvery { registryApi.resolveDid(any()) } throws IOException("Network error")

        val bundle = fetcher.fetchAndCache("did:ssdid:test")

        assertThat(bundle).isNull()
        assertThat(store.getBundle("did:ssdid:test")).isNull()
    }

    @Test
    fun `fetchAndCache sets 7-day expiry`() = runTest {
        val store = InMemoryBundleStore()
        val fetcher = makeFetcher(store, ttlDays = 7)

        coEvery { registryApi.resolveDid("did:ssdid:test") } returns DidDocument(
            id = "did:ssdid:test",
            controller = "did:ssdid:test",
            verificationMethod = emptyList(),
            authentication = emptyList(),
            assertionMethod = emptyList()
        )

        val bundle = fetcher.fetchAndCache("did:ssdid:test")

        assertThat(bundle).isNotNull()
        assertThat(bundle!!.expiresAt).isNotEmpty()
        // Verify expiry is roughly 7 days from now
        val expiresAt = java.time.Instant.parse(bundle.expiresAt)
        val now = java.time.Instant.now()
        val daysUntilExpiry = java.time.Duration.between(now, expiresAt).toDays()
        assertThat(daysUntilExpiry).isAtLeast(6)
        assertThat(daysUntilExpiry).isAtMost(7)
    }
}

/** Fake SettingsRepository for BundleFetcherTest — returns configurable bundleTtlDays. */
private class FakeFetcherSettingsRepository(
    private val bundleTtlDays: Int = 7
) : SettingsRepository {
    override fun biometricEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
    override fun autoLockMinutes(): Flow<Int> = flowOf(5)
    override suspend fun setAutoLockMinutes(minutes: Int) = Unit
    override fun defaultAlgorithm(): Flow<String> = flowOf("Ed25519")
    override suspend fun setDefaultAlgorithm(algorithm: String) = Unit
    override fun language(): Flow<String> = flowOf("en")
    override suspend fun setLanguage(language: String) = Unit
    override fun bundleTtlDays(): Flow<Int> = flowOf(bundleTtlDays)
    override suspend fun setBundleTtlDays(days: Int) = Unit
}

/** Simple in-memory BundleStore for testing. */
private class InMemoryBundleStore : BundleStore {
    private val bundles = mutableMapOf<String, VerificationBundle>()

    override suspend fun saveBundle(bundle: VerificationBundle) {
        bundles[bundle.issuerDid] = bundle
    }

    override suspend fun getBundle(issuerDid: String): VerificationBundle? = bundles[issuerDid]

    override suspend fun deleteBundle(issuerDid: String) {
        bundles.remove(issuerDid)
    }

    override suspend fun listBundles(): List<VerificationBundle> = bundles.values.toList()
}
