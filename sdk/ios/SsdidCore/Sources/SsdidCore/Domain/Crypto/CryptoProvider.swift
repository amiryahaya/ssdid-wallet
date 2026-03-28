import Foundation

/// Error types for cryptographic operations.
public enum CryptoError: Error, LocalizedError {
    case unsupportedAlgorithm(Algorithm)
    case keyGenerationFailed(String)
    case signingFailed(String)
    case verificationFailed(String)
    case invalidKeyFormat(String)

    public var errorDescription: String? {
        switch self {
        case .unsupportedAlgorithm(let algo):
            return "Unsupported algorithm: \(algo.rawValue)"
        case .keyGenerationFailed(let reason):
            return "Key generation failed: \(reason)"
        case .signingFailed(let reason):
            return "Signing failed: \(reason)"
        case .verificationFailed(let reason):
            return "Verification failed: \(reason)"
        case .invalidKeyFormat(let reason):
            return "Invalid key format: \(reason)"
        }
    }
}

/// Result of a key pair generation operation.
public struct KeyPairResult {
    public let publicKey: Data
    public let privateKey: Data

    public init(publicKey: Data, privateKey: Data) {
        self.publicKey = publicKey
        self.privateKey = privateKey
    }
}

/// Protocol for cryptographic operations.
/// Implementations provide key generation, signing, and verification
/// for a set of supported algorithms.
public protocol CryptoProvider {
    /// Returns true if this provider supports the given algorithm.
    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool

    /// Generates a new key pair for the given algorithm.
    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult

    /// Signs data using the given algorithm and private key.
    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data

    /// Verifies a signature against data using the given algorithm and public key.
    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool
}
