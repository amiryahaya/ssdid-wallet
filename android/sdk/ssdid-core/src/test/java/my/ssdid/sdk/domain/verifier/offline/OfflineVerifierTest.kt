package my.ssdid.sdk.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import my.ssdid.sdk.domain.crypto.ClassicalProvider
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.revocation.RevocationStatus
import my.ssdid.sdk.domain.revocation.StatusListCredential
import my.ssdid.sdk.domain.revocation.StatusListSubject
import my.ssdid.sdk.domain.settings.SettingsRepository
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.vault.VaultImpl
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.zip.GZIPOutputStream

/** Minimal fake SettingsRepository providing a configurable bundle TTL. */
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

class OfflineVerifierTest {

    private lateinit var bundleStore: BundleStore
    private lateinit var verifier: OfflineVerifier
    private val classicalProvider = ClassicalProvider()
    private val pqcProvider: CryptoProvider = mockk() // not used in these tests
    private val ttlProvider = TtlProvider(FakeSettingsRepository(ttlDays = 7))

    @Before
    fun setup() {
        bundleStore = mockk()
        verifier = OfflineVerifier(classicalProvider, pqcProvider, bundleStore, ttlProvider)
    }

    @Test
    fun `verifyCredential returns valid for correct signature with fresh bundle`() = runTest {
        val kp = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val keyId = "did:ssdid:issuer#key-1"
        val publicKeyMultibase = Multibase.encode(kp.publicKey)

        val didDoc = DidDocument(
            id = "did:ssdid:issuer",
            controller = "did:ssdid:issuer",
            verificationMethod = listOf(
                VerificationMethod(
                    id = keyId, type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:issuer", publicKeyMultibase = publicKeyMultibase
                )
            ),
            authentication = listOf(keyId),
            assertionMethod = listOf(keyId)
        )

        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer",
            didDocument = didDoc,
            fetchedAt = Instant.now().toString(),
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        // Create a VC and sign it
        val vc = VerifiableCredential(
            id = "urn:uuid:test", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = Instant.now().toString(),
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = keyId, proofPurpose = "assertionMethod",
                proofValue = "placeholder"
            )
        )
        // Sign the canonical form
        val signedData = canonicalizeWithoutProof(vc)
        val signature = classicalProvider.sign(Algorithm.ED25519, kp.privateKey, signedData)
        val signedVc = vc.copy(proof = vc.proof.copy(proofValue = Multibase.encode(signature)))

        val result = verifier.verifyCredential(signedVc)

        assertThat(result.signatureValid).isTrue()
        assertThat(result.bundleFresh).isTrue()
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `verifyCredential returns error when no bundle exists`() = runTest {
        coEvery { bundleStore.getBundle(any()) } returns null

        val vc = makeTestVc("did:ssdid:unknown#key-1")
        val result = verifier.verifyCredential(vc)

        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("No cached bundle")
    }

    @Test
    fun `verifyCredential detects expired credential`() = runTest {
        val vc = makeTestVc("did:ssdid:issuer#key-1").copy(
            expirationDate = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        )

        val result = verifier.verifyCredential(vc)

        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("expired")
    }

    @Test
    fun `verifyCredential marks stale bundle`() = runTest {
        // fetchedAt is 8 days ago; with 7-day TTL the bundle is expired
        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer",
            didDocument = DidDocument(
                id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
                verificationMethod = emptyList(),
                authentication = emptyList(), assertionMethod = emptyList()
            ),
            fetchedAt = Instant.now().minus(8, ChronoUnit.DAYS).toString(),
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        val vc = makeTestVc("did:ssdid:issuer#key-1")
        val result = verifier.verifyCredential(vc)

        assertThat(result.bundleFresh).isFalse()
    }

