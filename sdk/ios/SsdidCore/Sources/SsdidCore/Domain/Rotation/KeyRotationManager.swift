import Foundation
import CryptoKit

// MARK: - Supporting Types

/// Status of the KERI-inspired pre-rotation commitment for an identity.
struct RotationStatus {
    let hasPreCommitment: Bool
    let nextKeyHash: String?
    let lastRotatedAt: String?
    let rotationHistory: [RotationEntry]
}

/// A single entry in the rotation history log.
struct RotationEntry: Codable {
    let timestamp: String
    let oldKeyIdFragment: String
    let newKeyIdFragment: String
}

/// Errors that can occur during key rotation.
enum RotationError: Error, LocalizedError {
    case noPreCommitment
    case preRotatedKeyNotFound(String)
    case privateKeyNotFound(String)
    case identityNotFound(String)

    var errorDescription: String? {
        switch self {
        case .noPreCommitment:
            return "No pre-commitment exists. Call prepareRotation first."
        case .preRotatedKeyNotFound(let keyId):
            return "Pre-rotated key data not found: \(keyId)"
        case .privateKeyNotFound(let keyId):
            return "Private key not found for: \(keyId)"
        case .identityNotFound(let keyId):
            return "Identity not found: \(keyId)"
        }
    }
}

/// Protocol for objects that can publish DID document updates to the registry.
protocol DidDocumentUpdater {
    func updateDidDocument(keyId: String) async throws
}

extension SsdidClient: DidDocumentUpdater {}

// MARK: - KeyRotationManager

/// Manages KERI-inspired key pre-rotation and rotation for identities.
///
/// The rotation flow has two phases:
/// 1. **Prepare**: Generate next keypair, publish pre-commitment hash.
/// 2. **Execute**: Promote pre-committed key to active, publish update, clean up old key.
final class KeyRotationManager: @unchecked Sendable {

    private let vault: Vault
    private let storage: VaultStorage
    private let cryptoProvider: CryptoProvider
    private let keychainManager: KeychainManagerProtocol
    private let ssdidClient: DidDocumentUpdater

    init(
        vault: Vault,
        storage: VaultStorage,
        cryptoProvider: CryptoProvider,
        keychainManager: KeychainManagerProtocol,
        ssdidClient: DidDocumentUpdater
    ) {
        self.vault = vault
        self.storage = storage
        self.cryptoProvider = cryptoProvider
        self.keychainManager = keychainManager
        self.ssdidClient = ssdidClient
    }

    // MARK: - Prepare Rotation

    /// Generates a next keypair, computes a SHA3-256 pre-commitment hash,
    /// encrypts and stores the pre-rotated key, and publishes the commitment.
    /// Returns the multibase-encoded hash string.
    func prepareRotation(identity: Identity) async throws -> String {
        let nextKeyPair = try cryptoProvider.generateKeyPair(algorithm: identity.algorithm)

        // SHA3-256 hash of next public key, multibase base64url encoded
        let hashBytes = SHA3.sha256(nextKeyPair.publicKey)
        let nextKeyHash = Multibase.encode(hashBytes)

        // Encrypt pre-rotated private key with keychain wrapping key
        let wrappingAlias = "ssdid_prerot_\(stableAlias(identity.keyId))"
        try keychainManager.generateWrappingKey(alias: wrappingAlias)
        let encryptedPrivateKey = try keychainManager.encrypt(alias: wrappingAlias, data: nextKeyPair.privateKey)

        // Store pre-rotated key material
        try await storage.savePreRotatedKey(
            keyId: identity.keyId,
            encryptedPrivateKey: encryptedPrivateKey,
            publicKey: nextKeyPair.publicKey
        )

        // Update identity with preRotatedKeyId reference
        var updatedIdentity = identity
        updatedIdentity.preRotatedKeyId = identity.keyId

        let existingEncKey = await storage.getEncryptedPrivateKey(keyId: identity.keyId)
            ?? { fatalError("Private key not found for: \(identity.keyId)") }()
        try await storage.saveIdentity(updatedIdentity, encryptedPrivateKey: existingEncKey)

        // Publish pre-commitment to registry
        try await ssdidClient.updateDidDocument(keyId: identity.keyId)

        return nextKeyHash
    }

