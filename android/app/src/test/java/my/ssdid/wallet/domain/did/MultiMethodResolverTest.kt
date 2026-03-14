package my.ssdid.wallet.domain.did

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod
import my.ssdid.wallet.domain.transport.RegistryApi
import org.junit.Test

class MultiMethodResolverTest {

    private val registryApi = mockk<RegistryApi>()
    private val ssdidResolver = SsdidRegistryResolver(registryApi)
    private val resolver = MultiMethodResolver(ssdidResolver, DidKeyResolver(), DidJwkResolver())

    @Test
    fun `routes did-ssdid to registry resolver`() = runTest {
        val doc = DidDocument(
            id = "did:ssdid:abc",
            verificationMethod = listOf(
                VerificationMethod("did:ssdid:abc#key-1", "Ed25519VerificationKey2020", "did:ssdid:abc", "zAbc")
            )
        )
        coEvery { registryApi.resolveDid("did:ssdid:abc") } returns doc
        val result = resolver.resolve("did:ssdid:abc")
        assertThat(result.getOrThrow().id).isEqualTo("did:ssdid:abc")
    }

    @Test
    fun `routes did-key to local resolver`() = runTest {
        val result = resolver.resolve("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
    }

    @Test
    fun `routes did-jwk to local resolver`() = runTest {
        val jwk = """{"kty":"OKP","crv":"Ed25519","x":"test"}"""
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(jwk.toByteArray())
        val result = resolver.resolve("did:jwk:$encoded")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().verificationMethod[0].type).isEqualTo("JsonWebKey2020")
    }

    @Test
    fun `unsupported method returns failure`() = runTest {
        val result = resolver.resolve("did:web:example.com")
        assertThat(result.isFailure).isTrue()
    }
}
