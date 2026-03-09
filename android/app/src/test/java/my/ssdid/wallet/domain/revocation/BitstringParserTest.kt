package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class BitstringParserTest {

    private fun makeEncodedList(bitCount: Int, revokedIndices: Set<Int>): String {
        val byteCount = (bitCount + 7) / 8
        val bytes = ByteArray(byteCount)
        for (idx in revokedIndices) {
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8) // MSB first
            bytes[bytePos] = (bytes[bytePos].toInt() or (1 shl bitPos)).toByte()
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    @Test
    fun `isRevoked returns true for revoked index`() {
        val encoded = makeEncodedList(128, setOf(42))
        assertThat(BitstringParser.isRevoked(encoded, 42)).isTrue()
    }

    @Test
    fun `isRevoked returns false for non-revoked index`() {
        val encoded = makeEncodedList(128, setOf(42))
        assertThat(BitstringParser.isRevoked(encoded, 43)).isFalse()
    }

    @Test
    fun `isRevoked handles multiple revoked indices`() {
        val encoded = makeEncodedList(256, setOf(0, 7, 8, 127, 255))
        assertThat(BitstringParser.isRevoked(encoded, 0)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 7)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 8)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 127)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 255)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 1)).isFalse()
        assertThat(BitstringParser.isRevoked(encoded, 128)).isFalse()
    }

    @Test
    fun `isRevoked handles all-zeros bitstring`() {
        val encoded = makeEncodedList(128, emptySet())
        assertThat(BitstringParser.isRevoked(encoded, 0)).isFalse()
        assertThat(BitstringParser.isRevoked(encoded, 64)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `isRevoked throws for negative index`() {
        val encoded = makeEncodedList(128, emptySet())
        BitstringParser.isRevoked(encoded, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `isRevoked throws for index beyond bitstring`() {
        val encoded = makeEncodedList(8, emptySet()) // only 1 byte = 8 bits
        BitstringParser.isRevoked(encoded, 8)
    }
}
