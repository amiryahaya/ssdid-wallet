/*
 * KAZ-SIGN Android Wrapper
 * Version 2.1.0
 *
 * Extension functions for KAZ-SIGN.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * Convert a byte array to a hexadecimal string.
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}

/**
 * Convert a hexadecimal string to a byte array.
 *
 * @throws IllegalArgumentException if the string is not valid hex
 */
fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Securely clear a byte array by overwriting with zeros.
 */
fun ByteArray.secureWipe() {
    fill(0)
}
