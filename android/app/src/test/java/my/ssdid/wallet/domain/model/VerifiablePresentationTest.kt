package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class VerifiablePresentationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `VP has correct default context`() {
        val vp = VerifiablePresentation(holder = "did:ssdid:holder123")
        assertThat(vp.context).contains("https://www.w3.org/ns/credentials/v2")
        assertThat(vp.type).contains("VerifiablePresentation")
    }

    @Test
    fun `VP serializes and deserializes correctly`() {
        val vp = VerifiablePresentation(
            holder = "did:ssdid:holder123",
            verifiableCredential = emptyList()
        )
        val encoded = json.encodeToString(vp)
        val decoded = json.decodeFromString<VerifiablePresentation>(encoded)
        assertThat(decoded.holder).isEqualTo("did:ssdid:holder123")
        assertThat(decoded.type).containsExactly("VerifiablePresentation")
    }

    @Test
    fun `VP with empty credentials is valid`() {
        val vp = VerifiablePresentation(holder = "did:key:z6MkTest")
        assertThat(vp.verifiableCredential).isEmpty()
        assertThat(vp.proof).isNull()
    }
}
