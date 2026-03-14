import Foundation

/// Data structure for pre-rotated key material.
struct PreRotatedKeyData {
    let encryptedPrivateKey: Data
    let publicKey: Data
}

/// Protocol for persistent vault storage operations.
protocol VaultStorage {
    func saveIdentity(_ identity: Identity, encryptedPrivateKey: Data) async throws
    func getIdentity(keyId: String) async -> Identity?
    func listIdentities() async -> [Identity]
    func deleteIdentity(keyId: String) async throws
    func getEncryptedPrivateKey(keyId: String) async -> Data?

    func saveCredential(_ credential: VerifiableCredential) async throws
    func listCredentials() async -> [VerifiableCredential]
    func deleteCredential(credentialId: String) async throws

    // SD-JWT VC storage
    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws
    func listSdJwtVcs() async -> [StoredSdJwtVc]
    func deleteSdJwtVc(id: String) async throws

    // mdoc / mDL storage
    func saveMDoc(_ mdoc: StoredMDoc) async throws
    func listMDocs() async -> [StoredMDoc]
    func getMDoc(id: String) async -> StoredMDoc?
    func deleteMDoc(id: String) async throws

    // Recovery key storage
    func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws
    func getRecoveryPublicKey(keyId: String) async -> Data?

    // Pre-rotated key storage (KERI)
    func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws
    func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData?
    func deletePreRotatedKey(keyId: String) async throws

    // Onboarding state
    func isOnboardingCompleted() async -> Bool
    func setOnboardingCompleted() async throws
}

/// File-based + UserDefaults implementation of VaultStorage.
/// Identities and credentials are stored in UserDefaults as JSON.
/// Encrypted private keys are stored as files in the app's documents directory.
final class FileVaultStorage: VaultStorage {

    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private enum Keys {
        static let identities = "ssdid_vault_identities"
        static let credentials = "ssdid_vault_credentials"
        static let sdJwtVcs = "ssdid_vault_sd_jwt_vcs"
        static let mdocs = "ssdid_vault_mdocs"
        static let onboarding = "ssdid_onboarding_completed"
    }

    init(defaults: UserDefaults = .standard, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
    }

    // MARK: - Identities

    func saveIdentity(_ identity: Identity, encryptedPrivateKey: Data) async throws {
        var identities = await listIdentities()

        // Replace if existing, otherwise append
        if let index = identities.firstIndex(where: { $0.keyId == identity.keyId }) {
            identities[index] = identity
        } else {
            identities.append(identity)
        }

        let data = try encoder.encode(identities)
        defaults.set(data, forKey: Keys.identities)

        // Save encrypted private key to file
        let keyPath = try privateKeyFilePath(keyId: identity.keyId)
        try encryptedPrivateKey.write(to: keyPath, options: [.atomic, .completeFileProtection])
    }

    func getIdentity(keyId: String) async -> Identity? {
        let identities = await listIdentities()
        return identities.first { $0.keyId == keyId }
    }

    func listIdentities() async -> [Identity] {
        guard let data = defaults.data(forKey: Keys.identities) else { return [] }
        return (try? decoder.decode([Identity].self, from: data)) ?? []
    }

    func deleteIdentity(keyId: String) async throws {
        var identities = await listIdentities()
        identities.removeAll { $0.keyId == keyId }
        let data = try encoder.encode(identities)
        defaults.set(data, forKey: Keys.identities)

        // Delete private key file
        let keyPath = try privateKeyFilePath(keyId: keyId)
        try? fileManager.removeItem(at: keyPath)
    }

    func getEncryptedPrivateKey(keyId: String) async -> Data? {
        guard let keyPath = try? privateKeyFilePath(keyId: keyId) else { return nil }
        return try? Data(contentsOf: keyPath)
    }

    // MARK: - Credentials

    func saveCredential(_ credential: VerifiableCredential) async throws {
        var credentials = await listCredentials()

        // Replace if existing, otherwise append
        if let index = credentials.firstIndex(where: { $0.id == credential.id }) {
            credentials[index] = credential
        } else {
            credentials.append(credential)
        }

        let data = try encoder.encode(credentials)
        defaults.set(data, forKey: Keys.credentials)
    }

    func listCredentials() async -> [VerifiableCredential] {
        guard let data = defaults.data(forKey: Keys.credentials) else { return [] }
        return (try? decoder.decode([VerifiableCredential].self, from: data)) ?? []
    }

    func deleteCredential(credentialId: String) async throws {
        var credentials = await listCredentials()
        credentials.removeAll { $0.id == credentialId }
        let data = try encoder.encode(credentials)
        defaults.set(data, forKey: Keys.credentials)
    }

    // MARK: - SD-JWT VCs

    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws {
        var vcs = await listSdJwtVcs()

        if let index = vcs.firstIndex(where: { $0.id == sdJwtVc.id }) {
            vcs[index] = sdJwtVc
        } else {
            vcs.append(sdJwtVc)
        }

        let data = try encoder.encode(vcs)
        defaults.set(data, forKey: Keys.sdJwtVcs)
    }

