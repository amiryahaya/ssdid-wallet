import Foundation
import CryptoKit
import Security

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
            // Use x963Representation (0x04 || x || y) for interop with registry/Android
            return KeyPairResult(
                publicKey: privateKey.publicKey.x963Representation,
                privateKey: privateKey.rawRepresentation
            )

        case .ECDSA_P384:
            let privateKey = P384.Signing.PrivateKey()
            // Use x963Representation (0x04 || x || y) for interop with registry/Android
            return KeyPairResult(
                publicKey: privateKey.publicKey.x963Representation,
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
            return try signEcdsaDer(privateKey: privateKey, data: data, keySizeInBits: 256)

        case .ECDSA_P384:
            return try signEcdsaDer(privateKey: privateKey, data: data, keySizeInBits: 384)

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
            return try verifyEcdsaDer(publicKey: publicKey, signature: signature, data: data, keySizeInBits: 256)

        case .ECDSA_P384:
            return try verifyEcdsaDer(publicKey: publicKey, signature: signature, data: data, keySizeInBits: 384)

        default:
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }
    }

    // MARK: - ECDSA SHA-512 DER helpers (Security framework)

    private func signEcdsaDer(privateKey: Data, data: Data, keySizeInBits: Int) throws -> Data {
        // Build SecKey from raw private key using CryptoKit as intermediate
        let fullKeyData: Data
        if keySizeInBits == 256 {
            let ck = try P256.Signing.PrivateKey(rawRepresentation: privateKey)
            fullKeyData = ck.x963Representation
        } else {
            let ck = try P384.Signing.PrivateKey(rawRepresentation: privateKey)
            fullKeyData = ck.x963Representation
        }

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeyClass as String: kSecAttrKeyClassPrivate,
            kSecAttrKeySizeInBits as String: keySizeInBits
        ]
        var error: Unmanaged<CFError>?
        guard let secKey = SecKeyCreateWithData(fullKeyData as CFData, attributes as CFDictionary, &error) else {
            throw CryptoError.signingFailed("Failed to create SecKey: \(error!.takeRetainedValue())")
        }

        let algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA512
        guard SecKeyIsAlgorithmSupported(secKey, .sign, algorithm) else {
            throw CryptoError.unsupportedAlgorithm(keySizeInBits == 256 ? .ECDSA_P256 : .ECDSA_P384)
        }
        guard let signature = SecKeyCreateSignature(secKey, algorithm, data as CFData, &error) else {
            throw CryptoError.signingFailed("ECDSA sign failed: \(error!.takeRetainedValue())")
        }
        return signature as Data
    }

    private func verifyEcdsaDer(publicKey: Data, signature: Data, data: Data, keySizeInBits: Int) throws -> Bool {
        // Public key should be in uncompressed point format (0x04 || x || y)
        let pubKeyData: Data
        if publicKey.first == 0x04 {
            pubKeyData = publicKey
        } else {
            // Try wrapping raw key bytes
            pubKeyData = Data([0x04]) + publicKey
        }

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String: keySizeInBits
        ]
        var error: Unmanaged<CFError>?
        guard let secKey = SecKeyCreateWithData(pubKeyData as CFData, attributes as CFDictionary, &error) else {
            throw CryptoError.verificationFailed("Failed to create public SecKey: \(error!.takeRetainedValue())")
        }

        let algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA512
        return SecKeyVerifySignature(secKey, algorithm, data as CFData, signature as CFData, &error)
    }
}
