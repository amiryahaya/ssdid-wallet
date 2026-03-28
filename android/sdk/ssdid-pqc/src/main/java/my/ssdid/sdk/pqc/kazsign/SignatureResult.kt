/*
 * KAZ-SIGN Android Wrapper
 * Version 4.0.0
 *
 * Signature result data class for KAZ-SIGN.
 */

package my.ssdid.sdk.pqc.kazsign

/**
 * Result of a signing operation.
 *
 * @property signature The complete signature (includes the message)
 * @property message The original message that was signed
 * @property level The security level used for signing
 */
data class SignatureResult(
    val signature: ByteArray,
    val message: ByteArray,
    val level: Int
) {
    /**
     * Get the security level enum.
     */
    val securityLevel: SecurityLevel
        get() = SecurityLevel.fromValue(level)

    /**
     * Get the signature overhead (signature bytes without message).
     */
    val overhead: Int
        get() = signature.size - message.size

    /**
     * Get the signature as a hexadecimal string.
     */
    val signatureHex: String
        get() = signature.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignatureResult

        if (!signature.contentEquals(other.signature)) return false
        if (!message.contentEquals(other.message)) return false
        if (level != other.level) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + message.contentHashCode()
        result = 31 * result + level
        return result
    }

    override fun toString(): String {
        return "SignatureResult(level=$level, signatureLength=${signature.size}, " +
               "messageLength=${message.size}, overhead=$overhead)"
    }
}
