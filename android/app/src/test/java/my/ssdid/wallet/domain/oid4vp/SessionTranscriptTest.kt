package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test

class SessionTranscriptTest {
    // --- G11: empty/edge-case strings ---

    @Test
    fun buildHandlesEmptyStrings() {
        val bytes = SessionTranscript.build("", "", "")
        val cbor = CBORObject.DecodeFromBytes(bytes)
        assertThat(cbor.size()).isEqualTo(3)
        assertThat(cbor[0].isNull).isTrue()
        assertThat(cbor[1].isNull).isTrue()
        val handover = cbor[2]
        assertThat(handover[0].AsString()).isEmpty()
        assertThat(handover[1].AsString()).isEmpty()
        assertThat(handover[2].AsString()).isEmpty()
    }

    @Test
    fun buildHandlesSpecialCharacters() {
        val bytes = SessionTranscript.build("client with spaces", "https://example.com/path?q=1&a=2", "nonce/special=chars")
        val cbor = CBORObject.DecodeFromBytes(bytes)
        val handover = cbor[2]
        assertThat(handover[0].AsString()).isEqualTo("client with spaces")
        assertThat(handover[1].AsString()).isEqualTo("https://example.com/path?q=1&a=2")
        assertThat(handover[2].AsString()).isEqualTo("nonce/special=chars")
    }

    @Test
    fun buildCreatesValidCborArray() {
        val bytes = SessionTranscript.build("client-id", "https://verifier.example/response", "nonce123")
        val cbor = CBORObject.DecodeFromBytes(bytes)
        assertThat(cbor.size()).isEqualTo(3)
        assertThat(cbor[0].isNull).isTrue()
        assertThat(cbor[1].isNull).isTrue()
        val handover = cbor[2]
        assertThat(handover[0].AsString()).isEqualTo("client-id")
        assertThat(handover[1].AsString()).isEqualTo("https://verifier.example/response")
        assertThat(handover[2].AsString()).isEqualTo("nonce123")
    }
}
