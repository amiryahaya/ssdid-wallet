import Foundation
import CryptoKit

/// Concrete implementation of the Vault protocol.
/// Uses CryptoProvider for key operations, KeychainManager for key wrapping,
/// and VaultStorage for persistence.
/// Concrete implementation of the Vault protocol.
/// Uses @unchecked Sendable because dependencies (CryptoProvider, KeychainManager,
/// VaultStorage) are not Sendable. Actor migration requires making all dependencies
/// Sendable first. The TOCTOU window on createIdentity name uniqueness is documented;
/// in practice concurrent identity creation is extremely rare.
public final class VaultImpl: Vault, @unchecked Sendable {

    private nonisolated(unsafe) static let iso8601 = ISO8601DateFormatter()

    private let classicalProvider: CryptoProvider
    private let pqcProvider: CryptoProvider
    private let keychainManager: KeychainManager
    private let storage: VaultStorage
    private let migrationLock = NSLock()
    private var migratingAliases = Set<String>()

    public     init(
        classicalProvider: CryptoProvider,
        pqcProvider: CryptoProvider,
        keychainManager: KeychainManager,
        storage: VaultStorage
    ) {
        self.classicalProvider = classicalProvider
        self.pqcProvider = pqcProvider
        self.keychainManager = keychainManager
        self.storage = storage
    }

    private func provider(for algorithm: Algorithm) -> CryptoProvider {
        algorithm.isPostQuantum ? pqcProvider : classicalProvider
    }

    // MARK: - Identity Management

    public     func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        let existing = await storage.listIdentities()
        if existing.contains(where: { $0.name.lowercased() == name.lowercased() }) {
            throw VaultError.identityNameExists(name)
        }

        let cryptoProvider = provider(for: algorithm)
        let keyPair = try cryptoProvider.generateKeyPair(algorithm: algorithm)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let publicKeyMultibase = Multibase.encode(keyPair.publicKey)

        // Wrap private key with Keychain-backed key
        let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"
        try keychainManager.generateWrappingKey(alias: wrappingAlias)
        let encryptedPrivateKey = try keychainManager.encrypt(alias: wrappingAlias, data: keyPair.privateKey)

        let now = Self.iso8601.string(from: Date())
        let identity = Identity(
            name: name,
            did: did.value,
            keyId: keyId,
            algorithm: algorithm,
            publicKeyMultibase: publicKeyMultibase,
            createdAt: now
        )

