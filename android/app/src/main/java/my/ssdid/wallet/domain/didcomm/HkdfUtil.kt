package my.ssdid.wallet.domain.didcomm

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 per RFC 5869.
 *
 * Extract-then-expand key derivation. When no salt is provided,
 * RFC 5869 §2.2 specifies using a string of HashLen (32) zero bytes.
 */
object HkdfUtil {

    /**
     * Derive [length] bytes of key material from [ikm] using HKDF-SHA256.
     *
     * @param ikm    Input keying material (e.g., ECDH shared secret).
     * @param salt   Optional salt; defaults to 32 zero bytes per RFC 5869 §2.2.
     * @param info   Context/application-specific info string.
     * @param length Desired output length in bytes (must be <= 255 * 32).
     * @throws IllegalArgumentException if length exceeds maximum.
     */
    fun deriveKey(
        ikm: ByteArray,
        salt: ByteArray = ByteArray(32),
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..(255 * 32)) {
            "HKDF output length must be 1..${255 * 32}, got $length"
        }

        // Extract: PRK = HMAC-Hash(salt, IKM)
        // Per RFC 5869 §2.2: if salt is not provided or zero-length,
        // it is set to a string of HashLen (32 for SHA-256) zero octets.
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Expand: T(1) || T(2) || ... where T(i) = HMAC-Hash(PRK, T(i-1) || info || i)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val output = ByteArray(length)
        var prev = byteArrayOf()
        var offset = 0
        var counter = 1

        while (offset < length) {
            mac.update(prev)
            mac.update(info)
            mac.update(byteArrayOf(counter.toByte()))
            prev = mac.doFinal()
            val toCopy = minOf(prev.size, length - offset)
            System.arraycopy(prev, 0, output, offset, toCopy)
            offset += toCopy
            counter++
        }

        return output
    }
}
