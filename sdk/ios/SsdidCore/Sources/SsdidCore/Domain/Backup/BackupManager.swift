import Foundation
import CryptoKit

/// Manages encrypted backup creation and restoration using PBKDF2 + AES-256-GCM.
public final class BackupManager: @unchecked Sendable {

    private let vault: Vault
    private let storage: VaultStorage
    private let keychainManager: KeychainManager
    private let activityRepo: ActivityRepository

    private static let pbkdf2Iterations: UInt32 = 600_000
    private static let saltLength = 32
    private static let nonceLength = 12
    private static let keyLengthBytes = 32

    public init(
        vault: Vault,
        storage: VaultStorage,
        keychainManager: KeychainManager,
        activityRepo: ActivityRepository
    ) {
        self.vault = vault
        self.storage = storage
        self.keychainManager = keychainManager
        self.activityRepo = activityRepo
    }

    // MARK: - Create Backup

    /// Creates an encrypted backup of all identities.
    /// Returns the backup package as JSON-encoded bytes.
    public func createBackup(passphrase: String) async throws -> Data {
        let identities = await vault.listIdentities()
        guard !identities.isEmpty else {
            throw BackupError.noIdentities
        }

        // Build backup identities with decrypted private keys
        var backupIdentities: [BackupIdentity] = []
        for identity in identities {
            let did = Did(value: identity.did)
            let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"

            guard let encryptedPrivateKey = await storage.getEncryptedPrivateKey(keyId: identity.keyId) else {
                throw VaultError.privateKeyNotFound(identity.keyId)
            }

            var rawPrivateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
            defer { rawPrivateKey.resetBytes(in: 0..<rawPrivateKey.count) }

            backupIdentities.append(BackupIdentity(
                keyId: identity.keyId,
                did: identity.did,
                name: identity.name,
                algorithm: identity.algorithm.rawValue,
                privateKey: rawPrivateKey.base64URLEncodedNoPadding(),
                publicKey: identity.publicKeyMultibase,
                createdAt: identity.createdAt
            ))
        }

        let payload = BackupPayload(identities: backupIdentities)
        let payloadBytes = try JSONEncoder().encode(payload)

        // Generate salt and derive keys
        var salt = Data(count: Self.saltLength)
        salt.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, Self.saltLength, $0.baseAddress!) }

        var backupKey = try deriveBackupKey(passphrase: passphrase, salt: salt)
        defer { backupKey.resetBytes(in: 0..<backupKey.count) }

        var encKey = deriveSubKey(prk: backupKey, info: "enc")
        defer { encKey.resetBytes(in: 0..<encKey.count) }

        var macKey = deriveSubKey(prk: backupKey, info: "mac")
        defer { macKey.resetBytes(in: 0..<macKey.count) }

        // Encrypt with AES-256-GCM
        let nonce = AES.GCM.Nonce()
        let symmetricKey = SymmetricKey(data: encKey)
        let sealedBox = try AES.GCM.seal(payloadBytes, using: symmetricKey, nonce: nonce)
        let ciphertext = sealedBox.ciphertext + sealedBox.tag

        let now = ISO8601DateFormatter().string(from: Date())

        // Compute HMAC over salt || nonce || ciphertext
        let hmacKey = SymmetricKey(data: macKey)
        var hmacData = Data()
        hmacData.append(salt)
        hmacData.append(contentsOf: nonce)
        hmacData.append(ciphertext)
        let hmac = HMAC<SHA256>.authenticationCode(for: hmacData, using: hmacKey)

        let package = BackupPackage(
            salt: salt.base64URLEncodedNoPadding(),
            nonce: Data(nonce).base64URLEncodedNoPadding(),
            ciphertext: ciphertext.base64URLEncodedNoPadding(),
            algorithms: Array(Set(identities.map { $0.algorithm.rawValue })),
            dids: identities.map { $0.did },
            createdAt: now,
            hmac: Data(hmac).base64URLEncodedNoPadding()
        )

        let result = try JSONEncoder().encode(package)

        // Log activity (non-fatal)
        for identity in identities {
            try? await activityRepo.addActivity(ActivityRecord(
                id: UUID().uuidString,
                type: .BACKUP_CREATED,
                did: identity.did,
                timestamp: ISO8601DateFormatter().string(from: Date()),
                status: .SUCCESS,
                details: ["algorithm": identity.algorithm.rawValue]
            ))
        }

        return result
    }

    // MARK: - Restore Backup

    /// Restores identities from an encrypted backup.
    /// Returns the number of identities restored.
    public func restoreBackup(backupData: Data, passphrase: String) async throws -> Int {
        let package = try JSONDecoder().decode(BackupPackage.self, from: backupData)

        let salt = try Data.fromBase64URL(package.salt)
        var backupKey = try deriveBackupKey(passphrase: passphrase, salt: salt)
        defer { backupKey.resetBytes(in: 0..<backupKey.count) }

        var encKey = deriveSubKey(prk: backupKey, info: "enc")
        defer { encKey.resetBytes(in: 0..<encKey.count) }

        var macKey = deriveSubKey(prk: backupKey, info: "mac")
        defer { macKey.resetBytes(in: 0..<macKey.count) }

        // Verify HMAC
        let nonce = try Data.fromBase64URL(package.nonce)
        let ciphertext = try Data.fromBase64URL(package.ciphertext)

        let hmacKey = SymmetricKey(data: macKey)
        var hmacInput = Data()
        hmacInput.append(salt)
        hmacInput.append(nonce)
        hmacInput.append(ciphertext)
        let expectedHmac = HMAC<SHA256>.authenticationCode(for: hmacInput, using: hmacKey)

        let actualHmac = try Data.fromBase64URL(package.hmac)
        guard Data(expectedHmac) == actualHmac else {
            throw BackupError.hmacVerificationFailed
        }

        // Decrypt
        let gcmNonce = try AES.GCM.Nonce(data: nonce)
        let symmetricKey = SymmetricKey(data: encKey)

        // Separate ciphertext and tag (last 16 bytes are GCM tag)
        let tagLength = 16
        guard ciphertext.count > tagLength else {
            throw BackupError.invalidBackupFormat("Ciphertext too short")
        }
        let encryptedData = ciphertext.prefix(ciphertext.count - tagLength)
        let tag = ciphertext.suffix(tagLength)
        let sealedBox = try AES.GCM.SealedBox(nonce: gcmNonce, ciphertext: encryptedData, tag: tag)
        let payloadBytes = try AES.GCM.open(sealedBox, using: symmetricKey)

        let payload = try JSONDecoder().decode(BackupPayload.self, from: payloadBytes)

        // Restore each identity
        var restoredCount = 0
        for backupIdentity in payload.identities {
            var rawPrivateKey = try Data.fromBase64URL(backupIdentity.privateKey)
            defer { rawPrivateKey.resetBytes(in: 0..<rawPrivateKey.count) }

            let did = Did(value: backupIdentity.did)
            let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"
            try keychainManager.generateWrappingKey(alias: wrappingAlias)
            let encryptedPrivateKey = try keychainManager.encrypt(alias: wrappingAlias, data: rawPrivateKey)

            guard let algorithm = Algorithm(rawValue: backupIdentity.algorithm) else {
                continue // Skip unknown algorithms
            }

            let identity = Identity(
                name: backupIdentity.name,
                did: backupIdentity.did,
                keyId: backupIdentity.keyId,
                algorithm: algorithm,
                publicKeyMultibase: backupIdentity.publicKey,
                createdAt: backupIdentity.createdAt
            )
            try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedPrivateKey)
            restoredCount += 1
        }

        return restoredCount
    }

    // MARK: - Key Derivation

    /// Derives a backup key from a passphrase using PBKDF2-HMAC-SHA256.
    private func deriveBackupKey(passphrase: String, salt: Data) throws -> Data {
        let passphraseData = Data(passphrase.utf8)
        var derivedKey = Data(count: Self.keyLengthBytes)

        let result = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
            salt.withUnsafeBytes { saltBytes in
                passphraseData.withUnsafeBytes { passphraseBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passphraseBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        passphraseData.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        Self.pbkdf2Iterations,
                        derivedKeyBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        Self.keyLengthBytes
                    )
                }
            }
        }

        guard result == kCCSuccess else {
            throw BackupError.keyDerivationFailed
        }
        return derivedKey
    }

    /// HKDF-Expand (RFC 5869) using HMAC-SHA256.
    /// PRK = backupKey (already derived via PBKDF2), info = purpose label.
    /// Outputs exactly 32 bytes (one HMAC block).
    private func deriveSubKey(prk: Data, info: String) -> Data {
        let key = SymmetricKey(data: prk)
        var input = Data(info.utf8)
        input.append(0x01) // counter byte for first (and only) block
        let mac = HMAC<SHA256>.authenticationCode(for: input, using: key)
        return Data(mac)
    }
}

