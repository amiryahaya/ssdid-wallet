import Foundation
import CryptoKit

/// X25519 key agreement provider using CryptoKit Curve25519.
/// Generates ephemeral key pairs and derives shared secrets via ECDH.
final class X25519Provider {

    /// Generates a new X25519 key pair for key agreement.
    func generateKeyPair() -> KeyPairResult {
        let privateKey = Curve25519.KeyAgreement.PrivateKey()
        return KeyPairResult(
            publicKey: privateKey.publicKey.rawRepresentation,
            privateKey: privateKey.rawRepresentation
        )
    }

    /// Derives a shared secret from a local private key and a remote public key
    /// using X25519 Diffie-Hellman key agreement.
    func deriveSharedSecret(privateKey: Data, peerPublicKey: Data) throws -> Data {
        let privKey = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: privateKey)
        let pubKey = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerPublicKey)
        let shared = try privKey.sharedSecretFromKeyAgreement(with: pubKey)
        return shared.withUnsafeBytes { Data($0) }
    }
}