        try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedPrivateKey)
        return identity
    }

    public     func getIdentity(keyId: String) async -> Identity? {
        return await storage.getIdentity(keyId: keyId)
    }

    public     func listIdentities() async -> [Identity] {
        return await storage.listIdentities()
    }

    public     func deleteIdentity(keyId: String) async throws {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let did = Did(value: identity.did)
        keychainManager.deleteKey(alias: "ssdid_wrap_\(did.methodSpecificId())")
        try await storage.deleteIdentity(keyId: keyId)
    }

    public     func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {
        guard var identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        guard let encryptedKey = await storage.getEncryptedPrivateKey(keyId: keyId) else {
            throw VaultError.privateKeyNotFound(keyId)
        }
        if let profileName = profileName { identity.profileName = profileName }
        if let email = email {
            if email != identity.email {
                identity.email = email
                identity.emailVerified = false  // Reset verification when email changes
            }
        }
        if let emailVerified = emailVerified { identity.emailVerified = emailVerified }
        try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedKey)
    }

    // MARK: - Signing

    public     func sign(keyId: String, data: Data) async throws -> Data {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let did = Did(value: identity.did)
        let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"

        guard let encryptedPrivateKey = await storage.getEncryptedPrivateKey(keyId: keyId) else {
            throw VaultError.privateKeyNotFound(keyId)
        }

        // Try SE-path first (new format or already migrated)
        if keychainManager.hasEphemeralKey(alias: wrappingAlias) {
            do {
                var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
                defer {
                    privateKey.withUnsafeMutableBytes { ptr in
                        if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
                    }
                }
                let cryptoProvider = provider(for: identity.algorithm)
                return try cryptoProvider.sign(algorithm: identity.algorithm, privateKey: privateKey, data: data)
            } catch let error as KeychainError {
                // SE decrypt failed — may be stale ephemeral key from interrupted migration
                if !keychainManager.hasLegacyKey(alias: wrappingAlias) {
                    throw error // No fallback available
                }
                // Fall through to legacy migration path
            } catch {
                // Non-keychain error (signing failed, etc.) — do not silently downgrade
                throw error
            }
        }

        // Legacy path — migrate to SE
        if keychainManager.hasLegacyKey(alias: wrappingAlias) {
            return try await migrateAndSign(
                identity: identity,
                wrappingAlias: wrappingAlias,
                encryptedPrivateKey: encryptedPrivateKey,
                data: data
            )
        }

        throw VaultError.privateKeyNotFound(keyId)
    }

    /// Migrates a legacy software-wrapped key to SE-derived wrapping, then signs.
    /// Uses per-alias tracking to prevent concurrent migration of the same identity.
    private func migrateAndSign(
        identity: Identity,
        wrappingAlias: String,
        encryptedPrivateKey: Data,
        data: Data
    ) async throws -> Data {
        // Check / mark alias under lock to prevent concurrent migration
        try migrationLock.withLock {
            guard !migratingAliases.contains(wrappingAlias) else {
                throw VaultError.storageFailed("Migration in progress for this identity, please retry")
            }
            migratingAliases.insert(wrappingAlias)
        }
        defer {
            migrationLock.withLock { _ = migratingAliases.remove(wrappingAlias) }
        }

        // Check if already migrated by a previous call
        if keychainManager.hasEphemeralKey(alias: wrappingAlias),
           !keychainManager.hasLegacyKey(alias: wrappingAlias) {
            var pk = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
            defer {
                pk.withUnsafeMutableBytes { ptr in
                    if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
                }
            }
            return try provider(for: identity.algorithm).sign(algorithm: identity.algorithm, privateKey: pk, data: data)
        }

        // Decrypt with legacy key
        var pk = try keychainManager.decryptLegacy(alias: wrappingAlias, data: encryptedPrivateKey)

        // Generate new SE-derived wrapping key
        try keychainManager.generateWrappingKey(alias: wrappingAlias)

        // Re-encrypt with SE-derived key
        let reEncrypted = try keychainManager.encrypt(alias: wrappingAlias, data: pk)

        // Sign while we have the raw key
        let signature = try provider(for: identity.algorithm).sign(algorithm: identity.algorithm, privateKey: pk, data: data)

        // Zero raw key
        pk.withUnsafeMutableBytes { ptr in
            if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
        }

        // Persist new ciphertext asynchronously (outside migration tracking)
        try await storage.saveIdentity(identity, encryptedPrivateKey: reEncrypted)
        keychainManager.deleteLegacyKey(alias: wrappingAlias)

        return signature
    }

    // MARK: - DID Document

    public     func buildDidDocument(keyId: String) async throws -> DidDocument {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let did = Did(value: identity.did)

        // Check for pre-rotated key hash
        var nextKeyHash: String? = nil
        if let preRotatedKeyId = identity.preRotatedKeyId {
            if let preRotatedData = await storage.getPreRotatedKey(keyId: preRotatedKeyId) {
                let hash = SHA3.sha256(preRotatedData.publicKey)
                nextKeyHash = Multibase.encode(hash)
            }
        }

        var doc = DidDocument.build(
            did: did,
            keyId: identity.keyId,
            algorithm: identity.algorithm,
            publicKeyMultibase: identity.publicKeyMultibase
        )
        doc.nextKeyHash = nextKeyHash
        return doc
    }

    // MARK: - Proof Creation

    public     func createProof(
        keyId: String,
        document: [String: Any],
        proofPurpose: String,
        challenge: String? = nil,
        domain: String? = nil
    ) async throws -> Proof {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let now = Self.iso8601.string(from: Date())

        // Build proof options (without proofValue)
        var proofOptions: [String: Any] = [
            "type": identity.algorithm.proofType,
            "created": now,
            "verificationMethod": identity.keyId,
            "proofPurpose": proofPurpose
        ]
        if let challenge = challenge { proofOptions["challenge"] = challenge }
        if let domain = domain { proofOptions["domain"] = domain }

        // W3C Data Integrity signing payload:
        // SHA3-256(canonical_json(proof_options)) || SHA3-256(canonical_json(document))
        let optionsJson = canonicalJson(proofOptions)
        let docJson = canonicalJson(document)
        let optionsHash = SHA3.sha256(Data(optionsJson.utf8))
        let docHash = SHA3.sha256(Data(docJson.utf8))
        var payload = optionsHash
        payload.append(docHash)

        let signatureData = try await sign(keyId: keyId, data: payload)

        return Proof(
            type: identity.algorithm.proofType,
            created: now,
            verificationMethod: identity.keyId,
            proofPurpose: proofPurpose,
            proofValue: Multibase.encode(signatureData),
            domain: domain,
            challenge: challenge
        )
    }

    // MARK: - Credentials

    public     func storeCredential(_ credential: VerifiableCredential) async throws {
        try await storage.saveCredential(credential)
    }

    public     func listCredentials() async -> [VerifiableCredential] {
        return await storage.listCredentials()
    }

    public     func getCredentialForDid(_ did: String) async -> VerifiableCredential? {
        let credentials = await storage.listCredentials()
        return credentials.first { $0.credentialSubject.id == did }
    }

    public     func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] {
        let credentials = await storage.listCredentials()
        return credentials.filter { $0.credentialSubject.id == did }
    }

    public     func deleteCredential(credentialId: String) async throws {
        try await storage.deleteCredential(credentialId: credentialId)
    }

    // MARK: - Canonical JSON

    /// Produces deterministic JSON by recursively sorting dictionary keys.
    /// Delegates to shared JsonUtils implementation.
    public static func canonicalJson(_ value: Any) -> String {
        JsonUtils.canonicalJson(value)
    }
}

// Allow calling canonicalJson as instance method forwarding to static
extension VaultImpl {
    public     func canonicalJson(_ value: Any) -> String {
        JsonUtils.canonicalJson(value)
    }
}
