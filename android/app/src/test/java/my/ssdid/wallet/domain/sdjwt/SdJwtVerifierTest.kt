package my.ssdid.wallet.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.did.DidResolver
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod
import org.junit.Test

class SdJwtVerifierTest {

    private val didResolver = mockk<DidResolver>()
    private val alwaysTrue: (Algorithm, ByteArray, ByteArray, ByteArray) -> Boolean = { _, _, _, _ -> true }
    private val alwaysFalse: (Algorithm, ByteArray, ByteArray, ByteArray) -> Boolean = { _, _, _, _ -> false }

    private val testSigner: (ByteArray) -> ByteArray = { "test-signature".toByteArray() }
    private val issuerInstance = SdJwtIssuer(signer = testSigner, algorithm = "EdDSA")

    private fun mockIssuerDid(did: String = "did:key:z6MkIssuer") {
        val doc = DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(
                    id = "$did#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyMultibase = "uVGVzdFB1YmxpY0tleQ"
                )
            )
        )
        coEvery { didResolver.resolve(did) } returns Result.success(doc)
    }

    @Test
    fun `verify valid SD-JWT returns verified with disclosed claims`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = mapOf("name" to JsonPrimitive("Ahmad"), "employeeId" to JsonPrimitive("EMP-1234")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800 // year 2100
        )

        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isTrue()
        assertThat(result.issuer).isEqualTo("did:key:z6MkIssuer")
        assertThat(result.disclosedClaims).containsEntry("name", JsonPrimitive("Ahmad"))
        assertThat(result.disclosedClaims).containsEntry("employeeId", JsonPrimitive("EMP-1234"))
    }

    @Test
    fun `verify with failed signature returns not verified`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysFalse, alwaysFalse)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun `verify expired SD-JWT returns error`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 1 // expired in 1970
        )

        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("expired") }).isTrue()
    }

    @Test
    fun `verify with KB-JWT checks audience and nonce`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        // Create a presentation with KB-JWT
        val sdJwtString = sdJwt.present(sdJwt.disclosures)
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtString,
            audience = "https://verifier.example.com",
            nonce = "abc123",
            algorithm = "EdDSA",
            signer = testSigner
        )

        val withKbJwt = SdJwtVc(sdJwt.issuerJwt, sdJwt.disclosures, kbJwt)

        // Matching audience and nonce (no cnf claim, so KB-JWT sig not verified — only aud/nonce checked)
        val result = verifier.verify(
            withKbJwt,
            expectedAudience = "https://verifier.example.com",
            expectedNonce = "abc123"
        )
        assertThat(result.verified).isTrue()

        // Wrong audience
        val result2 = verifier.verify(
            withKbJwt,
            expectedAudience = "https://wrong.com",
            expectedNonce = "abc123"
        )
        assertThat(result2.verified).isFalse()
    }

    @Test
    fun `verify with unresolvable DID returns failure`() = runTest {
        coEvery { didResolver.resolve(any()) } returns Result.failure(Exception("DID not found"))
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkUnknown",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name")
        )

        val result = verifier.verify(sdJwt)
        assertThat(result.verified).isFalse()
    }

    @Test
    fun `verify subject is extracted from payload`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val result = verifier.verify(sdJwt)
        assertThat(result.subject).isEqualTo("did:key:z6MkHolder")
    }

    @Test
    fun `verify with no verification methods returns error`() = runTest {
        val did = "did:key:z6MkEmpty"
        val doc = DidDocument(
            id = did,
            verificationMethod = emptyList()
        )
        coEvery { didResolver.resolve(did) } returns Result.success(doc)
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        val sdJwt = issuerInstance.issue(
            issuer = did,
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val result = verifier.verify(sdJwt)
        assertThat(result.verified).isFalse()
        assertThat(result.errors).contains("No verification methods in issuer DID document")
    }

    @Test
    fun `verify with unsupported algorithm returns error`() = runTest {
        val did = "did:key:z6MkBadAlg"
        val doc = DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(
                    id = "$did#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyMultibase = "uVGVzdFB1YmxpY0tleQ"
                )
            )
        )
        coEvery { didResolver.resolve(did) } returns Result.success(doc)
        val verifier = SdJwtVerifier(didResolver, alwaysTrue, alwaysTrue)

        // Issue with a non-standard algorithm that won't map
        val badAlgIssuer = SdJwtIssuer(signer = testSigner, algorithm = "UNSUPPORTED")
        val sdJwt = badAlgIssuer.issue(
            issuer = did,
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val result = verifier.verify(sdJwt)
        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("Unsupported JWT algorithm") }).isTrue()
    }
}
