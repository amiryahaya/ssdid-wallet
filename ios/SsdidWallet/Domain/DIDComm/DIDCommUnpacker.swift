import Foundation
import CryptoKit

/// Unpacks DIDComm v2 authcrypt messages (X25519 ECDH + HKDF-SHA256 + AES-256-GCM).
final class DIDCommUnpacker {

    private let x25519 = X25519Provider()

    /// Decrypts a packed DIDComm message.
    ///
    /// Expects wire format: nonce (12 bytes) || ciphertext || tag (16 bytes).
    ///
    /// - Parameters:
    ///   - packed: The encrypted message bytes.
    ///   - recipientPrivateKey: Recipient's X25519 private key (32 bytes).
    ///   - senderPublicKey: Sender's X25519 public key (32 bytes).
    /// - Returns: The decrypted DIDComm message.
    func unpack(
        packed: Data,
        recipientPrivateKey: Data,
        senderPublicKey: Data
    ) throws -> DIDCommMessage {
        guard packed.count > 28 else {
            // Minimum: 12 (nonce) + 0 (ciphertext) + 16 (tag) = 28 bytes
            throw DIDCommError.invalidData("Packed data too short (\(packed.count) bytes)")
        }

        // 1. ECDH shared secret
        let sharedSecret = try x25519.deriveSharedSecret(
            privateKey: recipientPrivateKey,
            peerPublicKey: senderPublicKey
        )

        // 2. HKDF-SHA256 derive AES-256 key
        let packer = DIDCommPacker()
        let aesKey = packer.hkdfSha256(
            ikm: sharedSecret,
            info: Data("DIDComm-authcrypt".utf8),
            length: 32
        )

        // 3. AES-256-GCM decrypt
        let symmetricKey = SymmetricKey(data: aesKey)
        let sealedBox = try AES.GCM.SealedBox(combined: packed)
        let plaintext = try AES.GCM.open(sealedBox, using: symmetricKey)

        // 4. Parse JSON to DIDCommMessage
        do {
            return try JSONDecoder().decode(DIDCommMessage.self, from: plaintext)
        } catch {
            throw DIDCommError.unpackingFailed("JSON decoding failed: \(error.localizedDescription)")
        }
    }
}
