import Foundation

/// Data structure for pre-rotated key material.
public struct PreRotatedKeyData {
    public let encryptedPrivateKey: Data
    public let publicKey: Data
}

/// Protocol for persistent vault storage operations.
public protocol VaultStorage: SdJwtVcStorage {
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

    // Recovery key storage
    func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws
    func getRecoveryPublicKey(keyId: String) async -> Data?

    // Pre-rotated key storage (KERI)
    func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws
    func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData?
    func deletePreRotatedKey(keyId: String) async throws

    // Rotation history
    func addRotationEntry(did: String, entry: RotationEntry) async throws
    func getRotationHistory(did: String) async -> [RotationEntry]

    // Onboarding state
    func isOnboardingCompleted() async -> Bool
    func setOnboardingCompleted() async throws
}

/// File-based + UserDefaults implementation of VaultStorage.
/// Identities and credentials are stored in UserDefaults as JSON.
/// Encrypted private keys are stored as files in the app's documents directory.
public final class FileVaultStorage: VaultStorage {

    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private enum Keys {
        static let identities = "ssdid_vault_identities"
        static let credentials = "ssdid_vault_credentials"
        static let sdJwtVcs = "ssdid_vault_sd_jwt_vcs"
        static let onboarding = "ssdid_onboarding_completed"
    }

    public init(defaults: UserDefaults = .standard, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
    }

    // MARK: - Identities

    public func saveIdentity(_ identity: Identity, encryptedPrivateKey: Data) async throws {
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

    public func getIdentity(keyId: String) async -> Identity? {
        let identities = await listIdentities()
        return identities.first { $0.keyId == keyId }
    }

    public func listIdentities() async -> [Identity] {
        guard let data = defaults.data(forKey: Keys.identities) else { return [] }
        return (try? decoder.decode([Identity].self, from: data)) ?? []
    }

    public func deleteIdentity(keyId: String) async throws {
        var identities = await listIdentities()
        identities.removeAll { $0.keyId == keyId }
        let data = try encoder.encode(identities)
        defaults.set(data, forKey: Keys.identities)

        // Delete private key file
        let keyPath = try privateKeyFilePath(keyId: keyId)
        try? fileManager.removeItem(at: keyPath)
    }

    public func getEncryptedPrivateKey(keyId: String) async -> Data? {
        guard let keyPath = try? privateKeyFilePath(keyId: keyId) else { return nil }
        return try? Data(contentsOf: keyPath)
    }

    // MARK: - Credentials

    public func saveCredential(_ credential: VerifiableCredential) async throws {
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

    public func listCredentials() async -> [VerifiableCredential] {
        guard let data = defaults.data(forKey: Keys.credentials) else { return [] }
        return (try? decoder.decode([VerifiableCredential].self, from: data)) ?? []
    }

    public func deleteCredential(credentialId: String) async throws {
        var credentials = await listCredentials()
        credentials.removeAll { $0.id == credentialId }
        let data = try encoder.encode(credentials)
        defaults.set(data, forKey: Keys.credentials)
    }

    // MARK: - SD-JWT VCs

    public func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws {
        var vcs = await listSdJwtVcs()

        if let index = vcs.firstIndex(where: { $0.id == sdJwtVc.id }) {
            vcs[index] = sdJwtVc
        } else {
            vcs.append(sdJwtVc)
        }

        let data = try encoder.encode(vcs)
        defaults.set(data, forKey: Keys.sdJwtVcs)
    }

    public func listSdJwtVcs() async -> [StoredSdJwtVc] {
        guard let data = defaults.data(forKey: Keys.sdJwtVcs) else { return [] }
        return (try? decoder.decode([StoredSdJwtVc].self, from: data)) ?? []
    }

    public func deleteSdJwtVc(id: String) async throws {
        var vcs = await listSdJwtVcs()
        vcs.removeAll { $0.id == id }
        let data = try encoder.encode(vcs)
        defaults.set(data, forKey: Keys.sdJwtVcs)
    }

    // MARK: - Recovery Keys

    public func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws {
        let path = try recoveryKeyFilePath(keyId: keyId)
        try encryptedPublicKey.write(to: path, options: [.atomic, .completeFileProtection])
    }

    public func getRecoveryPublicKey(keyId: String) async -> Data? {
        guard let path = try? recoveryKeyFilePath(keyId: keyId) else { return nil }
        return try? Data(contentsOf: path)
    }

    // MARK: - Pre-Rotated Keys

    public func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws {
        let privPath = try preRotatedPrivateKeyFilePath(keyId: keyId)
        let pubPath = try preRotatedPublicKeyFilePath(keyId: keyId)
        try encryptedPrivateKey.write(to: privPath, options: [.atomic, .completeFileProtection])
        try publicKey.write(to: pubPath, options: [.atomic, .completeFileProtection])
    }

    public func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData? {
        guard let privPath = try? preRotatedPrivateKeyFilePath(keyId: keyId),
              let pubPath = try? preRotatedPublicKeyFilePath(keyId: keyId),
              let encPriv = try? Data(contentsOf: privPath),
              let pub = try? Data(contentsOf: pubPath) else {
            return nil
        }
        return PreRotatedKeyData(encryptedPrivateKey: encPriv, publicKey: pub)
    }

    public func deletePreRotatedKey(keyId: String) async throws {
        if let privPath = try? preRotatedPrivateKeyFilePath(keyId: keyId) {
            try? fileManager.removeItem(at: privPath)
        }
        if let pubPath = try? preRotatedPublicKeyFilePath(keyId: keyId) {
            try? fileManager.removeItem(at: pubPath)
        }
    }

    // MARK: - Rotation History

    private func rotationHistoryKey(did: String) -> String {
        "ssdid_rotation_history_\(did)"
    }

    public func addRotationEntry(did: String, entry: RotationEntry) async throws {
        var history = await getRotationHistory(did: did)
        history.append(entry)
        let data = try encoder.encode(history)
        defaults.set(data, forKey: rotationHistoryKey(did: did))
    }

    public func getRotationHistory(did: String) async -> [RotationEntry] {
        guard let data = defaults.data(forKey: rotationHistoryKey(did: did)) else { return [] }
        return (try? decoder.decode([RotationEntry].self, from: data)) ?? []
    }

    // MARK: - Onboarding

    public func isOnboardingCompleted() async -> Bool {
        defaults.bool(forKey: Keys.onboarding)
    }

    public func setOnboardingCompleted() async throws {
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

// MARK: - SdJwtVcStore conformance for OpenId4VpHandler

extension FileVaultStorage: SdJwtVcStore {}
