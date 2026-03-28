package my.ssdid.sdk.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import java.util.Base64

class KeyBindingJwtTest {

    private val testSigner: (ByteArray) -> ByteArray = { "test-kb-signature".toByteArray() }

    @Test
    fun `create produces valid KB-JWT structure`() = runBlocking {
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = "eyJhbGciOiJFZDI1NTE5In0.eyJ0ZXN0IjoidHJ1ZSJ9.c2ln~WyJzYWx0IiwiYSIsImIiXQ~",
            audience = "https://verifier.example.com",
            nonce = "abc123",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = 1719792000
        )

        // KB-JWT should have 3 dot-separated parts
        val parts = kbJwt.split(".")
        assertThat(parts).hasSize(3)
    }

    @Test
    fun `KB-JWT header has typ kb+jwt`() = runBlocking {
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = "header.payload.sig~disc~",
            audience = "https://verifier.example.com",
            nonce = "abc123",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = 1719792000
        )

        val headerJson = decodeJwtPart(kbJwt.split(".")[0])
        assertThat(headerJson["typ"]?.jsonPrimitive?.content).isEqualTo("kb+jwt")
        assertThat(headerJson["alg"]?.jsonPrimitive?.content).isEqualTo("EdDSA")
    }

    @Test
    fun `KB-JWT payload contains aud, nonce, iat, sd_hash`() = runBlocking {
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = "header.payload.sig~disc~",
            audience = "https://verifier.example.com",
            nonce = "abc123",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = 1719792000
        )

        val payloadJson = decodeJwtPart(kbJwt.split(".")[1])
        assertThat(payloadJson["aud"]?.jsonPrimitive?.content).isEqualTo("https://verifier.example.com")
        assertThat(payloadJson["nonce"]?.jsonPrimitive?.content).isEqualTo("abc123")
        assertThat(payloadJson["iat"]?.jsonPrimitive?.long).isEqualTo(1719792000)
        assertThat(payloadJson["sd_hash"]?.jsonPrimitive?.content).isNotEmpty()
    }

    @Test
    fun `sd_hash is deterministic for same input`() = runBlocking {
        val input = "header.payload.sig~disc~"
        val kbJwt1 = KeyBindingJwt.create(
            sdJwtWithDisclosures = input,
            audience = "aud",
            nonce = "n",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = 1719792000
        )
        val kbJwt2 = KeyBindingJwt.create(
            sdJwtWithDisclosures = input,
            audience = "aud",
            nonce = "n",
            algorithm = "EdDSA",
            signer = testSigner,
            issuedAt = 1719792000
        )

        val hash1 = decodeJwtPart(kbJwt1.split(".")[1])["sd_hash"]?.jsonPrimitive?.content
        val hash2 = decodeJwtPart(kbJwt2.split(".")[1])["sd_hash"]?.jsonPrimitive?.content
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `KB-JWT can be parsed as last part of SD-JWT`() = runBlocking {
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = "header.payload.sig~WyJzYWx0IiwiYSIsImIiXQ~",
            audience = "https://verifier.example.com",
            nonce = "abc123",
            algorithm = "EdDSA",
            signer = testSigner
        )

        // Construct full SD-JWT with KB-JWT
        val fullSdJwt = "header.payload.sig~WyJzYWx0IiwiYSIsImIiXQ~$kbJwt"
        val parsed = SdJwtParser.parse(fullSdJwt)
        assertThat(parsed.keyBindingJwt).isEqualTo(kbJwt)
    }

    private fun decodeJwtPart(base64url: String): JsonObject {
        val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
        return Json.parseToJsonElement(json).jsonObject
    }
}
