import Foundation
import Security
import CryptoKit
import LocalAuthentication

/// Errors specific to Keychain operations.
public enum KeychainError: Error, LocalizedError {
    case keyGenerationFailed(OSStatus)
    case keyNotFound(String)
    case encryptionFailed(String)
    case decryptionFailed(String)
    case deleteFailed(OSStatus)
    case unexpectedError(String)
    case accessControlCreationFailed
    case biometricAuthFailed(String)
    case secureEnclaveUnavailable

    public     var errorDescription: String? {
        switch self {
        case .keyGenerationFailed(let status):
            return "Keychain key generation failed: \(status)"
        case .keyNotFound(let alias):
            return "Keychain key not found: \(alias)"
        case .encryptionFailed(let reason):
            return "Keychain encryption failed: \(reason)"
        case .decryptionFailed(let reason):
            return "Keychain decryption failed: \(reason)"
        case .deleteFailed(let status):
            return "Keychain key deletion failed: \(status)"
        case .unexpectedError(let reason):
            return "Keychain unexpected error: \(reason)"
        case .accessControlCreationFailed:
            return "Keychain access control creation failed"
        case .biometricAuthFailed(let reason):
            return "Biometric authentication failed: \(reason)"
        case .secureEnclaveUnavailable:
            return "Secure Enclave is not available on this device"
        }
    }
}

/// iOS Keychain wrapper for key wrapping operations.
/// Uses AES-256-GCM with Keychain-stored symmetric keys.
/// Keys are stored with kSecAttrAccessibleWhenUnlockedThisDeviceOnly for security.
public protocol KeychainManagerProtocol {
    func generateWrappingKey(alias: String) throws
    func encrypt(alias: String, data: Data) throws -> Data
    func decrypt(alias: String, data: Data) throws -> Data
    func deleteKey(alias: String)
    func hasKey(alias: String) -> Bool
    func hasEphemeralKey(alias: String) -> Bool
    func hasLegacyKey(alias: String) -> Bool
    func decryptLegacy(alias: String, data: Data) throws -> Data
    func deleteLegacyKey(alias: String)
}

/// Concrete Keychain manager using iOS Keychain Services.
/// Uses Secure Enclave ECDH key derivation when available, with legacy AES-256
/// software key fallback for simulators and older devices.
public final class KeychainManager: KeychainManagerProtocol {

    private static let seMasterTag = "my.ssdid.wallet.se_master"
    private static let ephemeralPrefix = "my.ssdid.wallet.eph_"

    private let servicePrefix: String

    /// When true, sensitive wrapping keys are protected with biometric authentication
    /// (Face ID / Touch ID). The biometric check uses `.biometryCurrentSet`, meaning
    /// stored keys are invalidated if the enrolled biometrics change.
    ///
    /// - Important: Defaults to `false` in DEBUG builds, `true` in release builds.
    ///   Configured via ``SsdidSdk/Builder/requireBiometric(_:)``.
    public     let requireBiometric: Bool

    /// Cached SE master key to avoid repeated Keychain lookups.
    private let masterKeyLock = NSLock()
    private var _seMasterKey: SecureEnclave.P256.KeyAgreement.PrivateKey?

    public     init(servicePrefix: String = "my.ssdid.wallet", requireBiometric: Bool = false) {
        self.servicePrefix = servicePrefix
        self.requireBiometric = requireBiometric
    }

    private func serviceKey(alias: String) -> String {
        "\(servicePrefix).\(alias)"
    }

    // MARK: - SE Master Key Lifecycle

    private func ensureSEMasterKey() throws -> SecureEnclave.P256.KeyAgreement.PrivateKey {
        masterKeyLock.lock()
        defer { masterKeyLock.unlock() }

        if let cached = _seMasterKey { return cached }
        if let existing = try loadSEMasterKey() {
            _seMasterKey = existing
            return existing
        }
        guard SecureEnclave.isAvailable else {
            throw KeychainError.secureEnclaveUnavailable
        }

        var acError: Unmanaged<CFError>?
        let flags: SecAccessControlCreateFlags = requireBiometric
            ? [.privateKeyUsage, .biometryCurrentSet]
            : .privateKeyUsage
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            flags,
            &acError
        ) else {
            throw KeychainError.accessControlCreationFailed
        }

        let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(accessControl: accessControl)

