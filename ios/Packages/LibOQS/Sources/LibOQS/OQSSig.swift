import Foundation
import LibOQSNative

/// Errors from OQS signature operations.
public enum OQSError: Error, LocalizedError {
    case algorithmNotEnabled(String)
    case keyGenerationFailed
    case signingFailed
    case verificationFailed
    case invalidKeySize

    public var errorDescription: String? {
        switch self {
        case .algorithmNotEnabled(let name): return "OQS algorithm not enabled: \(name)"
        case .keyGenerationFailed: return "OQS key generation failed"
        case .signingFailed: return "OQS signing failed"
        case .verificationFailed: return "OQS signature verification failed"
        case .invalidKeySize: return "OQS invalid key size"
        }
    }
}

/// Swift wrapper around liboqs OQS_SIG API.
public final class OQSSig: @unchecked Sendable {

    /// Algorithm identifiers matching liboqs 0.15.0 names.
    public enum Algorithm: String, CaseIterable, Sendable {
        case mlDsa44 = "ML-DSA-44"
        case mlDsa65 = "ML-DSA-65"
        case mlDsa87 = "ML-DSA-87"
        case slhDsaSha2_128s = "SLH_DSA_PURE_SHA2_128S"
        case slhDsaSha2_128f = "SLH_DSA_PURE_SHA2_128F"
        case slhDsaSha2_192s = "SLH_DSA_PURE_SHA2_192S"
        case slhDsaSha2_192f = "SLH_DSA_PURE_SHA2_192F"
        case slhDsaSha2_256s = "SLH_DSA_PURE_SHA2_256S"
        case slhDsaSha2_256f = "SLH_DSA_PURE_SHA2_256F"
        case slhDsaShake_128s = "SLH_DSA_PURE_SHAKE_128S"
        case slhDsaShake_128f = "SLH_DSA_PURE_SHAKE_128F"
        case slhDsaShake_192s = "SLH_DSA_PURE_SHAKE_192S"
        case slhDsaShake_192f = "SLH_DSA_PURE_SHAKE_192F"
        case slhDsaShake_256s = "SLH_DSA_PURE_SHAKE_256S"
        case slhDsaShake_256f = "SLH_DSA_PURE_SHAKE_256F"
    }

    private let sig: UnsafeMutablePointer<OQS_SIG>

    public let publicKeyLength: Int
    public let secretKeyLength: Int
    public let signatureLength: Int

    public init(algorithm: Algorithm) throws {
        guard let s = OQS_SIG_new(algorithm.rawValue) else {
            throw OQSError.algorithmNotEnabled(algorithm.rawValue)
        }
        self.sig = s
        self.publicKeyLength = Int(s.pointee.length_public_key)
        self.secretKeyLength = Int(s.pointee.length_secret_key)
        self.signatureLength = Int(s.pointee.length_signature)
    }

    deinit {
        OQS_SIG_free(sig)
    }

    /// Generate a new key pair.
    public func generateKeyPair() throws -> (publicKey: Data, secretKey: Data) {
        var publicKey = Data(count: publicKeyLength)
        var secretKey = Data(count: secretKeyLength)

        let rc = publicKey.withUnsafeMutableBytes { pubPtr in
            secretKey.withUnsafeMutableBytes { secPtr in
                OQS_SIG_keypair(
                    sig,
                    pubPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    secPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                )
            }
        }

        guard rc == OQS_SUCCESS else {
            throw OQSError.keyGenerationFailed
        }

        return (publicKey, secretKey)
    }

    /// Sign a message with the given secret key.
    public func sign(message: Data, secretKey: Data) throws -> Data {
        guard secretKey.count == secretKeyLength else {
            throw OQSError.invalidKeySize
        }

        var signature = Data(count: signatureLength)
        var signatureLen: Int = 0

        let rc = signature.withUnsafeMutableBytes { sigPtr in
            message.withUnsafeBytes { msgPtr in
                secretKey.withUnsafeBytes { skPtr in
                    OQS_SIG_sign(
                        sig,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        &signatureLen,
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        message.count,
                        skPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        guard rc == OQS_SUCCESS else {
            throw OQSError.signingFailed
        }

        return signature.prefix(signatureLen)
    }

    /// Verify a signature against a message and public key.
    public func verify(message: Data, signature: Data, publicKey: Data) throws -> Bool {
        guard publicKey.count == publicKeyLength else {
            throw OQSError.invalidKeySize
        }

        let rc = message.withUnsafeBytes { msgPtr in
            signature.withUnsafeBytes { sigPtr in
                publicKey.withUnsafeBytes { pkPtr in
                    OQS_SIG_verify(
                        sig,
                        msgPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        message.count,
                        sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        signature.count,
                        pkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self)
                    )
                }
            }
        }

        return rc == OQS_SUCCESS
    }
}
