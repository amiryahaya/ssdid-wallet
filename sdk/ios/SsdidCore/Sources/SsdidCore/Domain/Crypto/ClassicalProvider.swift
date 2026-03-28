import Foundation
import CryptoKit

/// CryptoProvider implementation for classical (non-PQC) algorithms
/// using Apple CryptoKit: Ed25519, ECDSA P-256, ECDSA P-384.
public final class ClassicalProvider: CryptoProvider {

    public     func supportsAlgorithm(_ algorithm: Algorithm) -> Bool {
        switch algorithm {
        case .ED25519, .ECDSA_P256, .ECDSA_P384:
            return true
        default:
            return false
        }
    }

    public     func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        switch algorithm {
        case .ED25519:
            let privateKey = Curve25519.Signing.PrivateKey()
            return KeyPairResult(
                publicKey: privateKey.publicKey.rawRepresentation,
                privateKey: privateKey.rawRepresentation
            )

        case .ECDSA_P256:
            let privateKey = P256.Signing.PrivateKey()
            return KeyPairResult(
                publicKey: privateKey.publicKey.rawRepresentation,
                privateKey: privateKey.rawRepresentation
            )

        case .ECDSA_P384:
            let privateKey = P384.Signing.PrivateKey()
            return KeyPairResult(
                publicKey: privateKey.publicKey.rawRepresentation,
                privateKey: privateKey.rawRepresentation
            )

        default:
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }
    }

    public     func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        switch algorithm {
        case .ED25519:
            let key = try Curve25519.Signing.PrivateKey(rawRepresentation: privateKey)
            let signature = try key.signature(for: data)
            return signature

        case .ECDSA_P256:
            let key = try P256.Signing.PrivateKey(rawRepresentation: privateKey)
            let signature = try key.signature(for: data)
            return signature.rawRepresentation

        case .ECDSA_P384:
            let key = try P384.Signing.PrivateKey(rawRepresentation: privateKey)
            let signature = try key.signature(for: data)
            return signature.rawRepresentation

        default:
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }
    }

    public     func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        switch algorithm {
        case .ED25519:
            let key = try Curve25519.Signing.PublicKey(rawRepresentation: publicKey)
            return key.isValidSignature(signature, for: data)

        case .ECDSA_P256:
            let key: P256.Signing.PublicKey
            if publicKey.count == 33 {
                key = try P256.Signing.PublicKey(compressedRepresentation: publicKey)
            } else {
                key = try P256.Signing.PublicKey(rawRepresentation: publicKey)
            }
            let ecdsaSig = try P256.Signing.ECDSASignature(rawRepresentation: signature)
            return key.isValidSignature(ecdsaSig, for: data)

        case .ECDSA_P384:
            let key: P384.Signing.PublicKey
            if publicKey.count == 49 {
                key = try P384.Signing.PublicKey(compressedRepresentation: publicKey)
            } else {
                key = try P384.Signing.PublicKey(rawRepresentation: publicKey)
            }
            let ecdsaSig = try P384.Signing.ECDSASignature(rawRepresentation: signature)
            return key.isValidSignature(ecdsaSig, for: data)

        default:
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }
    }
}
