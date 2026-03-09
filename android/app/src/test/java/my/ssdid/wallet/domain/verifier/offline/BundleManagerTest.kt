package my.ssdid.wallet.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.revocation.StatusListCredential
import my.ssdid.wallet.domain.revocation.StatusListFetcher
import my.ssdid.wallet.domain.revocation.StatusListSubject
import my.ssdid.wallet.domain.verifier.Verifier
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class BundleManagerTest {

    private lateinit var verifier: Verifier
    private lateinit var statusListFetcher: StatusListFetcher
    private lateinit var bundleStore: BundleStore
    private lateinit var manager: BundleManager

    private val testDidDoc = DidDocument(
        id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
        verificationMethod = listOf(
            VerificationMethod(
                id = "did:ssdid:issuer#key-1", type = "Ed25519VerificationKey2020",
                controller = "did:ssdid:issuer", publicKeyMultibase = "uPubKey"
            )
        ),
        authentication = listOf("did:ssdid:issuer#key-1"),
        assertionMethod = listOf("did:ssdid:issuer#key-1")
    )

    @Before
    fun setup() {
        verifier = mockk()
        statusListFetcher = mockk()
        bundleStore = mockk(relaxed = true)
        manager = BundleManager(verifier, statusListFetcher, bundleStore, Duration.ofHours(24))
    }

    @Test
    fun `prefetchBundle resolves DID and saves bundle`() = runTest {
        coEvery { verifier.resolveDid("did:ssdid:issuer") } returns Result.success(testDidDoc)

        val result = manager.prefetchBundle("did:ssdid:issuer")

        assertThat(result.isSuccess).isTrue()
        val bundle = result.getOrThrow()
        assertThat(bundle.issuerDid).isEqualTo("did:ssdid:issuer")
        assertThat(bundle.didDocument).isEqualTo(testDidDoc)
        assertThat(bundle.statusList).isNull()
        coVerify { bundleStore.saveBundle(any()) }
    }

    @Test
    fun `prefetchBundle includes status list when URL provided`() = runTest {
        coEvery { verifier.resolveDid("did:ssdid:issuer") } returns Result.success(testDidDoc)
        val statusList = StatusListCredential(
            type = listOf("VerifiableCredential"), issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList", statusPurpose = "revocation",
                encodedList = "encodedData"
            )
        )
        coEvery { statusListFetcher.fetch("https://example.com/status") } returns Result.success(statusList)

        val result = manager.prefetchBundle("did:ssdid:issuer", "https://example.com/status")

        assertThat(result.getOrThrow().statusList).isEqualTo(statusList)
    }

    @Test
    fun `prefetchBundle succeeds even if status list fetch fails`() = runTest {
        coEvery { verifier.resolveDid("did:ssdid:issuer") } returns Result.success(testDidDoc)
        coEvery { statusListFetcher.fetch(any()) } returns Result.failure(RuntimeException("offline"))

        val result = manager.prefetchBundle("did:ssdid:issuer", "https://example.com/status")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().statusList).isNull()
    }

    @Test
    fun `prefetchBundle fails when DID resolution fails`() = runTest {
        coEvery { verifier.resolveDid(any()) } returns Result.failure(RuntimeException("network error"))

        val result = manager.prefetchBundle("did:ssdid:unknown")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `hasFreshBundle returns true for non-expired bundle`() = runTest {
        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = testDidDoc,
            fetchedAt = Instant.now().toString(),
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        assertThat(manager.hasFreshBundle("did:ssdid:issuer")).isTrue()
    }

    @Test
    fun `hasFreshBundle returns false for expired bundle`() = runTest {
        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = testDidDoc,
            fetchedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString(),
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        assertThat(manager.hasFreshBundle("did:ssdid:issuer")).isFalse()
    }

    @Test
    fun `hasFreshBundle returns false when no bundle`() = runTest {
        coEvery { bundleStore.getBundle(any()) } returns null

        assertThat(manager.hasFreshBundle("did:ssdid:unknown")).isFalse()
    }

    @Test
    fun `refreshStaleBundles refreshes expired bundles`() = runTest {
        val staleBundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = testDidDoc,
            fetchedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString(),
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.listBundles() } returns listOf(staleBundle)
        coEvery { verifier.resolveDid("did:ssdid:issuer") } returns Result.success(testDidDoc)

        val count = manager.refreshStaleBundles()

        assertThat(count).isEqualTo(1)
        coVerify { bundleStore.saveBundle(match { it.issuerDid == "did:ssdid:issuer" }) }
    }
}
