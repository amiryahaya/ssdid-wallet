/*
 * KAZ-SIGN Android Wrapper
 * Version 3.0.0
 *
 * JNI native method declarations.
 * This class provides direct access to the native library.
 * For most use cases, use KazSigner instead.
 */

package my.ssdid.wallet.domain.crypto.kazsign

/**
 * Native JNI interface for KAZ-SIGN library.
 *
 * This class loads the native library and declares all JNI methods.
 * For high-level API, use [KazSigner] instead.
 */
internal object KazSignNative {

    init {
        System.loadLibrary("kazsign")
    }

    // ========================================================================
    // Version API
    // ========================================================================

    /**
     * Get the library version string.
     */
    @JvmStatic
    external fun nativeGetVersion(): String

    /**
     * Get the library version number.
     */
    @JvmStatic
    external fun nativeGetVersionNumber(): Int

    // ========================================================================
    // Initialization API
    // ========================================================================

    /**
     * Initialize the library for a specific security level.
     *
     * @param level Security level (128, 192, or 256)
     * @return 0 on success, negative error code on failure
     */
    @JvmStatic
    external fun nativeInitLevel(level: Int): Int

    /**
     * Clear resources for a specific security level.
     *
     * @param level Security level to clear
     */
    @JvmStatic
    external fun nativeClearLevel(level: Int)

    /**
     * Clear resources for all security levels.
     */
    @JvmStatic
    external fun nativeClearAll()

    // ========================================================================
    // Parameter API
    // ========================================================================

    /**
     * Get secret key size in bytes for the given security level.
     */
    @JvmStatic
    external fun nativeGetSecretKeyBytes(level: Int): Int

    /**
     * Get public key size in bytes for the given security level.
     */
    @JvmStatic
    external fun nativeGetPublicKeyBytes(level: Int): Int

    /**
     * Get signature overhead in bytes for the given security level.
     */
    @JvmStatic
    external fun nativeGetSignatureOverhead(level: Int): Int

    /**
     * Get hash output size in bytes for the given security level.
     */
    @JvmStatic
    external fun nativeGetHashBytes(level: Int): Int

    // ========================================================================
    // Key Generation API
    // ========================================================================

    /**
     * Generate a new key pair.
     *
     * @param level Security level
     * @return KeyPair containing public and secret keys
     * @throws KazSignException if key generation fails
     */
    @JvmStatic
    external fun nativeGenerateKeyPair(level: Int): KeyPair

    // ========================================================================
    // Signing API
    // ========================================================================

    /**
     * Sign a message.
     *
     * @param level Security level
     * @param message Message to sign
     * @param secretKey Secret signing key
     * @return Signature (includes the message)
     * @throws KazSignException if signing fails
     */
    @JvmStatic
    external fun nativeSign(level: Int, message: ByteArray, secretKey: ByteArray): ByteArray

    // ========================================================================
    // Verification API
    // ========================================================================

    /**
     * Verify a signature and extract the message.
     *
     * @param level Security level
     * @param signature Signature to verify (includes the message)
     * @param publicKey Public verification key
     * @return VerificationResult with validity and recovered message
     * @throws KazSignException if verification encounters an error
     */
    @JvmStatic
    external fun nativeVerify(level: Int, signature: ByteArray, publicKey: ByteArray): VerificationResult

    // ========================================================================
    // Hash API
    // ========================================================================

    /**
     * Hash a message using the appropriate hash function for the security level.
     *
     * @param level Security level
     * @param message Message to hash
     * @return Hash value
     * @throws KazSignException if hashing fails
     */
    @JvmStatic
    external fun nativeHash(level: Int, message: ByteArray): ByteArray

    // ========================================================================
    // Detached Signature API
    // ========================================================================

    /**
     * Create a detached signature (signature does not include the message).
     *
     * @param level Security level
     * @param message Message to sign
     * @param secretKey Secret signing key
     * @return Detached signature bytes
     * @throws KazSignException if signing fails
     */
    @JvmStatic
    external fun nativeSignDetached(level: Int, message: ByteArray, secretKey: ByteArray): ByteArray

