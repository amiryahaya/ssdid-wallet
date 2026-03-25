package my.ssdid.wallet.ui.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.revocation.StatusListCredential
import my.ssdid.wallet.domain.revocation.StatusListSubject
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.verifier.offline.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Fake SettingsRepository for use in tests - provides default 7-day TTL.
 */
private class FakeSettingsRepository(ttlDays: Int = 7) : SettingsRepository {
    private val biometric = MutableStateFlow(true)
    private val autoLock = MutableStateFlow(5)
    private val algorithm = MutableStateFlow("ED25519")
    private val lang = MutableStateFlow("en")
    private val bundleTtl = MutableStateFlow(ttlDays)

    override fun biometricEnabled() = biometric
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometric.value = enabled }
    override fun autoLockMinutes() = autoLock
    override suspend fun setAutoLockMinutes(minutes: Int) { autoLock.value = minutes }
    override fun defaultAlgorithm() = algorithm
    override suspend fun setDefaultAlgorithm(algorithm: String) { this.algorithm.value = algorithm }
    override fun language() = lang
    override suspend fun setLanguage(language: String) { lang.value = language }
    override fun bundleTtlDays() = bundleTtl
    override suspend fun setBundleTtlDays(days: Int) { bundleTtl.value = days }
}

@RunWith(AndroidJUnit4::class)
class OfflineVerificationTest {

    private lateinit var fakeVerifier: FakeOnlineVerifier
    private lateinit var bundleStore: InMemoryBundleStore
    private lateinit var offlineVerifier: OfflineVerifier
    private lateinit var orchestrator: VerificationOrchestrator
    private lateinit var ttlProvider: TtlProvider

    private lateinit var issuerDid: String
    private lateinit var keyId: String
    private lateinit var privateKey: ByteArray
    private lateinit var didDocument: DidDocument

    @Before
    fun setUp() {
        val (pubKey, privKey) = OfflineTestHelper.createKeyPair()
        privateKey = privKey
        issuerDid = "did:ssdid:test-issuer-${System.currentTimeMillis()}"
        keyId = "$issuerDid#key-1"
        didDocument = OfflineTestHelper.createDidDocument(issuerDid, keyId, pubKey)

        fakeVerifier = FakeOnlineVerifier()
        bundleStore = InMemoryBundleStore()
        ttlProvider = TtlProvider(FakeSettingsRepository(ttlDays = 7))
        val classicalProvider = ClassicalProvider()
        val pqcProvider = ClassicalProvider() // placeholder for interface, Ed25519 only
        offlineVerifier = OfflineVerifier(classicalProvider, pqcProvider, bundleStore)
        orchestrator = VerificationOrchestrator(fakeVerifier, offlineVerifier, bundleStore, ttlProvider)
    }

    // UC-9: Network error with fresh bundle → VERIFIED_OFFLINE
    @Test
    fun networkError_freshBundle_returnsVerifiedOffline() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)
        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED_OFFLINE)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        assertThat(result.checks.first { it.type == CheckType.SIGNATURE }.status).isEqualTo(CheckStatus.PASS)
        assertThat(result.bundleAge).isNotNull()
    }

    // UC-10: Network error with stale bundle → DEGRADED
    @Test
    fun networkError_staleBundle_returnsDegraded() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 1.5)
        bundleStore.saveBundle(bundle)
        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.DEGRADED)
        assertThat(result.checks.first { it.type == CheckType.BUNDLE_FRESHNESS }.status).isEqualTo(CheckStatus.FAIL)
    }

    // UC-11: Network error with no bundle → FAILED
    @Test
    fun networkError_noBundle_returnsFailed() = runTest {
        // bundleStore is empty
        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
    }

    // UC-12: Expired credential → FAILED
    @Test
    fun expiredCredential_returnsFailed() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)
        val pastDate = Instant.now().minus(Duration.ofDays(30)).toString()
        val credential = OfflineTestHelper.createTestCredential(
            issuerDid, keyId, privateKey, expirationDate = pastDate
        )
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.checks.first { it.type == CheckType.EXPIRY }.status).isEqualTo(CheckStatus.FAIL)
    }

    // UC-13: Revoked credential via cached status list → FAILED
    @Test
    fun revokedCredential_returnsFailed() = runTest {
        val revokedIndex = 42
        val bitstring = OfflineTestHelper.createStatusListBitstring(revokedIndices = setOf(revokedIndex))
        val statusListCredential = StatusListCredential(
            context = listOf("https://www.w3.org/ns/credentials/v2"),
            id = "https://registry.ssdid.my/status/1",
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = issuerDid,
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList",
                statusPurpose = "revocation",
                encodedList = bitstring
            )
        )
        val bundle = OfflineTestHelper.createBundle(
            issuerDid, didDocument, freshnessRatio = 0.1, statusList = statusListCredential
        )
        bundleStore.saveBundle(bundle)
        val credentialStatus = CredentialStatus(
            id = "https://registry.ssdid.my/status/1#$revokedIndex",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = revokedIndex.toString(),
            statusListCredential = "https://registry.ssdid.my/status/1"
        )
        val credential = OfflineTestHelper.createTestCredential(
            issuerDid, keyId, privateKey, credentialStatus = credentialStatus
        )
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.checks.first { it.type == CheckType.REVOCATION }.status).isEqualTo(CheckStatus.FAIL)
    }

    // UC-7: Offline happy path — all checks pass
    @Test
    fun offlineHappyPath_allChecksPass() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)
        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED_OFFLINE)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        result.checks.forEach { check ->
            assertThat(check.status).isAnyOf(CheckStatus.PASS, CheckStatus.UNKNOWN)
        }
    }

    // Verification error (not network) does NOT trigger offline fallback
    @Test
    fun verificationError_doesNotFallbackToOffline() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)
        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = SecurityException("invalid signature")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
    }

    // Network error + fresh bundle + unknown revocation → DEGRADED
    @Test
    fun networkError_freshBundle_unknownRevocation_returnsDegraded() = runTest {
        // Bundle has NO status list → revocation check will be UNKNOWN
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1, statusList = null)
        bundleStore.saveBundle(bundle)
        // Credential has a credentialStatus field → will try to check revocation
        val credentialStatus = CredentialStatus(
            id = "https://registry.ssdid.my/status/1#5",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = "5",
            statusListCredential = "https://registry.ssdid.my/status/1"
        )
        val credential = OfflineTestHelper.createTestCredential(
            issuerDid, keyId, privateKey, credentialStatus = credentialStatus
        )
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.DEGRADED)
        assertThat(result.checks.first { it.type == CheckType.REVOCATION }.status).isEqualTo(CheckStatus.UNKNOWN)
        assertThat(result.checks.first { it.type == CheckType.BUNDLE_FRESHNESS }.status).isEqualTo(CheckStatus.PASS)
    }

    // Credential with no expiry date → VERIFIED_OFFLINE
    @Test
    fun noExpiryDate_returnsVerifiedOffline() = runTest {
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)
        // expirationDate = null
        val credential = OfflineTestHelper.createTestCredential(
            issuerDid, keyId, privateKey, expirationDate = null
        )
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        assertThat(result.status).isEqualTo(VerificationStatus.VERIFIED_OFFLINE)
        assertThat(result.checks.first { it.type == CheckType.EXPIRY }.status).isEqualTo(CheckStatus.PASS)
    }
}
