/*
 * KAZ-SIGN Android Wrapper
 * Version 3.0.0
 *
 * Post-quantum digital signature library for Android.
 * Supports security levels 128, 192, and 256 at runtime.
 *
 * Usage:
 *   val signer = KazSigner(SecurityLevel.LEVEL_128)
 *   val keyPair = signer.generateKeyPair()
 *   val signature = signer.sign(message, keyPair.secretKey)
 *   val result = signer.verify(signature, keyPair.publicKey)
 */

package my.ssdid.mobile.domain.crypto.kazsign

import java.io.Closeable

/**
 * KAZ-SIGN digital signature operations.
 *
 * This class provides post-quantum secure digital signatures using the KAZ-SIGN algorithm.
 * It supports multiple security levels (128, 192, 256-bit) selectable at runtime.
 *
 * Example usage:
 * ```kotlin
 * val signer = KazSigner(SecurityLevel.LEVEL_128)
 * try {
 *     val keyPair = signer.generateKeyPair()
 *     val message = "Hello, World!".toByteArray()
 *     val signatureResult = signer.sign(message, keyPair.secretKey)
 *     val verificationResult = signer.verify(signatureResult.signature, keyPair.publicKey)
 *     println("Valid: ${verificationResult.isValid}")
 * } finally {
 *     signer.close()
 * }
 * ```
 *
 * @property level The security level for this signer instance
 */
