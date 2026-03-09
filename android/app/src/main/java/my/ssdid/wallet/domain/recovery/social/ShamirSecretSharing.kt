package my.ssdid.wallet.domain.recovery.social

import java.security.SecureRandom

/**
 * Shamir's Secret Sharing over GF(256) using the irreducible polynomial x^8 + x^4 + x^3 + x + 1.
 * Splits a byte array secret into N shares with threshold K (any K shares can reconstruct).
 */
object ShamirSecretSharing {

    data class Share(val index: Int, val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Share && index == other.index && data.contentEquals(other.data)
        override fun hashCode(): Int = 31 * index + data.contentHashCode()
    }

    /**
     * Split [secret] into [n] shares requiring [k] shares to reconstruct.
     * @param secret The secret bytes to split
     * @param k Minimum shares needed (threshold), must be >= 2
     * @param n Total shares to generate, must be >= k and <= 255
     */
    fun split(secret: ByteArray, k: Int, n: Int): List<Share> {
        require(secret.isNotEmpty()) { "Secret must not be empty" }
        require(k >= 2) { "Threshold must be at least 2, got $k" }
        require(n >= k) { "Total shares ($n) must be >= threshold ($k)" }
        require(n <= 255) { "Maximum 255 shares supported, got $n" }

        val random = SecureRandom()
        val shares = Array(n) { ByteArray(secret.size) }

        for (byteIdx in secret.indices) {
            // Build random polynomial: coefficients[0] = secret byte, rest random
            val coefficients = ByteArray(k)
            coefficients[0] = secret[byteIdx]
            for (i in 1 until k) {
                coefficients[i] = (random.nextInt(256)).toByte()
            }

            // Evaluate polynomial at x = 1..n
            for (shareIdx in 0 until n) {
                val x = shareIdx + 1 // x values are 1-indexed
                shares[shareIdx][byteIdx] = evaluatePolynomial(coefficients, x)
            }
        }

        return shares.mapIndexed { idx, data -> Share(idx + 1, data) }
    }

    /**
     * Reconstruct the secret from [k] or more shares.
     * @param shares At least [k] shares (the threshold used during split)
     */
    fun combine(shares: List<Share>): ByteArray {
        require(shares.size >= 2) { "Need at least 2 shares, got ${shares.size}" }
        val secretSize = shares.first().data.size
        require(shares.all { it.data.size == secretSize }) { "All shares must have equal length" }

        val indices = shares.map { it.index }
        require(indices.toSet().size == indices.size) { "Duplicate share indices" }

        val result = ByteArray(secretSize)
        for (byteIdx in 0 until secretSize) {
            // Lagrange interpolation at x = 0
            var value = 0
            for (i in shares.indices) {
                val xi = shares[i].index
                val yi = shares[i].data[byteIdx].toInt() and 0xFF
                var lagrange = 1
                for (j in shares.indices) {
                    if (i == j) continue
                    val xj = shares[j].index
                    // lagrange *= (0 - xj) / (xi - xj) in GF(256)
                    lagrange = gfMul(lagrange, gfMul(xj, gfInverse(xi xor xj)))
                }
                value = value xor gfMul(yi, lagrange)
            }
            result[byteIdx] = value.toByte()
        }
        return result
    }

    // GF(256) arithmetic with irreducible polynomial 0x11B (x^8 + x^4 + x^3 + x + 1)

    private fun evaluatePolynomial(coefficients: ByteArray, x: Int): Byte {
        // Horner's method: result = c[k-1]*x + c[k-2], etc.
        var result = 0
        for (i in coefficients.indices.reversed()) {
            result = gfMul(result, x) xor (coefficients[i].toInt() and 0xFF)
        }
        return result.toByte()
    }

    internal fun gfMul(a: Int, b: Int): Int {
        var aa = a
        var bb = b
        var result = 0
        while (bb > 0) {
            if (bb and 1 != 0) result = result xor aa
            aa = aa shl 1
            if (aa and 0x100 != 0) aa = aa xor 0x11B
            bb = bb shr 1
        }
        return result
    }

    internal fun gfInverse(a: Int): Int {
        require(a != 0) { "Cannot invert zero in GF(256)" }
        // a^254 = a^(-1) in GF(256) by Fermat's little theorem
        var result = a
        for (i in 0 until 6) {
            result = gfMul(result, result) // square
            result = gfMul(result, a)      // multiply by a
        }
        result = gfMul(result, result) // final square: total exponent = 254
        return result
    }
}
