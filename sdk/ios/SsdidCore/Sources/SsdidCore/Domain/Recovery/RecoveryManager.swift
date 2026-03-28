import Foundation
import CryptoKit

/// Manages recovery key generation, storage, and identity restoration.
final class RecoveryManager: @unchecked Sendable {

    private let vault: Vault
    private let storage: VaultStorage
    private let classicalProvider: CryptoProvider
    private let pqcProvider: CryptoProvider
    private let keychainManager: KeychainManager

    init(
        vault: Vault,
        storage: VaultStorage,
        classicalProvider: CryptoProvider,
        pqcProvider: CryptoProvider,
        keychainManager: KeychainManager
    ) {
        self.vault = vault
        self.storage = storage
        self.classicalProvider = classicalProvider
        self.pqcProvider = pqcProvider
        self.keychainManager = keychainManager
    }

    private func provider(for algorithm: Algorithm) -> CryptoProvider {
        algorithm.isPostQuantum ? pqcProvider : classicalProvider
    }

    /// Generates a recovery key pair for the given identity.
    /// Returns the recovery private key bytes -- the caller must export/store offline.
    /// The recovery public key is stored in the vault for future DID Document updates.
    func generateRecoveryKey(identity: Identity) async throws -> Data {
        let cryptoProvider = provider(for: identity.algorithm)
        let recoveryKeyPair = try cryptoProvider.generateKeyPair(algorithm: identity.algorithm)
        let recoveryKeyId = "\(identity.keyId)-recovery"

        // Store the recovery public key encrypted (for DID Document building)
        let wrappingAlias = "ssdid_recovery_\(stableAlias(identity.keyId))"
        try keychainManager.generateWrappingKey(alias: wrappingAlias)
        let encryptedRecoveryPubKey = try keychainManager.encrypt(alias: wrappingAlias, data: recoveryKeyPair.publicKey)

        // Save recovery key reference in vault storage
        try await storage.saveRecoveryPublicKey(keyId: recoveryKeyId, encryptedPublicKey: encryptedRecoveryPubKey)

        // Update identity to mark as having recovery key
        let updatedIdentity = Identity(
            name: identity.name,
            did: identity.did,
            keyId: identity.keyId,
            algorithm: identity.algorithm,
            publicKeyMultibase: identity.publicKeyMultibase,
            createdAt: identity.createdAt,
            isActive: identity.isActive,
            recoveryKeyId: recoveryKeyId,
            hasRecoveryKey: true,
            preRotatedKeyId: identity.preRotatedKeyId
        )

        // Re-save identity with updated metadata
        guard let encryptedPrivateKey = await storage.getEncryptedPrivateKey(keyId: identity.keyId) else {
            throw VaultError.privateKeyNotFound(identity.keyId)
        }
        try await storage.saveIdentity(updatedIdentity, encryptedPrivateKey: encryptedPrivateKey)

        // Return the recovery private key for the user to store offline
        return recoveryKeyPair.privateKey
    }

    /// Checks if an identity has a recovery key configured.
    func hasRecoveryKey(keyId: String) async -> Bool {
        guard let identity = await storage.getIdentity(keyId: keyId) else { return false }
        return identity.hasRecoveryKey
    }

    /// Restores an identity using an offline recovery private key.
    /// Generates a new primary key pair and stores locally.
    /// Caller should subsequently publish the updated DID Document via SsdidClient.
    func restoreWithRecoveryKey(
        did: String,
        recoveryPrivateKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ) async throws -> Identity {
        guard let recoveryPrivateKey = Data(base64Encoded: recoveryPrivateKeyBase64) else {
            throw RecoveryError.invalidRecoveryKey("Failed to decode Base64 recovery key")
        }

        let cryptoProvider = provider(for: algorithm)

        // Verify the recovery key against stored recovery public key if available
        let recoveryKeyId = await findRecoveryKeyId(did: did)
        if let recoveryKeyId = recoveryKeyId {
            if let encryptedRecoveryPubKey = await storage.getRecoveryPublicKey(keyId: recoveryKeyId) {
                let wrappingAlias = "ssdid_recovery_\(stableAlias(recoveryKeyId.replacingOccurrences(of: "-recovery", with: "")))"
                let recoveryPublicKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedRecoveryPubKey)

                // Challenge-response verification
                var challengeBytes = [UInt8](repeating: 0, count: 32)
                _ = SecRandomCopyBytes(kSecRandomDefault, challengeBytes.count, &challengeBytes)
                let challengeData = Data(challengeBytes)

                let sig = try cryptoProvider.sign(algorithm: algorithm, privateKey: recoveryPrivateKey, data: challengeData)
                let verified = try cryptoProvider.verify(algorithm: algorithm, publicKey: recoveryPublicKey, signature: sig, data: challengeData)
                guard verified else {
                    throw RecoveryError.verificationFailed("Recovery key does not match stored recovery public key")
                }
            }
        }

        // Generate new primary key pair
        let kp = try cryptoProvider.generateKeyPair(algorithm: algorithm)
        let keyId = "\(did)#\(UUID().uuidString.prefix(8))"
        let publicKeyMultibase = Multibase.encode(kp.publicKey)
        let now = ISO8601DateFormatter().string(from: Date())

        let identity = Identity(
            name: name,
            did: did,
            keyId: keyId,
            algorithm: algorithm,
            publicKeyMultibase: publicKeyMultibase,
            createdAt: now
        )

        // Encrypt and store new private key
        let alias = stableAlias(keyId)
        try keychainManager.generateWrappingKey(alias: alias)
        let encryptedKey = try keychainManager.encrypt(alias: alias, data: kp.privateKey)
        try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedKey)

        return identity
    }

    // MARK: - Private Helpers

    private func findRecoveryKeyId(did: String) async -> String? {
        let identities = await storage.listIdentities()
        return identities
            .filter { $0.did == did && $0.hasRecoveryKey && $0.recoveryKeyId != nil }
            .compactMap { $0.recoveryKeyId }
            .first
    }

    private func stableAlias(_ keyId: String) -> String {
        let hash = SHA256.hash(data: Data(keyId.utf8))
        let hashData = Data(hash)
        let base64 = hashData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return String(base64.prefix(16))
    }
}

/// Errors specific to recovery operations.
public enum RecoveryError: Error, LocalizedError {
    case invalidRecoveryKey(String)
    case verificationFailed(String)

    public var errorDescription: String? {
        switch self {
        case .invalidRecoveryKey(let reason):
            return "Invalid recovery key: \(reason)"
        case .verificationFailed(let reason):
            return "Recovery key verification failed: \(reason)"
        }
    }
}
