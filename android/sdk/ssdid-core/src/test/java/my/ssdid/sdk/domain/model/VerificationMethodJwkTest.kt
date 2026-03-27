package my.ssdid.sdk.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test

class VerificationMethodJwkTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `VerificationMethod with publicKeyJwk serializes correctly`() {
        val vm = VerificationMethod(
            id = "did:jwk:abc#0",
            type = "JsonWebKey2020",
            controller = "did:jwk:abc",
            publicKeyJwk = buildJsonObject {
                put("kty", "OKP")
                put("crv", "Ed25519")
                put("x", "0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ")
            }
        )
        val encoded = json.encodeToString(vm)
        val decoded = json.decodeFromString<VerificationMethod>(encoded)
        assertThat(decoded.publicKeyJwk).isNotNull()
        assertThat(decoded.publicKeyJwk!!["kty"]?.jsonPrimitive?.content).isEqualTo("OKP")
    }

    @Test
    fun `VerificationMethod without publicKeyJwk defaults to null`() {
        val vm = VerificationMethod(
            id = "did:ssdid:abc#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:ssdid:abc",
            publicKeyMultibase = "z6MkTest"
        )
        assertThat(vm.publicKeyJwk).isNull()
    }

    @Test
    fun `VerificationMethod deserializes JSON without publicKeyJwk`() {
        val jsonStr = """{"id":"did:ssdid:abc#key-1","type":"Ed25519VerificationKey2020","controller":"did:ssdid:abc","publicKeyMultibase":"z6MkTest"}"""
        val vm = json.decodeFromString<VerificationMethod>(jsonStr)
        assertThat(vm.publicKeyJwk).isNull()
        assertThat(vm.publicKeyMultibase).isEqualTo("z6MkTest")
    }
}
