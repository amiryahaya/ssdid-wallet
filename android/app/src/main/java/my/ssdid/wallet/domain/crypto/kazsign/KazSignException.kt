/*
 * KAZ-SIGN Android Wrapper
 * Version 3.0.0
 *
 * Exception class for KAZ-SIGN errors.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * Exception thrown when a KAZ-SIGN operation fails.
 *
 * @property errorCode The error code from the native library
 */
class KazSignException(
    message: String,
    val errorCode: ErrorCode = ErrorCode.UNKNOWN
) : Exception(message) {

    /**
     * Error codes returned by KAZ-SIGN operations.
     */
    enum class ErrorCode(val value: Int) {
        /** Operation successful */
        SUCCESS(0),
        /** Memory allocation failed */
        MEMORY_ERROR(-1),
        /** Random number generation failed */
        RNG_ERROR(-2),
        /** Invalid parameter */
        INVALID_PARAMETER(-3),
        /** Signature verification failed */
        VERIFICATION_FAILED(-4),
        /** DER encoding/decoding failed */
        DER_ERROR(-5),
        /** X.509 certificate operation failed */
        X509_ERROR(-6),
        /** PKCS#12 keystore operation failed */
        P12_ERROR(-7),
        /** Hash computation failed */
        HASH_ERROR(-8),
        /** Buffer too small */
        BUFFER_ERROR(-9),
        /** Unknown error */
        UNKNOWN(-99);

        companion object {
            fun fromValue(value: Int): ErrorCode {
                return entries.find { it.value == value } ?: UNKNOWN
            }
        }
    }

    companion object {
        /**
         * Create exception from error code.
         */
        fun fromErrorCode(code: Int): KazSignException {
            val errorCode = ErrorCode.fromValue(code)
            val message = when (errorCode) {
                ErrorCode.SUCCESS -> "Operation successful"
                ErrorCode.MEMORY_ERROR -> "Memory allocation failed"
                ErrorCode.RNG_ERROR -> "Random number generation failed"
                ErrorCode.INVALID_PARAMETER -> "Invalid parameter"
                ErrorCode.VERIFICATION_FAILED -> "Signature verification failed"
                ErrorCode.DER_ERROR -> "DER encoding/decoding failed"
                ErrorCode.X509_ERROR -> "X.509 certificate operation failed"
                ErrorCode.P12_ERROR -> "PKCS#12 keystore operation failed"
                ErrorCode.HASH_ERROR -> "Hash computation failed"
                ErrorCode.BUFFER_ERROR -> "Buffer too small"
                ErrorCode.UNKNOWN -> "Unknown error (code: $code)"
            }
            return KazSignException(message, errorCode)
        }
    }
}