// MARK: - Backup Data Models

public struct BackupPackage: Codable {
    public var version: Int = 1
    public let salt: String
    public let nonce: String
    public let ciphertext: String
    public let algorithms: [String]
    public let dids: [String]
    public let createdAt: String
    public let hmac: String

    enum CodingKeys: String, CodingKey {
        case version, salt, nonce, ciphertext, algorithms, dids
        case createdAt = "created_at"
        case hmac
    }
}

public struct BackupPayload: Codable {
    public let identities: [BackupIdentity]
}

public struct BackupIdentity: Codable {
    public let keyId: String
    public let did: String
    public let name: String
    public let algorithm: String
    public let privateKey: String
    public let publicKey: String
    public let createdAt: String

    enum CodingKeys: String, CodingKey {
        case keyId = "key_id"
        case did, name, algorithm
        case privateKey = "private_key"
        case publicKey = "public_key"
        case createdAt = "created_at"
    }
}

/// Errors specific to backup operations.
public enum BackupError: Error, LocalizedError {
    case noIdentities
    case hmacVerificationFailed
    case keyDerivationFailed
    case invalidBackupFormat(String)

    public var errorDescription: String? {
        switch self {
        case .noIdentities:
            return "No identities to back up"
        case .hmacVerificationFailed:
            return "HMAC verification failed: backup may be tampered with"
        case .keyDerivationFailed:
            return "Failed to derive encryption key from passphrase"
        case .invalidBackupFormat(let reason):
            return "Invalid backup format: \(reason)"
        }
    }
}

// MARK: - Data Helpers

private extension Data {
    func base64URLEncodedNoPadding() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    public static func fromBase64URL(_ string: String) throws -> Data {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: base64) else {
            throw BackupError.invalidBackupFormat("Invalid Base64URL encoding")
        }
        return data
    }
}

// MARK: - CommonCrypto Import

import CommonCrypto
