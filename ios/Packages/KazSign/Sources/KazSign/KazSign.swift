/*
 * KAZ-SIGN Swift Wrapper
 * Version 4.0.0
 *
 * Post-quantum digital signature library for iOS and macOS.
 * Supports security levels 128, 192, and 256 at runtime.
 *
 * Usage:
 *   let signer = try KazSigner(level: .level128)
 *   let keyPair = try signer.generateKeyPair()
 *   let signature = try signer.sign(message: data, secretKey: keyPair.secretKey)
 *   let isValid = try signer.verify(signature: signature, publicKey: keyPair.publicKey)
 */

import Foundation
import KazSignNative

// MARK: - Security Level

/// Security level for KAZ-SIGN operations
/// Note: Size constants must be kept in sync with include/kaz/sign.h
public enum SecurityLevel: Int, CaseIterable, Sendable {
    /// 128-bit security (SHA-256)
    case level128 = 128
    /// 192-bit security (SHA-384)
    case level192 = 192
    /// 256-bit security (SHA-512)
    case level256 = 256

    /// Secret key size in bytes (s || t)
    public var secretKeyBytes: Int {
        switch self {
        case .level128: return 32   // s(16) + t(16)
        case .level192: return 50   // s(25) + t(25)
        case .level256: return 64   // s(32) + t(32)
        }
    }

    /// Public key size in bytes (v)
    public var publicKeyBytes: Int {
        switch self {
        case .level128: return 54
        case .level192: return 88
        case .level256: return 118
        }
    }

    /// Signature overhead in bytes (excluding message)
    /// NOTE: These values must match KAZ_SIGN_SIGNATURE_OVERHEAD in kaz/sign.h
    /// Signature = S1 || S2 || S3 (3 equal-size components)
    public var signatureOverhead: Int {
        switch self {
        case .level128: return 162  // 3 * 54
        case .level192: return 264  // 3 * 88
        case .level256: return 354  // 3 * 118
        }
    }

    /// Hash output size in bytes
    public var hashBytes: Int {
        switch self {
        case .level128: return 32  // SHA-256
        case .level192: return 48  // SHA-384
        case .level256: return 64  // SHA-512
        }
    }

    /// Algorithm name
    public var algorithmName: String {
        "KAZ-SIGN-\(rawValue)"
    }
}

// MARK: - Error Types

/// Errors that can occur during KAZ-SIGN operations
public enum KazSignError: Error, LocalizedError, Sendable, Equatable {
    case memoryAllocationFailed
    case randomGenerationFailed
    case invalidParameter
    case verificationFailed
    case notInitialized
    case invalidKeySize
    case invalidSignatureSize
    case derEncodingFailed
    case x509OperationFailed
    case p12OperationFailed
    case hashFailed
    case bufferTooSmall
    case unknownError(Int32)

    public var errorDescription: String? {
        switch self {
        case .memoryAllocationFailed:
            return "Memory allocation failed"
        case .randomGenerationFailed:
            return "Random number generation failed"
        case .invalidParameter:
            return "Invalid parameter"
        case .verificationFailed:
            return "Signature verification failed"
        case .notInitialized:
            return "Signer not initialized"
        case .invalidKeySize:
            return "Invalid key size"
        case .invalidSignatureSize:
            return "Invalid signature size"
        case .derEncodingFailed:
            return "DER encoding/decoding failed"
        case .x509OperationFailed:
            return "X.509 certificate operation failed"
        case .p12OperationFailed:
            return "PKCS#12 operation failed"
        case .hashFailed:
            return "Hash operation failed"
        case .bufferTooSmall:
            return "Buffer too small"
        case .unknownError(let code):
            return "Unknown error (code: \(code))"
        }
    }

    init(code: Int32) {
        switch code {
        case -1: self = .memoryAllocationFailed
        case -2: self = .randomGenerationFailed
        case -3: self = .invalidParameter
        case -4: self = .verificationFailed
        case -5: self = .derEncodingFailed
        case -6: self = .x509OperationFailed
        case -7: self = .p12OperationFailed
        case -8: self = .hashFailed
        case -9: self = .bufferTooSmall
        default: self = .unknownError(code)
        }
    }
}

// MARK: - Key Pair