    // MARK: - Execute Rotation

    /// Promotes the pre-committed key to active, publishes to registry,
    /// then cleans up old key material. Crash-safe: publishes before deleting.
    func executeRotation(identity: Identity) async throws -> Identity {
        guard identity.preRotatedKeyId != nil else {
            throw RotationError.noPreCommitment
        }

        // Retrieve pre-rotated key data (stored under original keyId)
        guard let preRotatedData = await storage.getPreRotatedKey(keyId: identity.keyId) else {
            throw RotationError.preRotatedKeyNotFound(identity.keyId)
        }

        // Build new identity with pre-rotated key promoted to active
        let newKeyId = identity.did + "#key-\(UUID().uuidString.prefix(8))"
        let publicKeyMultibase = Multibase.encode(preRotatedData.publicKey)

        var newIdentity = Identity(
            name: identity.name,
            did: identity.did,
            keyId: newKeyId,
            algorithm: identity.algorithm,
            publicKeyMultibase: publicKeyMultibase,
            createdAt: identity.createdAt
        )
        newIdentity.isActive = identity.isActive
        newIdentity.profileName = identity.profileName
        newIdentity.email = identity.email
        newIdentity.emailVerified = identity.emailVerified

        // Re-wrap the pre-rotated private key with the new identity's wrapping key.
        // The pre-rotated key was encrypted with "ssdid_prerot_..." alias during prepareRotation,
        // but VaultImpl.sign() expects it under "ssdid_wrap_..." alias for the new DID.
        let prerotAlias = "ssdid_prerot_\(stableAlias(identity.keyId))"
        let rawPrivateKey = try keychainManager.decrypt(alias: prerotAlias, data: preRotatedData.encryptedPrivateKey)

        let did = Did(value: newIdentity.did)
        let newWrapAlias = "ssdid_wrap_\(did.methodSpecificId())"
        try keychainManager.generateWrappingKey(alias: newWrapAlias)
        let reWrappedKey = try keychainManager.encrypt(alias: newWrapAlias, data: rawPrivateKey)

        // Zero raw key from memory
        var mutableKey = rawPrivateKey
        mutableKey.withUnsafeMutableBytes { ptr in
            if let base = ptr.baseAddress { memset(base, 0, ptr.count) }
        }

        // Save new identity with re-wrapped private key
        try await storage.saveIdentity(newIdentity, encryptedPrivateKey: reWrappedKey)

        // Publish to registry BEFORE deleting old data (crash-safe ordering)
        try await ssdidClient.updateDidDocument(keyId: newIdentity.keyId)

        // Clean up old identity and pre-rotated key
        try await storage.deleteIdentity(keyId: identity.keyId)
        try await storage.deletePreRotatedKey(keyId: identity.keyId)
        keychainManager.deleteKey(alias: "ssdid_prerot_\(stableAlias(identity.keyId))")

        return newIdentity
    }

    // MARK: - Rotation Status

    /// Returns the current rotation status for the given identity.
    func getRotationStatus(identity: Identity) async -> RotationStatus {
        // Fetch the latest identity from storage (may have been updated by prepareRotation)
        let currentIdentity = await storage.getIdentity(keyId: identity.keyId) ?? identity
        let hasPreCommitment = currentIdentity.preRotatedKeyId != nil

        var nextKeyHash: String? = nil
        if hasPreCommitment, let preRotatedData = await storage.getPreRotatedKey(keyId: identity.keyId) {
            let hashBytes = SHA3.sha256(preRotatedData.publicKey)
            nextKeyHash = Multibase.encode(hashBytes)
        }

        return RotationStatus(
            hasPreCommitment: hasPreCommitment,
            nextKeyHash: nextKeyHash,
            lastRotatedAt: nil,
            rotationHistory: []
        )
    }

    // MARK: - Private

    private func stableAlias(_ keyId: String) -> String {
        let data = Data(keyId.utf8)
        let hash = SHA3.sha256(data)
        return hash.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
            .prefix(16)
            .description
    }
}
