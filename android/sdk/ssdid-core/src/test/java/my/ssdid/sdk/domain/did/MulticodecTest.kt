package my.ssdid.sdk.domain.did

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MulticodecTest {

    // --- Single-byte varint encode/decode round-trip (Ed25519 = 0xed) ---
    @Test
    fun `encode and decode round-trip for Ed25519 single-byte varint`() {
        val keyBytes = ByteArray(32) { it.toByte() }
        val encoded = Multicodec.encode(Multicodec.ED25519_PUB, keyBytes)
        val (codec, decoded) = Multicodec.decode(encoded)

        assertThat(codec).isEqualTo(Multicodec.ED25519_PUB)
        assertThat(decoded).isEqualTo(keyBytes)
    }

    // --- Two-byte varint encode/decode round-trip (P-256 = 0x1200) ---
    @Test
    fun `encode and decode round-trip for P-256 two-byte varint`() {
        val keyBytes = ByteArray(64) { (it + 1).toByte() }
        val encoded = Multicodec.encode(Multicodec.P256_PUB, keyBytes)
        val (codec, decoded) = Multicodec.decode(encoded)

        assertThat(codec).isEqualTo(Multicodec.P256_PUB)
        assertThat(decoded).isEqualTo(keyBytes)
    }

    // --- Decode rejects empty input ---
    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects empty input`() {
        Multicodec.decode(byteArrayOf())
    }

    // --- Decode rejects single-byte input for multi-byte varint ---
    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects single-byte input for multi-byte varint`() {
        // 0x80 has high bit set, so decoder expects at least 3 bytes total
        Multicodec.decode(byteArrayOf(0x80.toByte()))
    }
}
