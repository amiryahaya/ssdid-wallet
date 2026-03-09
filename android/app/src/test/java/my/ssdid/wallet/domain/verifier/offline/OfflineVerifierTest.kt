package my.ssdid.wallet.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.revocation.RevocationStatus
import my.ssdid.wallet.domain.revocation.StatusListCredential
import my.ssdid.wallet.domain.revocation.StatusListSubject
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.zip.GZIPOutputStream

class OfflineVerifierTest {

    private lateinit var bundleStore: BundleStore
    private lateinit var verifier: OfflineVerifier
    private val classicalProvider = ClassicalProvider()
    private val pqcProvider: CryptoProvider = mockk() // not used in these tests

    @Before
    fun setup() {
        bundleStore = mockk()
        verifier = OfflineVerifier(classicalProvider, pqcProvider, bundleStore)
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
        val bundle = VerificationBundle(
            issuerDid = "did:ssdid:issuer",
            didDocument = DidDocument(
                id = "did:ssdid:issuer", controller = "did:ssdid:issuer",
                verificationMethod = emptyList(),
                authentication = emptyList(), assertionMethod = emptyList()
            ),
            fetchedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString(),
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

        val statusList = StatusListCredential(
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList", statusPurpose = "revocation",
                encodedList = makeEncodedList(setOf(42))
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
                id = "https://registry.example/status#42",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "42",
                statusListCredential = "https://registry.example/status"
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
        val sorted = kotlinx.serialization.json.JsonObject(jsonObj.toSortedMap())
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), sorted).toByteArray(Charsets.UTF_8)
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
