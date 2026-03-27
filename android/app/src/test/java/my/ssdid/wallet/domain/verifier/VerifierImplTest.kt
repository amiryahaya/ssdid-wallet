package my.ssdid.wallet.domain.verifier

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.did.DidResolver
import my.ssdid.sdk.domain.model.*
import org.junit.Before
import org.junit.Test

class VerifierImplTest {

    private lateinit var verifier: VerifierImpl
    private lateinit var didResolver: DidResolver
    private lateinit var classicalProvider: CryptoProvider
    private lateinit var pqcProvider: CryptoProvider

    private val serverDid = "did:ssdid:dGVzdHNlcnZlcjEyMzQ1Ng"
    private val serverKeyId = "did:ssdid:dGVzdHNlcnZlcjEyMzQ1Ng#key-1"
    private val publicKeyBytes = "public-key-bytes".toByteArray()
    private val publicKeyMultibase = Multibase.encode(publicKeyBytes)

    private val serverDidDoc = DidDocument(
        id = serverDid,
        controller = serverDid,
        verificationMethod = listOf(
            VerificationMethod(
                id = serverKeyId,
                type = "Ed25519VerificationKey2020",
                controller = serverDid,
                publicKeyMultibase = publicKeyMultibase
            )
        ),
        authentication = listOf(serverKeyId),
        assertionMethod = listOf(serverKeyId)
    )

    @Before
    fun setup() {
        didResolver = mockk()
        classicalProvider = mockk()
        pqcProvider = mockk()
        verifier = VerifierImpl(didResolver, classicalProvider, pqcProvider)
    }

    // --- resolveDid ---

