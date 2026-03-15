/*
 * KAZ-SIGN Android Wrapper
 * Version 4.0.0
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
        secretKeyBytes = 32,    // s(16) + t(16)
        publicKeyBytes = 54,    // v
        signatureOverhead = 162, // S1(54) + S2(54) + S3(54)
        hashBytes = 32,
        algorithmName = "KAZ-SIGN-128"
    ),

    /**
     * 192-bit security level (SHA-384)
     */
    LEVEL_192(
        value = 192,
        secretKeyBytes = 50,    // s(25) + t(25)
        publicKeyBytes = 88,    // v
        signatureOverhead = 264, // S1(88) + S2(88) + S3(88)
        hashBytes = 48,
        algorithmName = "KAZ-SIGN-192"
    ),

    /**
     * 256-bit security level (SHA-512)
     */
    LEVEL_256(
        value = 256,
        secretKeyBytes = 64,    // s(32) + t(32)
        publicKeyBytes = 118,   // v
        signatureOverhead = 354, // S1(118) + S2(118) + S3(118)
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
