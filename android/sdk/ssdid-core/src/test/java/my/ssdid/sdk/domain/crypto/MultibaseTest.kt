package my.ssdid.sdk.domain.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MultibaseTest {
    @Test
    fun `encode uses u prefix for base64url`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = Multibase.encode(data)
        assertThat(encoded).startsWith("u")
    }

    @Test
    fun `round trip encode-decode`() {
        val data = "Hello SSDID".toByteArray()
        val encoded = Multibase.encode(data)
        val decoded = Multibase.decode(encoded)
        assertThat(decoded).isEqualTo(data)
    }

    @Test
    fun `decode rejects non-u prefix`() {
        try {
            Multibase.decode("zABC123")
            assertThat(false).isTrue() // should not reach
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("base64url")
        }
    }
}
