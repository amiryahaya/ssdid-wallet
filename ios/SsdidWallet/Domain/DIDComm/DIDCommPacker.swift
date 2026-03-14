import Foundation
import CryptoKit

/// Error types for DIDComm packing/unpacking operations.
enum DIDCommError: Error, LocalizedError {
    case packingFailed(String)
    case unpackingFailed(String)
    case invalidData(String)

    var errorDescription: String? {
        switch self {
        case .packingFailed(let reason):
            return "DIDComm packing failed: \(reason)"
        case .unpackingFailed(let reason):
            return "DIDComm unpacking failed: \(reason)"
        case .invalidData(let reason):
            return "Invalid DIDComm data: \(reason)"
        }
    }
}

/// Packs DIDComm v2 messages using authcrypt (X25519 ECDH + HKDF-SHA256 + AES-256-GCM).
final class DIDCommPacker {

    private let x25519 = X25519Provider()

    /// Encrypts a DIDComm message for the given recipient.
    ///
    /// Wire format: iv (12 bytes) || ciphertext || tag (16 bytes).
    ///
    /// - Parameters:
    ///   - message: The plaintext DIDComm message.
    ///   - senderPrivateKey: Sender's X25519 private key (32 bytes).
    ///   - recipientPublicKey: Recipient's X25519 public key (32 bytes).
    /// - Returns: The encrypted message bytes.
    func pack(
        message: DIDCommMessage,
        senderPrivateKey: Data,
        recipientPublicKey: Data
    ) throws -> Data {
        // 1. ECDH shared secret
        let sharedSecret = try x25519.deriveSharedSecret(
            privateKey: senderPrivateKey,
            peerPublicKey: recipientPublicKey
        )

        // 2. HKDF-SHA256 derive AES-256 key
        let aesKey = hkdfSha256(
            ikm: sharedSecret,
            info: Data("DIDComm-authcrypt".utf8),
            length: 32
        )

        // 3. Serialize message to JSON
        let plaintext = try JSONEncoder().encode(message)

        // 4. AES-256-GCM encrypt
        let symmetricKey = SymmetricKey(data: aesKey)
        let sealedBox = try AES.GCM.seal(plaintext, using: symmetricKey)

        guard let combined = sealedBox.combined else {
            throw DIDCommError.packingFailed("Failed to produce combined sealed box")
        }

        return combined
    }

    /// HKDF-SHA256 key derivation (extract + expand).
    internal func hkdfSha256(ikm: Data, info: Data, length: Int) -> Data {
        let symmetricKey = SymmetricKey(data: ikm)
        let derivedKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: symmetricKey,
            salt: Data(count: 32),
            info: info,
            outputByteCount: length
        )
        return derivedKey.withUnsafeBytes { Data($0) }
    }
}
