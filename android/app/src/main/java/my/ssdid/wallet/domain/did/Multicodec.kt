package my.ssdid.wallet.domain.did

object Multicodec {
    const val ED25519_PUB: Int = 0xed      // varint: 0xed 0x01
    const val P256_PUB: Int = 0x1200       // varint: 0x80 0x24
    const val P384_PUB: Int = 0x1201       // varint: 0x81 0x24

    fun decode(data: ByteArray): Pair<Int, ByteArray> {
        require(data.size >= 2) { "Data too short for multicodec" }
        val first = data[0].toInt() and 0xFF
        return if (first and 0x80 == 0) {
            first to data.copyOfRange(1, data.size)
        } else {
            require(data.size >= 3) { "Data too short for 2-byte varint" }
            val second = data[1].toInt() and 0xFF
            val codec = (first and 0x7F) or (second shl 7)
            codec to data.copyOfRange(2, data.size)
        }
    }

    fun encode(codec: Int, keyBytes: ByteArray): ByteArray {
        return if (codec < 0x80) {
            byteArrayOf(codec.toByte()) + keyBytes
        } else {
            byteArrayOf(
                ((codec and 0x7F) or 0x80).toByte(),
                (codec shr 7).toByte()
            ) + keyBytes
        }
    }
}
