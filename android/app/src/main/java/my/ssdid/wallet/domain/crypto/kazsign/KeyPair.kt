/*
 * KAZ-SIGN Android Wrapper
 * Version 2.0.0
 *
 * Key pair data class for KAZ-SIGN.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * A KAZ-SIGN key pair containing public and secret keys.
 *
 * @property publicKey The public verification key
 * @property secretKey The secret signing key
 * @property level The security level used to generate this key pair
 */
data class KeyPair(
    val publicKey: ByteArray,
    val secretKey: ByteArray,
    val level: Int
) {
    /**
     * Get the security level enum.
     */
    val securityLevel: SecurityLevel
        get() = SecurityLevel.fromValue(level)

    /**
     * Get the public key as a hexadecimal string.
     */
    val publicKeyHex: String
        get() = publicKey.toHexString()

    /**
     * Get the secret key as a hexadecimal string.
     */
    val secretKeyHex: String
        get() = secretKey.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!secretKey.contentEquals(other.secretKey)) return false
        if (level != other.level) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + secretKey.contentHashCode()
        result = 31 * result + level
        return result
    }

    override fun toString(): String {
        return "KeyPair(level=$level, publicKey=${publicKeyHex.take(16)}..., secretKey=[REDACTED])"
    }
}