    /**
     * Verify a detached signature.
     *
     * @param level Security level
     * @param message Original message
     * @param signature Detached signature
     * @param publicKey Public verification key
     * @return true if the signature is valid, false otherwise
     */
    @JvmStatic
    external fun nativeVerifyDetached(level: Int, message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

    // ========================================================================
    // SHA3-256 API
    // ========================================================================

    /**
     * Compute SHA3-256 hash of data.
     *
     * @param data Data to hash
     * @return 32-byte SHA3-256 hash
     * @throws KazSignException if hashing fails
     */
    @JvmStatic
    external fun nativeSha3_256(data: ByteArray): ByteArray

    // ========================================================================
    // DER Key Encoding API
    // ========================================================================

    /**
     * Encode a public key to DER format.
     *
     * @param level Security level
     * @param publicKey Raw public key
     * @return DER-encoded public key
     * @throws KazSignException if encoding fails
     */
    @JvmStatic
    external fun nativePublicKeyToDer(level: Int, publicKey: ByteArray): ByteArray

    /**
     * Decode a public key from DER format.
     *
     * @param level Security level
     * @param der DER-encoded public key
     * @return Raw public key
     * @throws KazSignException if decoding fails
     */
    @JvmStatic
    external fun nativePublicKeyFromDer(level: Int, der: ByteArray): ByteArray

    /**
     * Encode a private key to DER format.
     *
     * @param level Security level
     * @param secretKey Raw secret key
     * @return DER-encoded private key
     * @throws KazSignException if encoding fails
     */
    @JvmStatic
    external fun nativePrivateKeyToDer(level: Int, secretKey: ByteArray): ByteArray

    /**
     * Decode a private key from DER format.
     *
     * @param level Security level
     * @param der DER-encoded private key
     * @return Raw secret key
     * @throws KazSignException if decoding fails
     */
    @JvmStatic
    external fun nativePrivateKeyFromDer(level: Int, der: ByteArray): ByteArray

    // ========================================================================
    // X.509 Certificate API
    // ========================================================================

    /**
     * Generate a PKCS#10 Certificate Signing Request (CSR).
     *
     * @param level Security level
     * @param secretKey Secret signing key
     * @param publicKey Public key
     * @param subject Subject distinguished name (e.g., "CN=test")
     * @return DER-encoded CSR
     * @throws KazSignException if CSR generation fails
     */
    @JvmStatic
    external fun nativeGenerateCsr(level: Int, secretKey: ByteArray, publicKey: ByteArray, subject: String): ByteArray

    /**
     * Issue an X.509 certificate by signing a CSR.
     *
     * @param level Security level
     * @param issuerSk Issuer secret key
     * @param issuerPk Issuer public key
     * @param issuerName Issuer distinguished name
     * @param csr DER-encoded CSR
     * @param serial Certificate serial number
     * @param days Validity period in days
     * @return DER-encoded certificate
     * @throws KazSignException if certificate issuance fails
     */
    @JvmStatic
    external fun nativeIssueCertificate(level: Int, issuerSk: ByteArray, issuerPk: ByteArray,
                                        issuerName: String, csr: ByteArray,
                                        serial: Long, days: Int): ByteArray

    /**
     * Verify an X.509 certificate signature against an issuer public key.
     *
     * @param level Security level
     * @param cert DER-encoded certificate
     * @param issuerPk Issuer public key
     * @return true if the certificate is valid, false otherwise
     */
    @JvmStatic
    external fun nativeVerifyCertificate(level: Int, cert: ByteArray, issuerPk: ByteArray): Boolean

    /**
     * Extract the public key from an X.509 certificate.
     *
     * @param level Security level
     * @param cert DER-encoded certificate
     * @return Extracted public key
     * @throws KazSignException if extraction fails
     */
    @JvmStatic
    external fun nativeExtractPublicKey(level: Int, cert: ByteArray): ByteArray

    // ========================================================================
    // PKCS#12 Keystore API
    // ========================================================================

    /**
     * Create a PKCS#12 keystore.
     *
     * @param level Security level
     * @param secretKey Secret key
     * @param publicKey Public key
     * @param cert DER-encoded certificate (may be null)
     * @param password Password to protect the keystore
     * @param name Friendly name for the key entry
     * @return PKCS#12 data
     * @throws KazSignException if creation fails
     */
    @JvmStatic
    external fun nativeCreateP12(level: Int, secretKey: ByteArray, publicKey: ByteArray,
                                  cert: ByteArray?, password: String, name: String): ByteArray

    /**
     * Load a key pair and certificate from a PKCS#12 keystore.
     *
     * @param level Security level
     * @param p12Data PKCS#12 data
     * @param password Password to unlock the keystore
     * @return P12Contents with secret key, public key, and optional certificate
     * @throws KazSignException if loading fails
     */
    @JvmStatic
    external fun nativeLoadP12(level: Int, p12Data: ByteArray, password: String): P12Contents
}