/// A KAZ-SIGN key pair containing public and secret keys
public struct KeyPair: Sendable {
    /// Public verification key
    public let publicKey: Data
    /// Secret signing key
    public let secretKey: Data
    /// Security level
    public let level: SecurityLevel

    init(publicKey: Data, secretKey: Data, level: SecurityLevel) {
        self.publicKey = publicKey
        self.secretKey = secretKey
        self.level = level
    }
}

// MARK: - Signature Result

/// Result of a signing operation
public struct SignatureResult: Sendable {
    /// The signature (includes the message)
    public let signature: Data
    /// The original message
    public let message: Data
    /// Security level used
    public let level: SecurityLevel

    /// Signature overhead (signature bytes without message)
    public var overhead: Int {
        signature.count - message.count
    }
}

// MARK: - Verification Result

/// Result of a verification operation
public struct VerificationResult: Sendable {
    /// Whether the signature is valid
    public let isValid: Bool
    /// The recovered message (if valid)
    public let message: Data?
    /// Security level used
    public let level: SecurityLevel
}

// MARK: - P12 Contents

/// Contents extracted from a PKCS#12 keystore
public struct P12Contents: Sendable {
    /// Secret signing key
    public let secretKey: Data
    /// Public verification key
    public let publicKey: Data
    /// DER-encoded certificate
    public let certificate: Data
    /// Certificate chain (empty if no chain present)
    public let chain: [Data]
}

// MARK: - KazSigner

/// Main class for KAZ-SIGN cryptographic operations
public final class KazSigner: @unchecked Sendable {
    /// The security level being used
    public let level: SecurityLevel

    private let lock = NSLock()
    private var isInitialized = false

    /// Library version string
    public static var version: String {
        String(cString: kaz_sign_version())
    }

    /// Library version number
    public static var versionNumber: Int {
        Int(kaz_sign_version_number())
    }

    /// Create a new KazSigner with the specified security level
    /// - Parameter level: Security level (128, 192, or 256)
    /// - Throws: KazSignError if initialization fails
    public init(level: SecurityLevel) throws {
        self.level = level
        try initialize()
    }

    deinit {
        cleanup()
    }

    // MARK: - Initialization

    /// C level enum value for the current security level
    private var cLevel: Int32 {
        Int32(level.rawValue)
    }

    private func initialize() throws {
        lock.lock()
        defer { lock.unlock() }

        guard !isInitialized else { return }

        // Initialize the specific security level using runtime API
        let result = kaz_sign_init_level(kaz_sign_level_t(rawValue: UInt32(level.rawValue)))
        guard result == 0 else {
            throw KazSignError(code: result)
        }

        isInitialized = true
    }

    private func cleanup() {
        lock.lock()
        defer { lock.unlock() }

        if isInitialized {
            kaz_sign_clear_level(kaz_sign_level_t(rawValue: UInt32(level.rawValue)))
            isInitialized = false
        }
    }

    private func ensureInitialized() throws {
        guard isInitialized else {
            throw KazSignError.notInitialized
        }
    }

    // MARK: - Key Generation

