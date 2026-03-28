package my.ssdid.sdk.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.revocation.RevocationStatus
import my.ssdid.sdk.domain.verifier.Verifier
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant

class VerificationOrchestratorTest {

    private lateinit var onlineVerifier: Verifier
    private lateinit var offlineVerifier: OfflineVerifier
    private lateinit var bundleStore: BundleStore
    private lateinit var orchestrator: VerificationOrchestrator

    private val testCredential = VerifiableCredential(
        id = "vc-test-1",
        type = listOf("VerifiableCredential"),
        issuer = "did:ssdid:issuer123",
        issuanceDate = "2024-01-01T00:00:00Z",
        credentialSubject = CredentialSubject(id = "did:ssdid:holder1"),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer123#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zABCDEF"
        )
    )

    private val testBundle = VerificationBundle(
        issuerDid = "did:ssdid:issuer123",
        didDocument = mockk(relaxed = true),
        fetchedAt = Instant.now().minus(Duration.ofHours(2)).toString(),
        expiresAt = Instant.now().plus(Duration.ofDays(5)).toString()
    )

    private val freshOfflineResult = OfflineVerificationResult(
        signatureValid = true,
        revocationStatus = RevocationStatus.VALID,
        bundleFresh = true
    )

    @Before
    fun setup() {
        onlineVerifier = mockk()
        offlineVerifier = mockk()
        bundleStore = mockk()
        orchestrator = VerificationOrchestrator(onlineVerifier, offlineVerifier, bundleStore)
    }

    // Test 1: returns VERIFIED when online succeeds
    @Test
    fun `returns VERIFIED when online succeeds`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.success(true)

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
    }

    // Test 2: returns FAILED when online verification returns false (not network error)
    @Test
    fun `returns FAILED when online verification returns false`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.success(false)

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
    }

    // Test 3: falls back to offline on IOException
    @Test
    fun `falls back to offline on IOException`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns freshOfflineResult
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED_OFFLINE)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        assertThat(result.bundleAge).isNotNull()
        assertThat(result.bundleAge!!.toHours()).isAtLeast(1)
        val signatureCheck = result.checks.first { it.type == CheckType.SIGNATURE }
        assertThat(signatureCheck.status).isEqualTo(CheckStatus.PASS)
        val bundleFreshnessCheck = result.checks.first { it.type == CheckType.BUNDLE_FRESHNESS }
        assertThat(bundleFreshnessCheck.status).isEqualTo(CheckStatus.PASS)
    }

    // Test 4: falls back to offline on HTTP 5xx
    @Test
    fun `falls back to offline on HTTP 5xx`() = runTest {
        val httpException = HttpException(
            Response.error<Any>(503, "Service Unavailable".toResponseBody())
        )
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(httpException)
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns freshOfflineResult
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED_OFFLINE)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        val revocationCheck = result.checks.first { it.type == CheckType.REVOCATION }
        assertThat(revocationCheck.status).isEqualTo(CheckStatus.PASS)
        val signatureCheck = result.checks.first { it.type == CheckType.SIGNATURE }
        assertThat(signatureCheck.status).isEqualTo(CheckStatus.PASS)
    }

    // Test 5: returns DEGRADED when offline has stale bundle
    @Test
    fun `returns DEGRADED when offline has stale bundle`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns OfflineVerificationResult(
            signatureValid = true,
            revocationStatus = RevocationStatus.VALID,
            bundleFresh = false
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.DEGRADED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        val bundleFreshnessCheck = result.checks.first { it.type == CheckType.BUNDLE_FRESHNESS }
        assertThat(bundleFreshnessCheck.status).isEqualTo(CheckStatus.FAIL)
    }

    // Test 6: returns DEGRADED when revocation status unknown
    @Test
    fun `returns DEGRADED when revocation status unknown`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns OfflineVerificationResult(
            signatureValid = true,
            revocationStatus = RevocationStatus.UNKNOWN,
            bundleFresh = true
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.DEGRADED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
    }

    // Test 7: returns FAILED when offline signature invalid
    @Test
    fun `returns FAILED when offline signature invalid`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns OfflineVerificationResult(
            signatureValid = false,
            revocationStatus = RevocationStatus.VALID,
            bundleFresh = true
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
    }

    // Test 8: returns FAILED when offline has error
    @Test
    fun `returns FAILED when offline has error`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns OfflineVerificationResult(
            signatureValid = false,
            revocationStatus = RevocationStatus.UNKNOWN,
            bundleFresh = false,
            error = "No cached bundle for issuer"
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns null

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
    }

    // Test 9: returns FAILED when credential is revoked offline
    @Test
    fun `returns FAILED when credential is revoked offline`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(IOException("No network"))
        coEvery { offlineVerifier.verifyCredential(testCredential) } returns OfflineVerificationResult(
            signatureValid = true,
            revocationStatus = RevocationStatus.REVOKED,
            bundleFresh = true
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer123") } returns testBundle

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        val revocationCheck = result.checks.first { it.type == CheckType.REVOCATION }
        assertThat(revocationCheck.status).isEqualTo(CheckStatus.FAIL)
    }

    // Test 10: does not fall back on SecurityException
    @Test
    fun `does not fall back on SecurityException`() = runTest {
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(SecurityException("Security error"))

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
    }

    // Test 11: does not fall back on HTTP 4xx
    @Test
    fun `does not fall back on HTTP 4xx`() = runTest {
        val httpException = HttpException(
            Response.error<Any>(404, "".toResponseBody())
        )
        coEvery { onlineVerifier.verifyCredential(testCredential) } returns Result.failure(httpException)

        val result = orchestrator.verify(testCredential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
        coVerify(exactly = 0) { offlineVerifier.verifyCredential(any()) }
    }
}