    @Test
    fun `resolveDid returns document from registry`() = runTest {
        coEvery { didResolver.resolve(serverDid) } returns Result.success(serverDidDoc)

        val result = verifier.resolveDid(serverDid)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().id).isEqualTo(serverDid)
    }

    @Test
    fun `resolveDid fails when registry throws`() = runTest {
        coEvery { didResolver.resolve(any()) } returns Result.failure(RuntimeException("not found"))

        val result = verifier.resolveDid(serverDid)

        assertThat(result.isFailure).isTrue()
    }

    // --- verifySignature ---

    @Test
    fun `verifySignature resolves DID and verifies with classical provider`() = runTest {
        coEvery { didResolver.resolve(serverDid) } returns Result.success(serverDidDoc)
        every { classicalProvider.verify(Algorithm.ED25519, publicKeyBytes, any(), any()) } returns true

        val signature = "test-signature".toByteArray()
        val data = "test-data".toByteArray()
        val result = verifier.verifySignature(serverDid, serverKeyId, signature, data)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isTrue()
        verify { classicalProvider.verify(Algorithm.ED25519, publicKeyBytes, signature, data) }
    }

    @Test
    fun `verifySignature uses pqcProvider for post-quantum algorithm`() = runTest {
        val pqcKeyBytes = "pqc-key".toByteArray()
        val pqcDoc = DidDocument(
            id = serverDid,
            controller = serverDid,
            verificationMethod = listOf(
                VerificationMethod(
                    id = serverKeyId,
                    type = "KazSignVerificationKey2024",
                    controller = serverDid,
                    publicKeyMultibase = Multibase.encode(pqcKeyBytes)
                )
            ),
            authentication = listOf(serverKeyId),
            assertionMethod = listOf(serverKeyId)
        )
        coEvery { didResolver.resolve(serverDid) } returns Result.success(pqcDoc)
        every { pqcProvider.verify(Algorithm.KAZ_SIGN_128, pqcKeyBytes, any(), any()) } returns true

        val result = verifier.verifySignature(serverDid, serverKeyId, "sig".toByteArray(), "data".toByteArray())

        assertThat(result.isSuccess).isTrue()
        verify { pqcProvider.verify(eq(Algorithm.KAZ_SIGN_128), eq(pqcKeyBytes), any(), any()) }
    }

    @Test
    fun `verifySignature fails when keyId not found in document`() = runTest {
        coEvery { didResolver.resolve(serverDid) } returns Result.success(serverDidDoc)

        val result = verifier.verifySignature(serverDid, "did:ssdid:dGVzdHNlcnZlcjEyMzQ1Ng#key-999", "sig".toByteArray(), "data".toByteArray())

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("not found in DID Document")
    }

    @Test
    fun `verifySignature fails for unknown W3C type`() = runTest {
        val badDoc = DidDocument(
            id = serverDid,
            controller = serverDid,
            verificationMethod = listOf(
                VerificationMethod(
                    id = serverKeyId,
                    type = "UnknownType2099",
                    controller = serverDid,
                    publicKeyMultibase = publicKeyMultibase
                )
            ),
            authentication = listOf(serverKeyId),
            assertionMethod = listOf(serverKeyId)
        )
        coEvery { didResolver.resolve(serverDid) } returns Result.success(badDoc)

        val result = verifier.verifySignature(serverDid, serverKeyId, "sig".toByteArray(), "data".toByteArray())

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Unknown W3C verification method type")
    }

    // --- verifyChallengeResponse ---

    @Test
    fun `verifyChallengeResponse decodes multibase and delegates to verifySignature`() = runTest {
        coEvery { didResolver.resolve(serverDid) } returns Result.success(serverDidDoc)
        val signatureBytes = "raw-signature".toByteArray()
        val signedChallenge = Multibase.encode(signatureBytes)
        every { classicalProvider.verify(Algorithm.ED25519, publicKeyBytes, signatureBytes, any()) } returns true

        val result = verifier.verifyChallengeResponse(serverDid, serverKeyId, "challenge-text", signedChallenge)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isTrue()
        verify { classicalProvider.verify(Algorithm.ED25519, publicKeyBytes, signatureBytes, "challenge-text".toByteArray()) }
    }

    // --- verifyCredential ---

    @Test
    fun `verifyCredential verifies issuer signature on credential`() = runTest {
        val issuerDid = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng"
        val issuerKeyId = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng#key-1"
        val issuerPublicKey = "issuer-pub".toByteArray()
        val issuerDoc = DidDocument(
            id = issuerDid,
            controller = issuerDid,
            verificationMethod = listOf(
                VerificationMethod(
                    id = issuerKeyId,
                    type = "Ed25519VerificationKey2020",
                    controller = issuerDid,
                    publicKeyMultibase = Multibase.encode(issuerPublicKey)
                )
            ),
            authentication = listOf(issuerKeyId),
            assertionMethod = listOf(issuerKeyId)
        )
        coEvery { didResolver.resolve(issuerDid) } returns Result.success(issuerDoc)

        val vc = VerifiableCredential(
            id = "urn:uuid:vc-1",
            type = listOf("VerifiableCredential"),
            issuer = issuerDid,
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = issuerKeyId,
                proofPurpose = "assertionMethod",
                proofValue = Multibase.encode("proof-sig".toByteArray())
            )
        )
        every { classicalProvider.verify(Algorithm.ED25519, issuerPublicKey, any(), any()) } returns true

        val result = verifier.verifyCredential(vc)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isTrue()
        verify { classicalProvider.verify(eq(Algorithm.ED25519), eq(issuerPublicKey), eq("proof-sig".toByteArray()), any()) }
    }

    @Test
    fun `verifyCredential fails when credential is expired`() = runTest {
        val vc = VerifiableCredential(
            id = "urn:uuid:expired",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng",
            issuanceDate = "2020-01-01T00:00:00Z",
            expirationDate = "2020-12-31T23:59:59Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2020-01-01T00:00:00Z",
                verificationMethod = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uABC"
            )
        )

        val result = verifier.verifyCredential(vc)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("expired")
    }

    @Test
    fun `verifyCredential fails when issuer key not found`() = runTest {
        val issuerDid = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng"
        val issuerDoc = DidDocument(
            id = issuerDid,
            controller = issuerDid,
            verificationMethod = emptyList(),
            authentication = emptyList(),
            assertionMethod = emptyList()
        )
        coEvery { didResolver.resolve(issuerDid) } returns Result.success(issuerDoc)

        val vc = VerifiableCredential(
            id = "urn:uuid:test",
            type = listOf("VerifiableCredential"),
            issuer = issuerDid,
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uABC"
            )
        )

        val result = verifier.verifyCredential(vc)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("not found in issuer DID Document")
    }

    @Test
    fun `verifyCredential succeeds when no expiration date`() = runTest {
        val issuerDid = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng"
        val issuerKeyId = "did:ssdid:dGVzdGlzc3VlcjEyMzQ1Ng#key-1"
        val issuerPublicKey = "pub".toByteArray()
        val issuerDoc = DidDocument(
            id = issuerDid,
            controller = issuerDid,
            verificationMethod = listOf(
                VerificationMethod(
                    id = issuerKeyId,
                    type = "Ed25519VerificationKey2020",
                    controller = issuerDid,
                    publicKeyMultibase = Multibase.encode(issuerPublicKey)
                )
            ),
            authentication = listOf(issuerKeyId),
            assertionMethod = listOf(issuerKeyId)
        )
        coEvery { didResolver.resolve(issuerDid) } returns Result.success(issuerDoc)
        every { classicalProvider.verify(any(), any(), any(), any()) } returns true

        val vc = VerifiableCredential(
            id = "urn:uuid:no-exp",
            type = listOf("VerifiableCredential"),
            issuer = issuerDid,
            issuanceDate = "2026-03-06T00:00:00Z",
            expirationDate = null,
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = issuerKeyId,
                proofPurpose = "assertionMethod",
                proofValue = Multibase.encode("sig".toByteArray())
            )
        )

        val result = verifier.verifyCredential(vc)

        assertThat(result.isSuccess).isTrue()
    }
}
