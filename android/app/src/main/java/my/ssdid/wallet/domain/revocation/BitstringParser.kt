package my.ssdid.wallet.domain.revocation

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

object BitstringParser {
    fun isRevoked(encodedList: String, index: Int): Boolean {
        require(index >= 0) { "Status list index must be non-negative: $index" }
        val compressed = Base64.getUrlDecoder().decode(encodedList)
        val bitstring = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val bytePos = index / 8
        require(bytePos < bitstring.size) { "Index $index out of range (bitstring has ${bitstring.size * 8} bits)" }
        val bitPos = 7 - (index % 8) // MSB first per W3C spec
        return (bitstring[bytePos].toInt() ushr bitPos) and 1 == 1
    }
}
