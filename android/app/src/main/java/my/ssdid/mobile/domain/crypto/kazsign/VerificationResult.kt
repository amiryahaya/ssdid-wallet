/*
 * KAZ-SIGN Android Wrapper
 * Version 2.1.0
 *
 * Verification result data class for KAZ-SIGN.
 */

package my.ssdid.mobile.domain.crypto.kazsign

/**
 * Result of a signature verification operation.
 *
 * @property isValid Whether the signature is valid
 * @property message The recovered message (if valid), null otherwise
 * @property level The security level used for verification
 */
data class VerificationResult(
    val isValid: Boolean,
    val message: ByteArray?,
    val level: Int
) {
    /**
     * Get the security level enum.
     */
    val securityLevel: SecurityLevel
        get() = SecurityLevel.fromValue(level)

    /**
     * Get the recovered message as a UTF-8 string.
     *
     * @return The message as a string, or null if verification failed
     */
    fun getMessageAsString(): String? {
        return message?.toString(Charsets.UTF_8)
    }

    /**
     * Get the recovered message as a hexadecimal string.
     *
     * @return The message as hex, or null if verification failed
     */
    fun getMessageAsHex(): String? {
        return message?.toHexString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VerificationResult

        if (isValid != other.isValid) return false
        if (message != null) {
            if (other.message == null) return false
            if (!message.contentEquals(other.message)) return false
        } else if (other.message != null) return false
        if (level != other.level) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + (message?.contentHashCode() ?: 0)
        result = 31 * result + level
        return result
    }

    override fun toString(): String {
        return "VerificationResult(isValid=$isValid, level=$level, " +
               "messageLength=${message?.size ?: 0})"
    }
}