        // Persist dataRepresentation for reload
        var persistQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.seMasterTag,
            kSecAttrAccount as String: "se_master",
            kSecValueData as String: key.dataRepresentation
        ]

        if requireBiometric {
            // Apply same biometric protection to the stored token
            var acPersistError: Unmanaged<CFError>?
            if let ac = SecAccessControlCreateWithFlags(
                kCFAllocatorDefault,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                .biometryCurrentSet,
                &acPersistError
            ) {
                persistQuery[kSecAttrAccessControl as String] = ac
            }
        } else {
            persistQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        }

        let deleteQ: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.seMasterTag,
            kSecAttrAccount as String: "se_master"
        ]
        SecItemDelete(deleteQ as CFDictionary)
        let status = SecItemAdd(persistQuery as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.keyGenerationFailed(status)
        }
        _seMasterKey = key
        return key
    }

    private func loadSEMasterKey() throws -> SecureEnclave.P256.KeyAgreement.PrivateKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.seMasterTag,
            kSecAttrAccount as String: "se_master",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        switch status {
        case errSecSuccess:
            guard let data = result as? Data else { return nil }
            return try SecureEnclave.P256.KeyAgreement.PrivateKey(dataRepresentation: data)
        case errSecItemNotFound:
            return nil  // First-run: no key yet
        case errSecAuthFailed, errSecUserCanceled:
            // Biometrics re-enrolled or auth failed — existing wrapped keys are inaccessible
            throw KeychainError.biometricAuthFailed("SE master key invalidated — restore from backup required")
        default:
            throw KeychainError.unexpectedError("SE master key load failed with status \(status)")
        }
    }

    // MARK: - ECDH Key Derivation

    /// Derives a 256-bit AES wrapping key via ECDH between the SE master key and
    /// the per-identity ephemeral public key, using HKDF-SHA256.
    private func deriveKey(alias: String) throws -> SymmetricKey {
        let seMasterKey = try ensureSEMasterKey()
        let ephPubData = try loadEphemeralPublicKey(alias: alias)
        let ephPub = try P256.KeyAgreement.PublicKey(x963Representation: ephPubData)
        let sharedSecret = try seMasterKey.sharedSecretFromKeyAgreement(with: ephPub)
        return sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data(),
            sharedInfo: Data("ssdid-wrap-\(alias)".utf8),
            outputByteCount: 32
        )
    }

    // MARK: - Ephemeral Key Storage

    private func ephemeralServiceKey(alias: String) -> String {
        "\(Self.ephemeralPrefix)\(alias)"
    }

    public     func hasEphemeralKey(alias: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: ephemeralServiceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: false
        ]
        return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess
    }

    private func saveEphemeralPublicKey(alias: String, publicKey: P256.KeyAgreement.PublicKey) throws {
        let data = publicKey.x963Representation

        // Delete existing if present
        deleteEphemeralPublicKey(alias: alias)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: ephemeralServiceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.keyGenerationFailed(status)
        }
    }

    private func loadEphemeralPublicKey(alias: String) throws -> Data {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: ephemeralServiceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else {
            throw KeychainError.keyNotFound(alias)
        }
        return data
    }

    private func deleteEphemeralPublicKey(alias: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: ephemeralServiceKey(alias: alias),
            kSecAttrAccount as String: alias
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Key Generation

    /// Generates a new wrapping key for the given alias.
    /// On devices with Secure Enclave, creates an ephemeral P-256 key pair and stores
    /// the public key. The wrapping key is derived on-the-fly via ECDH + HKDF.
    /// Falls back to legacy random AES-256 on simulators / devices without SE.
    public     func generateWrappingKey(alias: String) throws {
        if SecureEnclave.isAvailable {
            // Ensure SE master key exists
            _ = try ensureSEMasterKey()

            // Create ephemeral P-256 key pair; store public key, discard private
            let ephemeralPrivate = P256.KeyAgreement.PrivateKey()
            try saveEphemeralPublicKey(alias: alias, publicKey: ephemeralPrivate.publicKey)
        } else {
            // SECURITY: SE not available — keys are software-backed (simulator or unsupported device)
            #if DEBUG
            print("⚠️ [KeychainManager] Secure Enclave unavailable for alias '\(alias)' — using software fallback")
            #else
            assertionFailure("Secure Enclave unavailable in production build")
            #endif
            try generateLegacyWrappingKey(alias: alias)
        }
    }

    // MARK: - Encryption

    /// Encrypts data using the wrapping key for the given alias.
    /// Uses SE-derived key if an ephemeral key exists, otherwise falls back to legacy.
    /// Returns nonce (12 bytes) + ciphertext + tag (16 bytes).
    public     func encrypt(alias: String, data: Data) throws -> Data {
        let key: SymmetricKey
        if hasEphemeralKey(alias: alias) {
            key = try deriveKey(alias: alias)
        } else {
            let keyData = try loadKey(alias: alias)
            key = SymmetricKey(data: keyData)
        }

        let sealedBox = try AES.GCM.seal(data, using: key)
        guard let combined = sealedBox.combined else {
            throw KeychainError.encryptionFailed("Failed to produce combined sealed box")
        }
        return combined
    }

    // MARK: - Decryption

    /// Decrypts data using the wrapping key for the given alias.
    /// Uses SE-derived key if an ephemeral key exists, otherwise falls back to legacy.
    /// Expects nonce (12 bytes) + ciphertext + tag (16 bytes).
    public     func decrypt(alias: String, data: Data) throws -> Data {
        let key: SymmetricKey
        if hasEphemeralKey(alias: alias) {
            key = try deriveKey(alias: alias)
        } else {
            let keyData = try loadKey(alias: alias)
            key = SymmetricKey(data: keyData)
        }

        let sealedBox = try AES.GCM.SealedBox(combined: data)
        return try AES.GCM.open(sealedBox, using: key)
    }

    // MARK: - Delete

    /// Deletes both ephemeral and legacy keys for the given alias.
    public     func deleteKey(alias: String) {
        deleteEphemeralPublicKey(alias: alias)
        deleteLegacyKey(alias: alias)
    }

    // MARK: - Secret Storage (String Values)

    /// Saves a secret string value in the Keychain under the given alias.
    /// If an entry with the same alias already exists, it is overwritten.
    public     func saveSecret(alias: String, value: String) throws {
        guard let data = value.data(using: .utf8) else {
            throw KeychainError.encryptionFailed("Could not encode secret as UTF-8")
        }

        deleteSecret(alias: alias)

        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecValueData as String: data
        ]

        if requireBiometric {
            var acError: Unmanaged<CFError>?
            guard let accessControl = SecAccessControlCreateWithFlags(
                kCFAllocatorDefault,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                .biometryCurrentSet,
                &acError
            ) else {
                throw KeychainError.accessControlCreationFailed
            }
            query[kSecAttrAccessControl as String] = accessControl
        } else {
            query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        }

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.unexpectedError("SecItemAdd failed for alias '\(alias)': \(status)")
        }
    }

    /// Loads a secret string value from the Keychain for the given alias.
    /// Returns nil if no value is stored under the alias.
    public     func loadSecret(alias: String) throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return nil
        }

        guard status == errSecSuccess, let data = result as? Data else {
            throw KeychainError.keyNotFound(alias)
        }

        guard let value = String(data: data, encoding: .utf8) else {
            throw KeychainError.decryptionFailed("Could not decode secret as UTF-8")
        }
        return value
    }

    /// Deletes a secret value from the Keychain for the given alias.
    public     func deleteSecret(alias: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Has Key

    /// Returns true if either an ephemeral (SE) key or a legacy key exists for the alias.
    public     func hasKey(alias: String) -> Bool {
        hasEphemeralKey(alias: alias) || hasLegacyKey(alias: alias)
    }

    // MARK: - Legacy Helpers

    /// Checks whether a legacy (software AES-256) key exists for the alias.
    public     func hasLegacyKey(alias: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: false
        ]
        return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess
    }

    /// Generates a legacy random AES-256 wrapping key (simulator / non-SE fallback).
    private func generateLegacyWrappingKey(alias: String) throws {
        // Generate 32 bytes of random key material
        var keyData = Data(count: 32)
        let result = keyData.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
        }
        guard result == errSecSuccess else {
            throw KeychainError.keyGenerationFailed(result)
        }

        // Delete existing key if present
        deleteLegacyKey(alias: alias)

        // Store in Keychain
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecValueData as String: keyData
        ]

        if requireBiometric {
            // Protect the wrapping key with biometric authentication.
            // .biometryCurrentSet invalidates the key if biometrics are re-enrolled.
            var error: Unmanaged<CFError>?
            guard let accessControl = SecAccessControlCreateWithFlags(
                kCFAllocatorDefault,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                .biometryCurrentSet,
                &error
            ) else {
                throw KeychainError.accessControlCreationFailed
            }
            query[kSecAttrAccessControl as String] = accessControl
        } else {
            query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        }

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.keyGenerationFailed(status)
        }
    }

    /// Decrypts data using a legacy (software AES-256) key for migration purposes.
    public     func decryptLegacy(alias: String, data: Data) throws -> Data {
        let keyData = try loadKey(alias: alias)
        let key = SymmetricKey(data: keyData)
        let sealedBox = try AES.GCM.SealedBox(combined: data)
        return try AES.GCM.open(sealedBox, using: key)
    }

    /// Deletes only the legacy (software) key for the given alias.
    public     func deleteLegacyKey(alias: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Private (Legacy Key Loading)

    /// Loads a legacy software AES-256 key from the Keychain.
    /// Used by `decryptLegacy` and as fallback for encrypt/decrypt when no ephemeral key exists.
    private func loadKey(alias: String) throws -> Data {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        if requireBiometric {
            // Provide an LAContext so the system prompts for biometric authentication
            // when retrieving a key protected with SecAccessControl.
            let context = LAContext()
            context.localizedReason = "Authenticate to access wallet keys"
            query[kSecUseAuthenticationContext as String] = context
        }

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            if status == errSecAuthFailed || status == errSecUserCanceled {
                throw KeychainError.biometricAuthFailed("Authentication required to access key '\(alias)'")
            }
            throw KeychainError.keyNotFound(alias)
        }
        return data
    }
}
