package my.ssdid.sdk.domain.did

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DidKeyResolverTest {

    private val resolver = DidKeyResolver()

    @Test
    fun `resolve Ed25519 did-key returns valid DID Document`() = runTest {
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        val result = resolver.resolve(did)

        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.id).isEqualTo(did)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
        assertThat(doc.verificationMethod[0].controller).isEqualTo(did)
        assertThat(doc.authentication).contains(doc.verificationMethod[0].id)
        assertThat(doc.assertionMethod).contains(doc.verificationMethod[0].id)
    }

    @Test
    fun `resolve P-256 did-key returns EcdsaSecp256r1 type`() = runTest {
        val did = "did:key:zDnaeWgbpcUat3VPa1GqrFbcr7jVBNMhBMRKTsgBHYBcJkRYH"
        val result = resolver.resolve(did)

        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.verificationMethod[0].type).isEqualTo("EcdsaSecp256r1VerificationKey2019")
    }

    @Test
    fun `resolve non-did-key returns failure`() = runTest {
        val result = resolver.resolve("did:ssdid:abc123")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `resolve invalid multibase returns failure`() = runTest {
        val result = resolver.resolve("did:key:invaliddata")
        assertThat(result.isFailure).isTrue()
    }
}
