/*
 * KAZ-SIGN Android Wrapper
 * Version 2.1.0
 *
 * Security level enumeration for KAZ-SIGN operations.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * Security level for KAZ-SIGN cryptographic operations.
 *
 * @property value The numeric value (128, 192, or 256)
 * @property secretKeyBytes Size of secret key in bytes
 * @property publicKeyBytes Size of public key in bytes
 * @property signatureOverhead Signature overhead in bytes (excluding message)
 * @property hashBytes Hash output size in bytes
 * @property algorithmName Algorithm name string
 */
enum class SecurityLevel(
    val value: Int,
    val secretKeyBytes: Int,
    val publicKeyBytes: Int,
    val signatureOverhead: Int,
    val hashBytes: Int,
    val algorithmName: String
) {
    /**
     * 128-bit security level (SHA-256)
     */
    LEVEL_128(
        value = 128,
        secretKeyBytes = 98,
        publicKeyBytes = 49,
        signatureOverhead = 57,
        hashBytes = 32,
        algorithmName = "KAZ-SIGN-128"
    ),

    /**
     * 192-bit security level (SHA-384)
     */
    LEVEL_192(
        value = 192,
        secretKeyBytes = 146,
        publicKeyBytes = 73,
        signatureOverhead = 81,
        hashBytes = 48,
        algorithmName = "KAZ-SIGN-192"
    ),

    /**
     * 256-bit security level (SHA-512)
     */
    LEVEL_256(
        value = 256,
        secretKeyBytes = 194,
        publicKeyBytes = 97,
        signatureOverhead = 105,
        hashBytes = 64,
        algorithmName = "KAZ-SIGN-256"
    );

    companion object {
        /**
         * Get security level from numeric value.
         *
         * @param value The numeric level (128, 192, or 256)
         * @return The corresponding SecurityLevel
         * @throws IllegalArgumentException if value is not valid
         */
        fun fromValue(value: Int): SecurityLevel {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid security level: $value. Must be 128, 192, or 256.")
        }
    }
}
