package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CborCodecTest {

    @Test
    fun roundTripStringValue() {
        val encoded = CborCodec.encodeDataElement("hello")
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo("hello")
    }

    @Test
    fun roundTripIntValue() {
        val encoded = CborCodec.encodeDataElement(42)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(42)
    }

    @Test
    fun roundTripByteArray() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = CborCodec.encodeDataElement(bytes)
        val decoded = CborCodec.decodeDataElement(encoded) as ByteArray
        assertThat(decoded).isEqualTo(bytes)
    }

    @Test
    fun roundTripMap() {
        val map = mapOf("name" to "Alice", "age" to 30)
        val encoded = CborCodec.encodeMap(map)
        val decoded = CborCodec.decodeMap(encoded)
        assertThat(decoded["name"]).isEqualTo("Alice")
        assertThat(decoded["age"]).isEqualTo(30)
    }

    @Test
    fun roundTripNestedMap() {
        val map = mapOf(
            "outer" to mapOf("inner" to "value")
        )
        val encoded = CborCodec.encodeMap(map)
        val decoded = CborCodec.decodeMap(encoded)
        @Suppress("UNCHECKED_CAST")
        val inner = decoded["outer"] as Map<String, Any>
        assertThat(inner["inner"]).isEqualTo("value")
    }

    @Test
    fun encodedBytesAreValidCbor() {
        val encoded = CborCodec.encodeDataElement("test")
        assertThat(encoded).isNotEmpty()
        CborCodec.decodeDataElement(encoded)
    }
}