class KazSigner(
    val level: SecurityLevel
) : Closeable {

    private var isInitialized = false
    private var isClosed = false

    /**
     * Create a KazSigner with the specified security level.
     *
     * @param level Numeric security level (128, 192, or 256)
     */
    constructor(level: Int) : this(SecurityLevel.fromValue(level))

    init {
        initialize()
    }

    // ========================================================================
    // Properties
    // ========================================================================

    /**
     * Size of secret key in bytes for this security level.
     */
    val secretKeyBytes: Int
        get() = level.secretKeyBytes

    /**
     * Size of public key in bytes for this security level.
     */
    val publicKeyBytes: Int
        get() = level.publicKeyBytes

    /**
     * Signature overhead in bytes (excluding message) for this security level.
     */
    val signatureOverhead: Int
        get() = level.signatureOverhead

    /**
     * Hash output size in bytes for this security level.
     */
    val hashBytes: Int
        get() = level.hashBytes

    /**
     * Algorithm name for this security level.
     */
    val algorithmName: String
        get() = level.algorithmName

    // ========================================================================
    // Initialization
    // ========================================================================

    /**
     * Initialize the native library for this security level.
     *
     * This is called automatically by the constructor.
     *
     * @throws KazSignException if initialization fails
     */
    private fun initialize() {
        check(!isClosed) { "KazSigner has been closed" }
        if (isInitialized) return

        val result = KazSignNative.nativeInitLevel(level.value)
        if (result != 0) {
            throw KazSignException.fromErrorCode(result)
        }
        isInitialized = true
    }

    /**
     * Check if this signer is initialized.
     */
    fun isInitialized(): Boolean = isInitialized && !isClosed

    // ========================================================================
    // Key Generation
    // ========================================================================

    /**
     * Generate a new key pair.
     *
     * @return A KeyPair containing the public and secret keys
     * @throws KazSignException if key generation fails
     * @throws IllegalStateException if the signer is closed
     */
    fun generateKeyPair(): KeyPair {
        ensureInitialized()
        return KazSignNative.nativeGenerateKeyPair(level.value)
    }

    // ========================================================================
    // Signing
    // ========================================================================

    /**
     * Sign a message.
     *
     * @param message The message to sign
     * @param secretKey The secret signing key
     * @return A SignatureResult containing the signature
     * @throws KazSignException if signing fails
     * @throws IllegalArgumentException if secret key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun sign(message: ByteArray, secretKey: ByteArray): SignatureResult {
        ensureInitialized()
        require(secretKey.size == secretKeyBytes) {
            "Secret key must be $secretKeyBytes bytes, got ${secretKey.size}"
        }

        val signature = KazSignNative.nativeSign(level.value, message, secretKey)
        return SignatureResult(signature, message, level.value)
    }

    /**
     * Sign a string message.
     *
     * @param message The string message to sign (encoded as UTF-8)
     * @param secretKey The secret signing key
     * @return A SignatureResult containing the signature
     */
    fun sign(message: String, secretKey: ByteArray): SignatureResult {
        return sign(message.toByteArray(Charsets.UTF_8), secretKey)
    }

    // ========================================================================
    // Verification
    // ========================================================================

    /**
     * Verify a signature and extract the message.
     *
     * @param signature The signature to verify (includes the message)
     * @param publicKey The public verification key
     * @return A VerificationResult with validity and recovered message
     * @throws IllegalArgumentException if public key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun verify(signature: ByteArray, publicKey: ByteArray): VerificationResult {
        ensureInitialized()
        require(publicKey.size == publicKeyBytes) {
            "Public key must be $publicKeyBytes bytes, got ${publicKey.size}"
        }

        return KazSignNative.nativeVerify(level.value, signature, publicKey)
    }

    /**
     * Verify a signature and get the message as a string.
     *
     * @param signature The signature to verify
     * @param publicKey The public verification key
     * @return A Pair of (isValid, recoveredMessage)
     */
    fun verifyString(signature: ByteArray, publicKey: ByteArray): Pair<Boolean, String?> {
        val result = verify(signature, publicKey)
        return Pair(result.isValid, result.getMessageAsString())
    }

    // ========================================================================
    // Hashing
    // ========================================================================

    /**
     * Hash a message using the appropriate hash function for this security level.
     *
     * @param message The message to hash
     * @return The hash value
     * @throws KazSignException if hashing fails
     * @throws IllegalStateException if the signer is closed
     */
    fun hash(message: ByteArray): ByteArray {
        ensureInitialized()
        return KazSignNative.nativeHash(level.value, message)
    }

    /**
     * Hash a string message.
     *
     * @param message The string message to hash (encoded as UTF-8)
     * @return The hash value
     */
    fun hash(message: String): ByteArray {
        return hash(message.toByteArray(Charsets.UTF_8))
    }

    // ========================================================================
    // Detached Signatures
    // ========================================================================

    /**
     * Create a detached signature (signature does not include the message).
     *
     * @param data The data to sign
     * @param secretKey The secret signing key
     * @return The detached signature bytes
     * @throws KazSignException if signing fails
     * @throws IllegalArgumentException if secret key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun signDetached(data: ByteArray, secretKey: ByteArray): ByteArray {
        ensureInitialized()
        require(secretKey.size == secretKeyBytes) {
            "Secret key must be $secretKeyBytes bytes, got ${secretKey.size}"
        }

        return KazSignNative.nativeSignDetached(level.value, data, secretKey)
    }

    /**
     * Verify a detached signature.
     *
     * @param data The original data
     * @param signature The detached signature
     * @param publicKey The public verification key
     * @return true if the signature is valid, false otherwise
     * @throws IllegalArgumentException if public key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun verifyDetached(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        ensureInitialized()
        require(publicKey.size == publicKeyBytes) {
            "Public key must be $publicKeyBytes bytes, got ${publicKey.size}"
        }

        return KazSignNative.nativeVerifyDetached(level.value, data, signature, publicKey)
    }

    // ========================================================================
    // DER Key Encoding
    // ========================================================================

    /**
     * Encode a public key to DER format.
     *
     * @param publicKey The raw public key
     * @return DER-encoded public key
     * @throws KazSignException if encoding fails
     * @throws IllegalArgumentException if public key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun publicKeyToDer(publicKey: ByteArray): ByteArray {
        ensureInitialized()
        require(publicKey.size == publicKeyBytes) {
            "Public key must be $publicKeyBytes bytes, got ${publicKey.size}"
        }

        return KazSignNative.nativePublicKeyToDer(level.value, publicKey)
    }

    /**
     * Decode a public key from DER format.
     *
     * @param der DER-encoded public key
     * @return The raw public key
     * @throws KazSignException if decoding fails
     * @throws IllegalStateException if the signer is closed
     */
    fun publicKeyFromDer(der: ByteArray): ByteArray {
        ensureInitialized()
        return KazSignNative.nativePublicKeyFromDer(level.value, der)
    }

    /**
     * Encode a private key to DER format.
     *
     * @param secretKey The raw secret key
     * @return DER-encoded private key
     * @throws KazSignException if encoding fails
     * @throws IllegalArgumentException if secret key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun privateKeyToDer(secretKey: ByteArray): ByteArray {
        ensureInitialized()
        require(secretKey.size == secretKeyBytes) {
            "Secret key must be $secretKeyBytes bytes, got ${secretKey.size}"
        }

        return KazSignNative.nativePrivateKeyToDer(level.value, secretKey)
    }

    /**
     * Decode a private key from DER format.
     *
     * @param der DER-encoded private key
     * @return The raw secret key
     * @throws KazSignException if decoding fails
     * @throws IllegalStateException if the signer is closed
     */
    fun privateKeyFromDer(der: ByteArray): ByteArray {
        ensureInitialized()
        return KazSignNative.nativePrivateKeyFromDer(level.value, der)
    }

    // ========================================================================
    // X.509 Certificates
    // ========================================================================

    /**
     * Generate a PKCS#10 Certificate Signing Request (CSR).
     *
     * @param secretKey The secret signing key
     * @param publicKey The public key
     * @param cn Common Name for the subject
     * @param org Organization name (optional)
     * @param ou Organizational Unit name (optional)
     * @return DER-encoded CSR
     * @throws KazSignException if CSR generation fails
     * @throws IllegalArgumentException if key sizes are invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun generateCsr(secretKey: ByteArray, publicKey: ByteArray,
                    cn: String, org: String? = null, ou: String? = null): ByteArray {
        ensureInitialized()
        require(secretKey.size == secretKeyBytes) {
            "Secret key must be $secretKeyBytes bytes, got ${secretKey.size}"
        }
        require(publicKey.size == publicKeyBytes) {
            "Public key must be $publicKeyBytes bytes, got ${publicKey.size}"
        }

        // Build subject distinguished name
        val subject = buildString {
            append("CN=$cn")
            if (org != null) append(",O=$org")
            if (ou != null) append(",OU=$ou")
        }

        return KazSignNative.nativeGenerateCsr(level.value, secretKey, publicKey, subject)
    }

    /**
     * Issue an X.509 certificate by signing a CSR.
     *
     * @param issuerSk Issuer secret key
     * @param issuerPk Issuer public key
     * @param issuerName Issuer distinguished name
     * @param csr DER-encoded CSR from the subject
     * @param serial Certificate serial number
     * @param days Validity period in days
     * @return DER-encoded certificate
     * @throws KazSignException if certificate issuance fails
     * @throws IllegalArgumentException if key sizes are invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun issueCertificate(issuerSk: ByteArray, issuerPk: ByteArray,
                         issuerName: String, csr: ByteArray,
                         serial: Long, days: Int): ByteArray {
        ensureInitialized()
        require(issuerSk.size == secretKeyBytes) {
            "Issuer secret key must be $secretKeyBytes bytes, got ${issuerSk.size}"
        }
        require(issuerPk.size == publicKeyBytes) {
            "Issuer public key must be $publicKeyBytes bytes, got ${issuerPk.size}"
        }

        return KazSignNative.nativeIssueCertificate(level.value, issuerSk, issuerPk,
                                                     issuerName, csr, serial, days)
    }

    /**
     * Verify an X.509 certificate signature against an issuer public key.
     *
     * @param cert DER-encoded certificate
     * @param issuerPk Issuer public key
     * @return true if the certificate is valid, false otherwise
     * @throws IllegalArgumentException if public key size is invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun verifyCertificate(cert: ByteArray, issuerPk: ByteArray): Boolean {
        ensureInitialized()
        require(issuerPk.size == publicKeyBytes) {
            "Issuer public key must be $publicKeyBytes bytes, got ${issuerPk.size}"
        }

        return KazSignNative.nativeVerifyCertificate(level.value, cert, issuerPk)
    }

    /**
     * Extract the public key from an X.509 certificate.
     *
     * @param cert DER-encoded certificate
     * @return The extracted public key
     * @throws KazSignException if extraction fails
     * @throws IllegalStateException if the signer is closed
     */
    fun extractPublicKey(cert: ByteArray): ByteArray {
        ensureInitialized()
        return KazSignNative.nativeExtractPublicKey(level.value, cert)
    }

    // ========================================================================
    // PKCS#12 Keystore
    // ========================================================================

    /**
     * Create a PKCS#12 keystore containing a key pair and optional certificate.
     *
     * @param secretKey The secret key
     * @param publicKey The public key
     * @param cert DER-encoded certificate (optional)
     * @param password Password to protect the keystore
     * @param name Friendly name for the key entry
     * @return PKCS#12 data
     * @throws KazSignException if creation fails
     * @throws IllegalArgumentException if key sizes are invalid
     * @throws IllegalStateException if the signer is closed
     */
    fun createP12(secretKey: ByteArray, publicKey: ByteArray,
                  cert: ByteArray? = null, password: String, name: String): ByteArray {
        ensureInitialized()
        require(secretKey.size == secretKeyBytes) {
            "Secret key must be $secretKeyBytes bytes, got ${secretKey.size}"
        }
        require(publicKey.size == publicKeyBytes) {
            "Public key must be $publicKeyBytes bytes, got ${publicKey.size}"
        }

        return KazSignNative.nativeCreateP12(level.value, secretKey, publicKey,
                                              cert, password, name)
    }

    /**
     * Load a key pair and certificate from a PKCS#12 keystore.
     *
     * @param p12 PKCS#12 data
     * @param password Password to unlock the keystore
     * @return P12Contents with secret key, public key, and optional certificate
     * @throws KazSignException if loading fails
     * @throws IllegalStateException if the signer is closed
     */
    fun loadP12(p12: ByteArray, password: String): P12Contents {
        ensureInitialized()
        return KazSignNative.nativeLoadP12(level.value, p12, password)
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    private fun ensureInitialized() {
        check(!isClosed) { "KazSigner has been closed" }
        check(isInitialized) { "KazSigner is not initialized" }
    }

    /**
     * Release native resources for this security level.
     *
     * After calling close(), this signer instance cannot be used.
     */
    override fun close() {
        if (!isClosed && isInitialized) {
            KazSignNative.nativeClearLevel(level.value)
            isInitialized = false
        }
        isClosed = true
    }

    // ========================================================================
    // Companion Object
    // ========================================================================

    companion object {
        /**
         * Get the library version string.
         */
        val version: String
            get() = KazSignNative.nativeGetVersion()

        /**
         * Get the library version number.
         *
         * Format: major * 10000 + minor * 100 + patch
         */
        val versionNumber: Int
            get() = KazSignNative.nativeGetVersionNumber()

        /**
         * Clear all native resources for all security levels.
         *
         * Call this when completely done with KAZ-SIGN operations.
         */
        fun clearAll() {
            KazSignNative.nativeClearAll()
        }

        /**
         * Compute SHA3-256 hash of data.
         *
         * This is a standalone function that does not require a KazSigner instance.
         *
         * @param data The data to hash
         * @return 32-byte SHA3-256 hash
         * @throws KazSignException if hashing fails
         */
        fun sha3_256(data: ByteArray): ByteArray {
            return KazSignNative.nativeSha3_256(data)
        }
    }
}

/**
 * Extension function to use KazSigner with automatic resource management.
 *
 * Example:
 * ```kotlin
 * kazSigner(SecurityLevel.LEVEL_128) {
 *     val keyPair = generateKeyPair()
 *     // ... use the signer
 * }
 * ```
 */
inline fun <R> kazSigner(level: SecurityLevel, block: KazSigner.() -> R): R {
    return KazSigner(level).use(block)
}