    func listSdJwtVcs() async -> [StoredSdJwtVc] {
        guard let data = defaults.data(forKey: Keys.sdJwtVcs) else { return [] }
        return (try? decoder.decode([StoredSdJwtVc].self, from: data)) ?? []
    }

    func deleteSdJwtVc(id: String) async throws {
        var vcs = await listSdJwtVcs()
        vcs.removeAll { $0.id == id }
        let data = try encoder.encode(vcs)
        defaults.set(data, forKey: Keys.sdJwtVcs)
    }

    // MARK: - mdoc / mDL

    func saveMDoc(_ mdoc: StoredMDoc) async throws {
        var mdocs = await listMDocs()

        if let index = mdocs.firstIndex(where: { $0.id == mdoc.id }) {
            mdocs[index] = mdoc
        } else {
            mdocs.append(mdoc)
        }

        let data = try encoder.encode(mdocs)
        defaults.set(data, forKey: Keys.mdocs)
    }

    func listMDocs() async -> [StoredMDoc] {
        guard let data = defaults.data(forKey: Keys.mdocs) else { return [] }
        return (try? decoder.decode([StoredMDoc].self, from: data)) ?? []
    }

    func getMDoc(id: String) async -> StoredMDoc? {
        let mdocs = await listMDocs()
        return mdocs.first { $0.id == id }
    }

    func deleteMDoc(id: String) async throws {
        var mdocs = await listMDocs()
        mdocs.removeAll { $0.id == id }
        let data = try encoder.encode(mdocs)
        defaults.set(data, forKey: Keys.mdocs)
    }

    // MARK: - Recovery Keys

    func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws {
        let path = try recoveryKeyFilePath(keyId: keyId)
        try encryptedPublicKey.write(to: path, options: [.atomic, .completeFileProtection])
    }

    func getRecoveryPublicKey(keyId: String) async -> Data? {
        guard let path = try? recoveryKeyFilePath(keyId: keyId) else { return nil }
        return try? Data(contentsOf: path)
    }

    // MARK: - Pre-Rotated Keys

    func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws {
        let privPath = try preRotatedPrivateKeyFilePath(keyId: keyId)
        let pubPath = try preRotatedPublicKeyFilePath(keyId: keyId)
        try encryptedPrivateKey.write(to: privPath, options: [.atomic, .completeFileProtection])
        try publicKey.write(to: pubPath, options: [.atomic, .completeFileProtection])
    }

    func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData? {
        guard let privPath = try? preRotatedPrivateKeyFilePath(keyId: keyId),
              let pubPath = try? preRotatedPublicKeyFilePath(keyId: keyId),
              let encPriv = try? Data(contentsOf: privPath),
              let pub = try? Data(contentsOf: pubPath) else {
            return nil
        }
        return PreRotatedKeyData(encryptedPrivateKey: encPriv, publicKey: pub)
    }

    func deletePreRotatedKey(keyId: String) async throws {
        if let privPath = try? preRotatedPrivateKeyFilePath(keyId: keyId) {
            try? fileManager.removeItem(at: privPath)
        }
        if let pubPath = try? preRotatedPublicKeyFilePath(keyId: keyId) {
            try? fileManager.removeItem(at: pubPath)
        }
    }

    // MARK: - Onboarding

    func isOnboardingCompleted() async -> Bool {
        defaults.bool(forKey: Keys.onboarding)
    }

    func setOnboardingCompleted() async throws {
        defaults.set(true, forKey: Keys.onboarding)
    }

    // MARK: - File Paths

    private func vaultDirectory() throws -> URL {
        let docsDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        let vaultDir = docsDir.appendingPathComponent("ssdid_vault", isDirectory: true)
        if !fileManager.fileExists(atPath: vaultDir.path) {
            try fileManager.createDirectory(at: vaultDir, withIntermediateDirectories: true)
        }
        return vaultDir
    }

    private func safeFileName(_ keyId: String) -> String {
        // Hash the keyId to produce a safe file name
        let hash = keyId.data(using: .utf8)!.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return hash
    }

    private func privateKeyFilePath(keyId: String) throws -> URL {
        try vaultDirectory().appendingPathComponent("pk_\(safeFileName(keyId)).enc")
    }

    private func recoveryKeyFilePath(keyId: String) throws -> URL {
        try vaultDirectory().appendingPathComponent("rk_\(safeFileName(keyId)).enc")
    }

    private func preRotatedPrivateKeyFilePath(keyId: String) throws -> URL {
        try vaultDirectory().appendingPathComponent("prk_\(safeFileName(keyId)).enc")
    }

    private func preRotatedPublicKeyFilePath(keyId: String) throws -> URL {
        try vaultDirectory().appendingPathComponent("prk_\(safeFileName(keyId)).pub")
    }
}
