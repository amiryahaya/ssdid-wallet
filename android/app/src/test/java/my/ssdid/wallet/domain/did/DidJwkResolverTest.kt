package my.ssdid.wallet.domain.did

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Base64

class DidJwkResolverTest {
    private val resolver = DidJwkResolver()

    @Test
    fun `resolve Ed25519 did-jwk returns JsonWebKey2020 type`() = runTest {
        val jwk = """{"kty":"OKP","crv":"Ed25519","x":"0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ"}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(jwk.toByteArray())
        val did = "did:jwk:$encoded"
        val result = resolver.resolve(did)
        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.id).isEqualTo(did)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("JsonWebKey2020")
    }

    @Test
    fun `resolve non-did-jwk returns failure`() = runTest {
        val result = resolver.resolve("did:ssdid:abc")
        assertThat(result.isFailure).isTrue()
    }
}
