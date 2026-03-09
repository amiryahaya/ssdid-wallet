/*
 * KAZ-SIGN Android Wrapper
 * Version 3.0.0
 *
 * PKCS#12 keystore contents data class for KAZ-SIGN.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * Contents loaded from a PKCS#12 keystore.
 *
 * @property secretKey The secret signing key
 * @property publicKey The public verification key
 * @property certificate The DER-encoded certificate (may be null if not present)
 * @property level The security level
 */
data class P12Contents(
    val secretKey: ByteArray,
    val publicKey: ByteArray,
    val certificate: ByteArray?,
    val level: Int
) {
    /**
     * Get the security level enum.
     */
    val securityLevel: SecurityLevel
        get() = SecurityLevel.fromValue(level)

    /**
     * Whether a certificate is present in the keystore.
     */
    val hasCertificate: Boolean
        get() = certificate != null && certificate.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as P12Contents

        if (!secretKey.contentEquals(other.secretKey)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (certificate != null) {
            if (other.certificate == null) return false
            if (!certificate.contentEquals(other.certificate)) return false
        } else if (other.certificate != null) return false
        if (level != other.level) return false

        return true
    }

    override fun hashCode(): Int {
        var result = secretKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (certificate?.contentHashCode() ?: 0)
        result = 31 * result + level
        return result
    }

    override fun toString(): String {
        return "P12Contents(level=$level, hasCertificate=$hasCertificate, " +
               "secretKey=[REDACTED], publicKey=${publicKey.toHexString().take(16)}...)"
    }
}
