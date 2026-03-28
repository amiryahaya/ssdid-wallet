package my.ssdid.sdk.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.did.DidResolver
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerificationMethod
import org.junit.Test
import java.util.Base64

class SdJwtVerifierTest {

    private val didResolver = mockk<DidResolver>()

    private val alwaysTrueProvider = mockk<CryptoProvider> {
        every { verify(any(), any(), any(), any()) } returns true
    }
    private val alwaysFalseProvider = mockk<CryptoProvider> {
        every { verify(any(), any(), any(), any()) } returns false
    }

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysFalseProvider, alwaysFalseProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
            signer = testSigner,
            issuedAt = System.currentTimeMillis() / 1000 // use current time for freshness check
        )

        val withKbJwt = SdJwtVc(sdJwt.issuerJwt, sdJwt.disclosures, kbJwt)

        // KB-JWT present but no cnf claim — should produce cnf missing error + iat freshness may pass
        val result = verifier.verify(
            withKbJwt,
            expectedAudience = "https://verifier.example.com",
            expectedNonce = "abc123"
        )
        // cnf/jwk is missing so this should NOT be verified
        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("cnf") }).isTrue()

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

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

    // --- TEST-C1: Disclosure hash mismatch ---
    @Test
    fun `verify with tampered disclosure returns hash mismatch error`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        // Create a tampered disclosure with different salt and value
        val tamperedDisclosure = Disclosure("different-salt", "name", JsonPrimitive("Tampered"))
        val tamperedSdJwt = SdJwtVc(sdJwt.issuerJwt, listOf(tamperedDisclosure), null)

        val result = verifier.verify(tamperedSdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("hash mismatch") }).isTrue()
        assertThat(result.disclosedClaims).doesNotContainKey("name")
    }

    // --- Malformed JWT structure ---
    @Test
    fun `verify with malformed JWT structure returns error`() = runTest {
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

        val sdJwt = SdJwtVc(issuerJwt = "not-a-valid-jwt", disclosures = emptyList(), keyBindingJwt = null)
        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors).contains("Invalid JWT structure")
    }

    // --- Missing iss in payload ---
    @Test
    fun `verify with missing iss in payload returns error`() = runTest {
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

        // Build a valid 3-part JWT with no iss field
        val header = buildJsonObject { put("alg", "EdDSA"); put("typ", "vc+sd-jwt") }
        val payload = buildJsonObject { put("sub", "did:key:z6MkHolder"); put("_sd_alg", "sha-256") }
        val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().toByteArray())
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray())
        val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("sig".toByteArray())
        val jwt = "$headerB64.$payloadB64.$sigB64"

        val sdJwt = SdJwtVc(issuerJwt = jwt, disclosures = emptyList(), keyBindingJwt = null)
        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors).contains("Missing iss in payload")
    }

    // --- nbf not yet valid ---
    @Test
    fun `verify with future nbf returns not yet valid error`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

        // Build JWT manually with a far-future nbf
        val header = buildJsonObject { put("alg", "EdDSA"); put("typ", "vc+sd-jwt") }
        val payload = buildJsonObject {
            put("iss", "did:key:z6MkIssuer")
            put("sub", "did:key:z6MkHolder")
            put("vct", "VerifiableCredential")
            put("iat", 1719792000)
            put("nbf", 9999999999L) // far future
            put("_sd_alg", "sha-256")
            putJsonArray("_sd") {}
        }
        val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().toByteArray())
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray())
        val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("sig".toByteArray())
        val jwt = "$headerB64.$payloadB64.$sigB64"

        val sdJwt = SdJwtVc(issuerJwt = jwt, disclosures = emptyList(), keyBindingJwt = null)
        val result = verifier.verify(sdJwt)

        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("not yet valid") }).isTrue()
    }

    // --- Wrong nonce KB-JWT ---
    @Test
    fun `verify with wrong nonce in KB-JWT returns error`() = runTest {
        mockIssuerDid()
        val verifier = SdJwtVerifier(didResolver, alwaysTrueProvider, alwaysTrueProvider)

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val sdJwtString = sdJwt.present(sdJwt.disclosures)
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtString,
            audience = "https://verifier.example.com",
            nonce = "correct-nonce",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = System.currentTimeMillis() / 1000
        )

        val withKbJwt = SdJwtVc(sdJwt.issuerJwt, sdJwt.disclosures, kbJwt)

        val result = verifier.verify(
            withKbJwt,
            expectedAudience = "https://verifier.example.com",
            expectedNonce = "wrong-nonce"
        )

        assertThat(result.verified).isFalse()
        assertThat(result.errors.any { it.contains("nonce mismatch") }).isTrue()
    }

    // --- KB-JWT with cnf and signature verification ---
    @Test
    fun `verify KB-JWT with cnf claim and signature verification`() = runTest {
        mockIssuerDid()

        // Track which algorithm the provider receives for KB-JWT verification
        val capturedAlgorithms = mutableListOf<Algorithm>()
        val trackingProvider = mockk<CryptoProvider> {
            every { verify(any(), any(), any(), any()) } answers {
                capturedAlgorithms.add(firstArg())
                true
            }
        }
        val verifier = SdJwtVerifier(didResolver, trackingProvider, trackingProvider)

        val holderJwk = buildJsonObject {
            put("kty", "OKP")
            put("crv", "Ed25519")
            put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 0x42 }))
        }

        val sdJwt = issuerInstance.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            holderKeyJwk = holderJwk,
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val sdJwtString = sdJwt.present(sdJwt.disclosures)
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtString,
            audience = "https://verifier.example.com",
            nonce = "test-nonce",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = System.currentTimeMillis() / 1000
        )

        val withKbJwt = SdJwtVc(sdJwt.issuerJwt, sdJwt.disclosures, kbJwt)

        val result = verifier.verify(
            withKbJwt,
            expectedAudience = "https://verifier.example.com",
            expectedNonce = "test-nonce"
        )

        assertThat(result.verified).isTrue()
        // Should have been called twice: once for issuer JWT, once for KB-JWT
        assertThat(capturedAlgorithms).hasSize(2)
        // Both should be EdDSA (ED25519)
        assertThat(capturedAlgorithms[0]).isEqualTo(Algorithm.ED25519)
        assertThat(capturedAlgorithms[1]).isEqualTo(Algorithm.ED25519)
    }
}
