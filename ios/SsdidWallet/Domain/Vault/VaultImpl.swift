import Foundation
import LibOQS

/// Concrete implementation of the Vault protocol.
/// Uses CryptoProvider for key operations, KeychainManager for key wrapping,
/// and VaultStorage for persistence.
final class VaultImpl: Vault, @unchecked Sendable {

    private nonisolated(unsafe) static let iso8601 = ISO8601DateFormatter()

    private let classicalProvider: CryptoProvider
    private let pqcProvider: CryptoProvider
    private let keychainManager: KeychainManager
    private let storage: VaultStorage

    init(
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

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
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

    func getIdentity(keyId: String) async -> Identity? {
        return await storage.getIdentity(keyId: keyId)
    }

    func listIdentities() async -> [Identity] {
        return await storage.listIdentities()
    }

    func deleteIdentity(keyId: String) async throws {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let did = Did(value: identity.did)
        keychainManager.deleteKey(alias: "ssdid_wrap_\(did.methodSpecificId())")
        try await storage.deleteIdentity(keyId: keyId)
    }

    func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {
        guard var identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        guard let encryptedKey = await storage.getEncryptedPrivateKey(keyId: keyId) else {
            throw VaultError.privateKeyNotFound(keyId)
        }
        if let profileName = profileName { identity.profileName = profileName }
        if let email = email { identity.email = email }
        if let emailVerified = emailVerified { identity.emailVerified = emailVerified }
        try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedKey)
    }

    // MARK: - Signing

    func sign(keyId: String, data: Data) async throws -> Data {
        guard let identity = await storage.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let did = Did(value: identity.did)
        let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"

        guard let encryptedPrivateKey = await storage.getEncryptedPrivateKey(keyId: keyId) else {
            throw VaultError.privateKeyNotFound(keyId)
        }

        let privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
        defer {
            // Zero private key from memory
            var mutableKey = privateKey
            mutableKey.resetBytes(in: 0..<mutableKey.count)
        }

        let cryptoProvider = provider(for: identity.algorithm)
        return try cryptoProvider.sign(algorithm: identity.algorithm, privateKey: privateKey, data: data)
    }

    // MARK: - DID Document

    func buildDidDocument(keyId: String) async throws -> DidDocument {
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

    func createProof(
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

    func storeCredential(_ credential: VerifiableCredential) async throws {
        try await storage.saveCredential(credential)
    }

    func listCredentials() async -> [VerifiableCredential] {
        return await storage.listCredentials()
    }

    func getCredentialForDid(_ did: String) async -> VerifiableCredential? {
        let credentials = await storage.listCredentials()
        return credentials.first { $0.credentialSubject.id == did }
    }

    func deleteCredential(credentialId: String) async throws {
        try await storage.deleteCredential(credentialId: credentialId)
    }

    // MARK: - Canonical JSON

    /// Produces deterministic JSON by recursively sorting dictionary keys.
    /// Delegates to shared JsonUtils implementation.
    static func canonicalJson(_ value: Any) -> String {
        JsonUtils.canonicalJson(value)
    }
}

// Allow calling canonicalJson as instance method forwarding to static
extension VaultImpl {
    func canonicalJson(_ value: Any) -> String {
        JsonUtils.canonicalJson(value)
    }
}
