package my.ssdid.wallet.ui.offline

// Category: e2e, offline

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
        offlineVerifier = OfflineVerifier(classicalProvider, pqcProvider, bundleStore, ttlProvider)
        orchestrator = VerificationOrchestrator(fakeVerifier, offlineVerifier, bundleStore)
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
            assertThat(check.status).isEqualTo(CheckStatus.PASS)
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

    // B2: DID key rotation — credential signed with new key B, bundle has old key A → FAILED
    @Test
    fun keyRotation_oldBundle_newCredential_returnsFailed() = runTest {
        // Create fresh bundle with old key (already set up in setUp)
        val bundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        bundleStore.saveBundle(bundle)

        // Generate a new key pair B and sign a credential with key-2 (not in DID doc)
        val (newPubKey, newPrivKey) = OfflineTestHelper.createKeyPair()
        val newKeyId = "$issuerDid#key-2"
        val credential = OfflineTestHelper.createTestCredential(issuerDid, newKeyId, newPrivKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        // key-2 not in cached DID doc → signature lookup fails → FAILED
        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
        assertThat(result.checks.first { it.type == CheckType.SIGNATURE }.status).isEqualTo(CheckStatus.FAIL)
    }

    // B3: Storage failure — FailingBundleStore throws on saveBundle, verifier returns graceful error
    @Test
    fun storageFailure_bundleFetchStillReturnsBundle() = runTest {
        val failingStore = object : BundleStore {
            override suspend fun saveBundle(bundle: VerificationBundle) {
                throw IOException("Disk full")
            }
            override suspend fun getBundle(issuerDid: String): VerificationBundle? = null
            override suspend fun deleteBundle(issuerDid: String) {}
            override suspend fun listBundles(): List<VerificationBundle> = emptyList()
        }

        val classicalProvider = ClassicalProvider()
        val failingOfflineVerifier = OfflineVerifier(classicalProvider, classicalProvider, failingStore, ttlProvider)
        val failingOrchestrator = VerificationOrchestrator(fakeVerifier, failingOfflineVerifier, failingStore)

        val credential = OfflineTestHelper.createTestCredential(issuerDid, keyId, privateKey)
        fakeVerifier.shouldThrow = IOException("no network")

        val result = failingOrchestrator.verify(credential)

        // No bundle in failing store → offline verifier returns missing bundle error → FAILED
        assertThat(result.status).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.source).isEqualTo(VerificationSource.OFFLINE)
    }

    // B7: Status list URL mismatch → revocation UNKNOWN → DEGRADED
    @Test
    fun statusListUrlMismatch_returnsUnknownRevocation() = runTest {
        val revokedIndex = 5
        val bitstring = OfflineTestHelper.createStatusListBitstring(revokedIndices = emptySet())
        // Status list cached with id = ".../status/1"
        val statusListCredential = StatusListCredential(
            context = listOf("https://www.w3.org/ns/credentials/v2"),
            id = "https://registry.ssdid.my/status/1",
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = issuerDid,
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList",
                statusPurpose = "revocation",
                encodedList = bitstring
            ),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = keyId,
                proofPurpose = "assertionMethod",
                proofValue = "uDummy"
            )
        )
        val bundle = OfflineTestHelper.createBundle(
            issuerDid, didDocument, freshnessRatio = 0.1, statusList = statusListCredential
        )
        bundleStore.saveBundle(bundle)

        // Credential references ".../status/2" — mismatched URL
        val credentialStatus = CredentialStatus(
            id = "https://registry.ssdid.my/status/2#$revokedIndex",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = revokedIndex.toString(),
            statusListCredential = "https://registry.ssdid.my/status/2"
        )
        val credential = OfflineTestHelper.createTestCredential(
            issuerDid, keyId, privateKey, credentialStatus = credentialStatus
        )
        fakeVerifier.shouldThrow = IOException("no network")

        val result = orchestrator.verify(credential)

        // URL mismatch → UNKNOWN revocation → DEGRADED (signature valid, bundle fresh)
        assertThat(result.status).isEqualTo(VerificationStatus.DEGRADED)
        assertThat(result.checks.first { it.type == CheckType.REVOCATION }.status).isEqualTo(CheckStatus.UNKNOWN)
    }
}
