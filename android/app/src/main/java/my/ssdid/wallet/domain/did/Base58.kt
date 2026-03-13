package my.ssdid.wallet.domain.did

import java.math.BigInteger

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    fun decode(input: String): ByteArray {
        var bi = BigInteger.ZERO
        for (ch in input) {
            val digit = ALPHABET.indexOf(ch)
            require(digit >= 0) { "Invalid Base58 character: $ch" }
            bi = bi.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes = bi.toByteArray()
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + stripped
    }
}
