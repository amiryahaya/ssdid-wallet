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

    // --- G3: Long, Boolean, List type branches ---

    @Test
    fun roundTripLongValue() {
        val encoded = CborCodec.encodeDataElement(Long.MAX_VALUE)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun roundTripBooleanTrue() {
        val encoded = CborCodec.encodeDataElement(true)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(true)
    }

    @Test
    fun roundTripBooleanFalse() {
        val encoded = CborCodec.encodeDataElement(false)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(false)
    }

    @Test
    fun roundTripList() {
        val list = listOf("alpha", "beta", "gamma")
        val encoded = CborCodec.encodeDataElement(list)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(list)
    }

    @Test
    fun roundTripMixedList() {
        val list = listOf("hello", 42, true)
        val encoded = CborCodec.encodeDataElement(list)
        @Suppress("UNCHECKED_CAST")
        val decoded = CborCodec.decodeDataElement(encoded) as List<Any>
        assertThat(decoded[0]).isEqualTo("hello")
        assertThat(decoded[1]).isEqualTo(42)
        assertThat(decoded[2]).isEqualTo(true)
    }

    @Test
    fun toCborObjectHandlesUnknownTypeFallback() {
        // Should fall back to toString()
        val encoded = CborCodec.encodeDataElement(3.14)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded.toString()).contains("3.14")
    }

    @Test
    fun encodedBytesAreValidCbor() {
        val encoded = CborCodec.encodeDataElement("test")
        assertThat(encoded).isNotEmpty()
        CborCodec.decodeDataElement(encoded)
    }
}
