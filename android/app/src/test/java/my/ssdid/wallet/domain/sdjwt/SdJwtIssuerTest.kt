package my.ssdid.wallet.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.*
import org.junit.Test
import java.util.Base64

class SdJwtIssuerTest {

    // Simple test signer that returns a fixed signature
    private val testSigner: (ByteArray) -> ByteArray = { "test-signature".toByteArray() }

    private val issuer = SdJwtIssuer(signer = testSigner, algorithm = "EdDSA")

    @Test
    fun `issue creates SD-JWT with correct structure`() {
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = mapOf("name" to JsonPrimitive("Ahmad"), "employeeId" to JsonPrimitive("EMP-1234"), "department" to JsonPrimitive("Engineering")),
            disclosable = setOf("name", "department"),
            issuedAt = 1719792000,
            expiresAt = 1751328000
        )

        assertThat(sdJwt.issuerJwt).isNotEmpty()
        assertThat(sdJwt.disclosures).hasSize(2)
        assertThat(sdJwt.keyBindingJwt).isNull()
    }

    @Test
    fun `issued JWT header has correct typ and alg`() {
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name")
        )

        val headerJson = decodeJwtPart(sdJwt.issuerJwt.split(".")[0])
        assertThat(headerJson["alg"]?.jsonPrimitive?.content).isEqualTo("EdDSA")
        assertThat(headerJson["typ"]?.jsonPrimitive?.content).isEqualTo("vc+sd-jwt")
    }

    @Test
    fun `issued JWT payload contains _sd array with disclosure hashes`() {
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = mapOf("name" to JsonPrimitive("Ahmad"), "employeeId" to JsonPrimitive("EMP-1234")),
            disclosable = setOf("name")
        )

        val payloadJson = decodeJwtPart(sdJwt.issuerJwt.split(".")[1])
        val sdArray = payloadJson["_sd"]?.jsonArray
        assertThat(sdArray).isNotNull()
        assertThat(sdArray).hasSize(1) // Only "name" is disclosable
        // employeeId should be visible in the payload
        assertThat(payloadJson["employeeId"]?.jsonPrimitive?.content).isEqualTo("EMP-1234")
        // "name" should NOT be in the payload (it's in a disclosure)
        assertThat(payloadJson.containsKey("name")).isFalse()
    }

    @Test
    fun `issued JWT payload contains cnf when holderKeyJwk provided`() {
        val holderJwk = buildJsonObject {
            put("kty", "OKP")
            put("crv", "Ed25519")
            put("x", "testkey")
        }
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name"),
            holderKeyJwk = holderJwk
        )

        val payloadJson = decodeJwtPart(sdJwt.issuerJwt.split(".")[1])
        val cnf = payloadJson["cnf"]?.jsonObject
        assertThat(cnf).isNotNull()
        assertThat(cnf!!["jwk"]?.jsonObject?.get("kty")?.jsonPrimitive?.content).isEqualTo("OKP")
    }

    @Test
    fun `disclosures decode to correct claim names`() {
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad"), "dept" to JsonPrimitive("Eng")),
            disclosable = setOf("name", "dept")
        )

        val claimNames = sdJwt.disclosures.map { it.claimName }.toSet()
        assertThat(claimNames).containsExactly("name", "dept")
    }

    @Test
    fun `compact form can be parsed back`() {
        val sdJwt = issuer.issue(
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = listOf("VerifiableCredential"),
            claims = mapOf("name" to JsonPrimitive("Ahmad")),
            disclosable = setOf("name")
        )

        val compact = sdJwt.present(sdJwt.disclosures)
        val parsed = SdJwtParser.parse(compact)
        assertThat(parsed.issuerJwt).isEqualTo(sdJwt.issuerJwt)
        assertThat(parsed.disclosures).hasSize(1)
        assertThat(parsed.disclosures[0].claimName).isEqualTo("name")
    }

    private fun decodeJwtPart(base64url: String): JsonObject {
        val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
        return Json.parseToJsonElement(json).jsonObject
    }
}
