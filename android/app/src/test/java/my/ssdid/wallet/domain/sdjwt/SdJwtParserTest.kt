package my.ssdid.wallet.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.*
import org.junit.Test

class SdJwtParserTest {

    @Test
    fun `parse splits compact SD-JWT into parts`() {
        // Build a realistic compact SD-JWT:
        // issuer JWT (header.payload.signature) ~ disclosure ~ trailing tilde
        val compact = "eyJhbGciOiJFZDI1NTE5In0.eyJfc2QiOlsiaGFzaDEiXSwic3ViIjoiZGlkOnNzZGlkOmFiYyJ9.c2ln~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~"
        val parsed = SdJwtParser.parse(compact)

        assertThat(parsed.issuerJwt).isNotEmpty()
        assertThat(parsed.disclosures).hasSize(1)
        assertThat(parsed.keyBindingJwt).isNull()
    }

    @Test
    fun `parse extracts disclosure claim name and value`() {
        val disclosureStr = "WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd"
        val disclosure = Disclosure.decode(disclosureStr)

        assertThat(disclosure.salt).isEqualTo("salt1")
        assertThat(disclosure.claimName).isEqualTo("name")
        assertThat(disclosure.claimValue).isEqualTo(JsonPrimitive("Ahmad"))
    }

    @Test
    fun `parse with key binding JWT extracts KB-JWT`() {
        // The last non-empty part has 2 dots = KB-JWT
        val compact = "eyJhbGciOiJFUzI1NiJ9.eyJfc2QiOltdfQ.c2ln~WyJzYWx0IiwiYSIsImIiXQ~eyJ0eXAiOiJrYitqd3QifQ.eyJhdWQiOiJ0ZXN0In0.c2ln~"
        val parsed = SdJwtParser.parse(compact)

        assertThat(parsed.keyBindingJwt).isNotNull()
        assertThat(parsed.disclosures).hasSize(1)
    }

    @Test
    fun `disclosure hash is deterministic`() {
        val disclosure = Disclosure("salt1", "name", JsonPrimitive("Ahmad"))
        val hash1 = disclosure.hash("sha-256")
        val hash2 = disclosure.hash("sha-256")
        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).isNotEmpty()
    }

    @Test
    fun `present reconstructs compact form with selected disclosures`() {
        val compact = "eyJhbGciOiJFZDI1NTE5In0.eyJfc2QiOlsiaGFzaDEiXX0.c2ln~WyJzMSIsIm5hbWUiLCJBaG1hZCJd~WyJzMiIsImRlcHQiLCJFbmciXQ~"
        val parsed = SdJwtParser.parse(compact)

        // Present with only the first disclosure
        val presentation = parsed.present(listOf(parsed.disclosures[0]))
        assertThat(presentation).startsWith(parsed.issuerJwt)
        assertThat(presentation.split("~").filter { it.isNotEmpty() }).hasSize(2) // issuer + 1 disclosure
    }

    @Test
    fun `disclosure encode round-trips`() {
        val original = Disclosure("salt1", "name", JsonPrimitive("Ahmad"))
        val encoded = original.encode()
        val decoded = Disclosure.decode(encoded)
        assertThat(decoded.salt).isEqualTo("salt1")
        assertThat(decoded.claimName).isEqualTo("name")
        assertThat(decoded.claimValue).isEqualTo(JsonPrimitive("Ahmad"))
    }

    @Test
    fun `disclosure hash rejects unsupported algorithm`() {
        val disclosure = Disclosure("salt1", "name", JsonPrimitive("Ahmad"))
        try {
            disclosure.hash("sha-384")
            assertThat(false).isTrue() // should not reach here
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Unsupported hash algorithm")
        }
    }

    // --- Empty string input ---
    @Test
    fun `parse empty string returns SdJwtVc with empty issuerJwt`() {
        val parsed = SdJwtParser.parse("")
        assertThat(parsed.issuerJwt).isEmpty()
        assertThat(parsed.disclosures).isEmpty()
        assertThat(parsed.keyBindingJwt).isNull()
    }

    // --- Non-primitive claim value (JsonObject) round-trip ---
    @Test
    fun `disclosure with JsonObject value round-trips`() {
        val objValue = buildJsonObject {
            put("street", "123 Main St")
            put("city", "KL")
        }
        val original = Disclosure("salt-obj", "address", objValue)
        val encoded = original.encode()
        val decoded = Disclosure.decode(encoded)

        assertThat(decoded.salt).isEqualTo("salt-obj")
        assertThat(decoded.claimName).isEqualTo("address")
        assertThat(decoded.claimValue).isEqualTo(objValue)
    }

    // --- Disclosure with JsonArray value round-trip ---
    @Test
    fun `disclosure with JsonArray value round-trips`() {
        val arrValue = buildJsonArray {
            add("reading")
            add("coding")
            add(42)
        }
        val original = Disclosure("salt-arr", "hobbies", arrValue)
        val encoded = original.encode()
        val decoded = Disclosure.decode(encoded)

        assertThat(decoded.salt).isEqualTo("salt-arr")
        assertThat(decoded.claimName).isEqualTo("hobbies")
        assertThat(decoded.claimValue).isEqualTo(arrValue)
    }
}