    /// Generate a new key pair
    /// - Returns: A KeyPair containing the public and secret keys
    /// - Throws: KazSignError if key generation fails
    public func generateKeyPair() throws -> KeyPair {
        try ensureInitialized()

        var publicKey = Data(count: level.publicKeyBytes)
        var secretKey = Data(count: level.secretKeyBytes)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = publicKey.withUnsafeMutableBytes { pkPtr in
            secretKey.withUnsafeMutableBytes { skPtr in
                kaz_sign_keypair_ex(
                    cLevelValue,
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return KeyPair(publicKey: publicKey, secretKey: secretKey, level: level)
    }

    // MARK: - Signing

    /// Sign a message
    /// - Parameters:
    ///   - message: The message to sign
    ///   - secretKey: The secret signing key
    /// - Returns: SignatureResult containing the signature
    /// - Throws: KazSignError if signing fails
    public func sign(message: Data, secretKey: Data) throws -> SignatureResult {
        try ensureInitialized()

        guard secretKey.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        var signature = Data(count: level.signatureOverhead + message.count)
        var signatureLength: UInt64 = 0

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = signature.withUnsafeMutableBytes { sigPtr in
            message.withUnsafeBytes { msgPtr in
                secretKey.withUnsafeBytes { skPtr in
                    kaz_sign_signature_ex(
                        cLevelValue,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        &signatureLength,
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt64(message.count),
                        skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        // Trim to actual length
        signature = signature.prefix(Int(signatureLength))

        return SignatureResult(signature: signature, message: message, level: level)
    }

    /// Sign a string message
    /// - Parameters:
    ///   - message: The string message to sign
    ///   - secretKey: The secret signing key
    /// - Returns: SignatureResult containing the signature
    /// - Throws: KazSignError if signing fails
    public func sign(message: String, secretKey: Data) throws -> SignatureResult {
        guard let messageData = message.data(using: .utf8) else {
            throw KazSignError.invalidParameter
        }
        return try sign(message: messageData, secretKey: secretKey)
    }

    // MARK: - Verification

    /// Verify a signature and extract the message
    /// - Parameters:
    ///   - signature: The signature to verify (includes the message)
    ///   - publicKey: The public verification key
    /// - Returns: VerificationResult with validity and recovered message
    public func verify(signature: Data, publicKey: Data) throws -> VerificationResult {
        try ensureInitialized()

        guard publicKey.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        guard signature.count >= level.signatureOverhead else {
            return VerificationResult(isValid: false, message: nil, level: level)
        }

        let maxMessageLength = signature.count - level.signatureOverhead
        var message = Data(count: maxMessageLength)
        var messageLength: UInt64 = 0

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = message.withUnsafeMutableBytes { msgPtr in
            signature.withUnsafeBytes { sigPtr in
                publicKey.withUnsafeBytes { pkPtr in
                    kaz_sign_verify_ex(
                        cLevelValue,
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        &messageLength,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt64(signature.count),
                        pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        if result == 0 {
            message = message.prefix(Int(messageLength))
            return VerificationResult(isValid: true, message: message, level: level)
        } else {
            return VerificationResult(isValid: false, message: nil, level: level)
        }
    }

    /// Verify a signature and extract the message as a string
    /// - Parameters:
    ///   - signature: The signature to verify
    ///   - publicKey: The public verification key
    /// - Returns: Tuple of (isValid, recoveredString)
    public func verifyString(signature: Data, publicKey: Data) throws -> (isValid: Bool, message: String?) {
        let result = try verify(signature: signature, publicKey: publicKey)
        let messageString = result.message.flatMap { String(data: $0, encoding: .utf8) }
        return (result.isValid, messageString)
    }

    // MARK: - Hashing

    /// Hash a message using the appropriate hash function for this security level
    /// - Parameter message: The message to hash
    /// - Returns: The hash value
    /// - Throws: KazSignError if hashing fails
    public func hash(message: Data) throws -> Data {
        var hashOutput = Data(count: level.hashBytes)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = hashOutput.withUnsafeMutableBytes { hashPtr in
            message.withUnsafeBytes { msgPtr in
                kaz_sign_hash_ex(
                    cLevelValue,
                    msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(message.count),
                    hashPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return hashOutput
    }

    /// Hash a string message
    /// - Parameter message: The string message to hash
    /// - Returns: The hash value
    /// - Throws: KazSignError if hashing fails
    public func hash(message: String) throws -> Data {
        guard let messageData = message.data(using: .utf8) else {
            throw KazSignError.invalidParameter
        }
        return try hash(message: messageData)
    }

    // MARK: - Detached Signatures

    /// Create a detached signature (signature does not include the message)
    /// - Parameters:
    ///   - data: The data to sign
    ///   - secretKey: The secret signing key
    /// - Returns: The detached signature
    /// - Throws: KazSignError if signing fails
    public func signDetached(data: Data, secretKey: Data) throws -> Data {
        try ensureInitialized()

        guard secretKey.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        let sigBytes = kaz_sign_detached_sig_bytes(kaz_sign_level_t(rawValue: UInt32(level.rawValue)))
        var signature = Data(count: sigBytes)
        var signatureLength: UInt64 = 0

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = signature.withUnsafeMutableBytes { sigPtr in
            data.withUnsafeBytes { msgPtr in
                secretKey.withUnsafeBytes { skPtr in
                    kaz_sign_detached_ex(
                        cLevelValue,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        &signatureLength,
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt64(data.count),
                        skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return signature.prefix(Int(signatureLength))
    }

    /// Verify a detached signature
    /// - Parameters:
    ///   - data: The original data that was signed
    ///   - signature: The detached signature to verify
    ///   - publicKey: The public verification key
    /// - Returns: true if the signature is valid
    /// - Throws: KazSignError if the public key is invalid
    public func verifyDetached(data: Data, signature: Data, publicKey: Data) throws -> Bool {
        try ensureInitialized()

        guard publicKey.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = signature.withUnsafeBytes { sigPtr in
            data.withUnsafeBytes { msgPtr in
                publicKey.withUnsafeBytes { pkPtr in
                    kaz_sign_verify_detached_ex(
                        cLevelValue,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt64(signature.count),
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt64(data.count),
                        pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        return result == 0
    }

    // MARK: - SHA-256

    /// Compute SHA-256 hash of data (static, always available)
    /// - Parameter data: The data to hash
    /// - Returns: 32-byte SHA-256 digest
    public static func sha3_256(_ data: Data) -> Data {
        var output = Data(count: 32)

        let result = output.withUnsafeMutableBytes { outPtr in
            data.withUnsafeBytes { msgPtr in
                kaz_sha3_256(
                    msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(data.count),
                    outPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        // SHA-256 should not fail under normal conditions; return empty data on error
        if result != 0 {
            return Data()
        }

        return output
    }

    // MARK: - DER Key Encoding

    /// Encode a public key to DER format
    /// - Parameter publicKey: The raw public key
    /// - Returns: DER-encoded public key
    /// - Throws: KazSignError if encoding fails
    public func publicKeyToDer(_ publicKey: Data) throws -> Data {
        try ensureInitialized()

        guard publicKey.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        // Allocate a generous buffer for DER output
        let maxDerSize = level.publicKeyBytes + 128
        var der = Data(count: maxDerSize)
        var derLength: UInt64 = UInt64(maxDerSize)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = der.withUnsafeMutableBytes { derPtr in
            publicKey.withUnsafeBytes { pkPtr in
                kaz_sign_pubkey_to_der(
                    cLevelValue,
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    derPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    &derLength
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return der.prefix(Int(derLength))
    }

    /// Decode a public key from DER format
    /// - Parameter der: The DER-encoded public key
    /// - Returns: The raw public key
    /// - Throws: KazSignError if decoding fails
    public func publicKeyFromDer(_ der: Data) throws -> Data {
        try ensureInitialized()

        var publicKey = Data(count: level.publicKeyBytes)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = publicKey.withUnsafeMutableBytes { pkPtr in
            der.withUnsafeBytes { derPtr in
                kaz_sign_pubkey_from_der(
                    cLevelValue,
                    derPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(der.count),
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return publicKey
    }

    /// Encode a private key to DER format
    /// - Parameter secretKey: The raw secret key
    /// - Returns: DER-encoded private key
    /// - Throws: KazSignError if encoding fails
    public func privateKeyToDer(_ secretKey: Data) throws -> Data {
        try ensureInitialized()

        guard secretKey.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        // Allocate a generous buffer for DER output
        let maxDerSize = level.secretKeyBytes + 128
        var der = Data(count: maxDerSize)
        var derLength: UInt64 = UInt64(maxDerSize)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = der.withUnsafeMutableBytes { derPtr in
            secretKey.withUnsafeBytes { skPtr in
                kaz_sign_privkey_to_der(
                    cLevelValue,
                    skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    derPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    &derLength
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return der.prefix(Int(derLength))
    }

    /// Decode a private key from DER format
    /// - Parameter der: The DER-encoded private key
    /// - Returns: The raw secret key
    /// - Throws: KazSignError if decoding fails
    public func privateKeyFromDer(_ der: Data) throws -> Data {
        try ensureInitialized()

        var secretKey = Data(count: level.secretKeyBytes)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = secretKey.withUnsafeMutableBytes { skPtr in
            der.withUnsafeBytes { derPtr in
                kaz_sign_privkey_from_der(
                    cLevelValue,
                    derPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(der.count),
                    skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return secretKey
    }

    // MARK: - X.509 Certificates

    /// Generate a PKCS#10 Certificate Signing Request (CSR)
    /// - Parameters:
    ///   - secretKey: The secret signing key
    ///   - publicKey: The public key
    ///   - cn: Common Name for the subject
    ///   - org: Organization name (optional)
    ///   - ou: Organizational Unit name (optional)
    /// - Returns: DER-encoded CSR
    /// - Throws: KazSignError if CSR generation fails
    public func generateCSR(secretKey: Data, publicKey: Data, cn: String, org: String?, ou: String?) throws -> Data {
        try ensureInitialized()

        guard secretKey.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        guard publicKey.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        // Build the subject distinguished name string
        var parts = ["CN=\(cn)"]
        if let org = org {
            parts.append("O=\(org)")
        }
        if let ou = ou {
            parts.append("OU=\(ou)")
        }
        let subject = parts.joined(separator: "/")

        let maxCsrSize = 4096
        var csr = Data(count: maxCsrSize)
        var csrLength: UInt64 = UInt64(maxCsrSize)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = csr.withUnsafeMutableBytes { csrPtr in
            secretKey.withUnsafeBytes { skPtr in
                publicKey.withUnsafeBytes { pkPtr in
                    kaz_sign_generate_csr(
                        cLevelValue,
                        skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        subject,
                        csrPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        &csrLength
                    )
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return csr.prefix(Int(csrLength))
    }

    /// Issue an X.509 certificate by signing a CSR
    /// - Parameters:
    ///   - issuerSK: Issuer's secret signing key
    ///   - issuerPK: Issuer's public key
    ///   - issuerName: Issuer's distinguished name string
    ///   - csr: DER-encoded CSR from the subject
    ///   - serial: Certificate serial number
    ///   - notBefore: Certificate validity start date
    ///   - notAfter: Certificate validity end date
    ///   - isCA: Whether the certificate is for a CA (reserved for future use)
    /// - Returns: DER-encoded X.509 certificate
    /// - Throws: KazSignError if certificate issuance fails
    public func issueCertificate(issuerSK: Data, issuerPK: Data, issuerName: String, csr: Data, serial: Data, notBefore: Date, notAfter: Date, isCA: Bool) throws -> Data {
        try ensureInitialized()

        guard issuerSK.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        guard issuerPK.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        // Convert serial Data to UInt64
        var serialValue: UInt64 = 0
        let serialBytes = serial.prefix(8)
        for byte in serialBytes {
            serialValue = (serialValue << 8) | UInt64(byte)
        }

        // Calculate validity period in days from date range
        let days = Int(notAfter.timeIntervalSince(notBefore) / 86400.0)
        guard days > 0 else {
            throw KazSignError.invalidParameter
        }

        let maxCertSize = 8192
        var cert = Data(count: maxCertSize)
        var certLength: UInt64 = UInt64(maxCertSize)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = cert.withUnsafeMutableBytes { certPtr in
            issuerSK.withUnsafeBytes { skPtr in
                issuerPK.withUnsafeBytes { pkPtr in
                    csr.withUnsafeBytes { csrPtr in
                        kaz_sign_issue_certificate(
                            cLevelValue,
                            skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            issuerName,
                            csrPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            UInt64(csr.count),
                            serialValue,
                            Int32(days),
                            certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            &certLength
                        )
                    }
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return cert.prefix(Int(certLength))
    }

    /// Verify an X.509 certificate signature against an issuer public key
    /// - Parameters:
    ///   - cert: DER-encoded certificate to verify
    ///   - issuerPublicKey: The issuer's public verification key
    /// - Returns: true if the certificate signature is valid
    /// - Throws: KazSignError if the key is invalid
    public func verifyCertificate(_ cert: Data, issuerPublicKey: Data) throws -> Bool {
        try ensureInitialized()

        guard issuerPublicKey.count == level.publicKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = cert.withUnsafeBytes { certPtr in
            issuerPublicKey.withUnsafeBytes { pkPtr in
                kaz_sign_verify_certificate(
                    cLevelValue,
                    certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(cert.count),
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        return result == 0
    }

    /// Extract the public key from an X.509 certificate
    /// - Parameter cert: DER-encoded certificate
    /// - Returns: The raw public key extracted from the certificate
    /// - Throws: KazSignError if extraction fails
    public func extractPublicKey(from cert: Data) throws -> Data {
        try ensureInitialized()

        var publicKey = Data(count: level.publicKeyBytes)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = publicKey.withUnsafeMutableBytes { pkPtr in
            cert.withUnsafeBytes { certPtr in
                kaz_sign_cert_extract_pubkey(
                    cLevelValue,
                    certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(cert.count),
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return publicKey
    }

    // MARK: - PKCS#12 Keystore

    /// Create a PKCS#12 keystore containing a key pair and certificate
    /// - Parameters:
    ///   - secretKey: The secret signing key
    ///   - certificate: DER-encoded certificate
    ///   - chain: Additional certificates in the chain (reserved for future use)
    ///   - password: Password to protect the keystore
    ///   - name: Friendly name for the key entry
    /// - Returns: PKCS#12 data
    /// - Throws: KazSignError if creation fails
    public func createP12(secretKey: Data, certificate: Data, chain: [Data]?, password: String, name: String) throws -> Data {
        try ensureInitialized()

        guard secretKey.count == level.secretKeyBytes else {
            throw KazSignError.invalidKeySize
        }

        // Extract public key from the certificate for the C API
        var publicKey = Data(count: level.publicKeyBytes)
        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))

        let extractResult = publicKey.withUnsafeMutableBytes { pkPtr in
            certificate.withUnsafeBytes { certPtr in
                kaz_sign_cert_extract_pubkey(
                    cLevelValue,
                    certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt64(certificate.count),
                    pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard extractResult == 0 else {
            throw KazSignError(code: extractResult)
        }

        let maxP12Size = 16384
        var p12 = Data(count: maxP12Size)
        var p12Length: UInt64 = UInt64(maxP12Size)

        let result = p12.withUnsafeMutableBytes { p12Ptr in
            secretKey.withUnsafeBytes { skPtr in
                publicKey.withUnsafeBytes { pkPtr in
                    certificate.withUnsafeBytes { certPtr in
                        kaz_sign_create_p12(
                            cLevelValue,
                            skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            UInt64(certificate.count),
                            password,
                            name,
                            p12Ptr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            &p12Length
                        )
                    }
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        return p12.prefix(Int(p12Length))
    }

    /// Load a key pair and certificate from a PKCS#12 keystore
    /// - Parameters:
    ///   - p12: PKCS#12 data
    ///   - password: Password to unlock the keystore
    /// - Returns: P12Contents containing the secret key, certificate, and chain
    /// - Throws: KazSignError if loading fails
    public func loadP12(_ p12: Data, password: String) throws -> P12Contents {
        try ensureInitialized()

        var secretKey = Data(count: level.secretKeyBytes)
        var publicKey = Data(count: level.publicKeyBytes)
        let maxCertSize = 8192
        var certificate = Data(count: maxCertSize)
        var certLength: UInt64 = UInt64(maxCertSize)

        let cLevelValue = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        let result = secretKey.withUnsafeMutableBytes { skPtr in
            publicKey.withUnsafeMutableBytes { pkPtr in
                certificate.withUnsafeMutableBytes { certPtr in
                    p12.withUnsafeBytes { p12Ptr in
                        kaz_sign_load_p12(
                            cLevelValue,
                            p12Ptr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            UInt64(p12.count),
                            password,
                            skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            certPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            &certLength
                        )
                    }
                }
            }
        }

        guard result == 0 else {
            throw KazSignError(code: result)
        }

        certificate = certificate.prefix(Int(certLength))

        return P12Contents(
            secretKey: secretKey,
            publicKey: publicKey,
            certificate: certificate,
            chain: []
        )
    }
}

// MARK: - Convenience Extensions

extension Data {
    /// Convert data to hexadecimal string
    public var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }

    /// Initialize Data from hexadecimal string
    public init?(hexString: String) {
        guard hexString.count % 2 == 0 else { return nil }
        let len = hexString.count / 2
        var data = Data(capacity: len)
        var index = hexString.startIndex

        for _ in 0..<len {
            let nextIndex = hexString.index(index, offsetBy: 2)
            guard let byte = UInt8(hexString[index..<nextIndex], radix: 16) else {
                return nil
            }
            data.append(byte)
            index = nextIndex
        }

        self = data
    }
}