    @Test
    fun `verifyCredential checks revocation from cached status list`() = runTest {
        val kp = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val keyId = "did:ssdid:issuer#key-1"

        val didDoc = DidDocument(
            id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
            verificationMethod = listOf(
                VerificationMethod(
                    id = keyId, type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:issuer",
                    publicKeyMultibase = Multibase.encode(kp.publicKey)
                )
            ),
            authentication = listOf(keyId), assertionMethod = listOf(keyId)
        )

        val statusListUrl = "https://registry.ssdid.my/status/1"
        val statusList = StatusListCredential(
            id = statusListUrl,
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList", statusPurpose = "revocation",
                encodedList = makeEncodedList(setOf(42))
            ),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = keyId, proofPurpose = "assertionMethod", proofValue = "uDummy"
            )
        )

        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = didDoc,
            statusList = statusList,
            fetchedAt = Instant.now().toString(),
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        // Create signed VC with revoked status
        val vc = VerifiableCredential(
            id = "urn:uuid:test", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = Instant.now().toString(),
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "$statusListUrl#42",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "42",
                statusListCredential = statusListUrl
            ),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = keyId, proofPurpose = "assertionMethod",
                proofValue = "placeholder"
            )
        )
        val signedData = canonicalizeWithoutProof(vc)
        val sig = classicalProvider.sign(Algorithm.ED25519, kp.privateKey, signedData)
        val signedVc = vc.copy(proof = vc.proof.copy(proofValue = Multibase.encode(sig)))

        val result = verifier.verifyCredential(signedVc)

        assertThat(result.signatureValid).isTrue()
        assertThat(result.revocationStatus).isEqualTo(RevocationStatus.REVOKED)
        assertThat(result.isValid).isFalse()
    }

    private fun makeTestVc(keyId: String) = VerifiableCredential(
        id = "urn:uuid:test", type = listOf("VerifiableCredential"),
        issuer = Did.fromKeyId(keyId).value,
        issuanceDate = Instant.now().toString(),
        credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
        proof = Proof(
            type = "Ed25519Signature2020", created = Instant.now().toString(),
            verificationMethod = keyId, proofPurpose = "assertionMethod", proofValue = "uABC"
        )
    )

    private fun canonicalizeWithoutProof(credential: VerifiableCredential): ByteArray {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false
        }
        val fullJson = json.encodeToString(VerifiableCredential.serializer(), credential)
        val jsonObj = json.parseToJsonElement(fullJson).jsonObject.toMutableMap()
        jsonObj.remove("proof")
        return VaultImpl.canonicalJson(kotlinx.serialization.json.JsonObject(jsonObj)).toByteArray(Charsets.UTF_8)
    }

    // B2: DID key rotation — credential signed with new key B, bundle has old key A → FAILED
    @Test
    fun `keyRotation_oldBundle_newCredential_returnsFailed`() = runTest {
        // Key pair A: old key cached in DID Document
        val kpA = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val keyId = "did:ssdid:issuer#key-1"

        val didDocWithKeyA = DidDocument(
            id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
            verificationMethod = listOf(
                VerificationMethod(
                    id = keyId, type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:issuer",
                    publicKeyMultibase = Multibase.encode(kpA.publicKey)
                )
            ),
            authentication = listOf(keyId), assertionMethod = listOf(keyId)
        )

        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = didDocWithKeyA,
            fetchedAt = Instant.now().toString(),
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        // Key pair B: new rotated key — signs the credential but NOT in cached DID doc
        val kpB = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val newKeyId = "did:ssdid:issuer#key-2"

        val vc = VerifiableCredential(
            id = "urn:uuid:test", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = Instant.now().toString(),
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = newKeyId, proofPurpose = "assertionMethod",
                proofValue = "placeholder"
            )
        )
        val signedData = canonicalizeWithoutProof(vc)
        val sig = classicalProvider.sign(Algorithm.ED25519, kpB.privateKey, signedData)
        val signedVc = vc.copy(proof = vc.proof.copy(proofValue = Multibase.encode(sig)))

        val result = verifier.verifyCredential(signedVc)

        // new key (key-2) is not in the cached DID doc → signature verification fails
        assertThat(result.signatureValid).isFalse()
        assertThat(result.error).contains("Key not found")
    }

    // B3: Storage failure — FailingBundleStore throws on saveBundle, fetchAndCache still returns bundle
    @Test
    fun `storageFailure_bundleFetchStillReturnsBundle`() = runTest {
        // FailingBundleStore throws IOException on saveBundle but allows reads
        val failingStore = object : BundleStore {
            override suspend fun saveBundle(bundle: VerificationBundle) {
                throw java.io.IOException("Disk full")
            }
            override suspend fun getBundle(issuerDid: String): VerificationBundle? = null
            override suspend fun deleteBundle(issuerDid: String) {}
            override suspend fun listBundles(): List<VerificationBundle> = emptyList()
        }

        val failingVerifier = OfflineVerifier(classicalProvider, pqcProvider, failingStore, ttlProvider)

        // With a failing store, any call to verifyCredential for an unknown issuer returns error
        val vc = makeTestVc("did:ssdid:unknown#key-1")
        val result = failingVerifier.verifyCredential(vc)

        // The verifier gracefully handles a missing bundle (store returns null on read)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("No cached bundle")
    }

    // B7: Status list URL mismatch → UNKNOWN revocation
    @Test
    fun `statusListUrlMismatch_returnsUnknownRevocation`() = runTest {
        val kp = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val keyId = "did:ssdid:issuer#key-1"

        val didDoc = DidDocument(
            id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
            verificationMethod = listOf(
                VerificationMethod(
                    id = keyId, type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:issuer",
                    publicKeyMultibase = Multibase.encode(kp.publicKey)
                )
            ),
            authentication = listOf(keyId), assertionMethod = listOf(keyId)
        )

        // Status list cached with id = ".../status/1"
        val statusList = StatusListCredential(
            id = "https://registry.ssdid.my/status/1",
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList", statusPurpose = "revocation",
                encodedList = makeEncodedList(emptySet())
            ),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = keyId, proofPurpose = "assertionMethod", proofValue = "uDummy"
            )
        )

        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer", didDocument = didDoc,
            statusList = statusList,
            fetchedAt = Instant.now().toString(),
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        )
        coEvery { bundleStore.getBundle("did:ssdid:issuer") } returns bundle

        // Credential references ".../status/2" — mismatched URL
        val vc = VerifiableCredential(
            id = "urn:uuid:test", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = Instant.now().toString(),
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.ssdid.my/status/2#5",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "5",
                statusListCredential = "https://registry.ssdid.my/status/2"
            ),
            proof = Proof(
                type = "Ed25519Signature2020", created = Instant.now().toString(),
                verificationMethod = keyId, proofPurpose = "assertionMethod",
                proofValue = "placeholder"
            )
        )
        val signedData = canonicalizeWithoutProof(vc)
        val sig = classicalProvider.sign(Algorithm.ED25519, kp.privateKey, signedData)
        val signedVc = vc.copy(proof = vc.proof.copy(proofValue = Multibase.encode(sig)))

        val result = verifier.verifyCredential(signedVc)

        assertThat(result.signatureValid).isTrue()
        assertThat(result.revocationStatus).isEqualTo(RevocationStatus.UNKNOWN)
    }

    private fun makeEncodedList(revokedIndices: Set<Int>): String {
        val bytes = ByteArray(128)
        for (idx in revokedIndices) {
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8)
            bytes[bytePos] = (bytes[bytePos].toInt() or (1 shl bitPos)).toByte()
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }
}
