import Foundation
import Security
import CryptoKit
import LocalAuthentication

/// Errors specific to Keychain operations.
enum KeychainError: Error, LocalizedError {
    case keyGenerationFailed(OSStatus)
    case keyNotFound(String)
    case encryptionFailed(String)
    case decryptionFailed(String)
    case deleteFailed(OSStatus)
    case unexpectedError(String)
    case accessControlCreationFailed
    case biometricAuthFailed(String)

    var errorDescription: String? {
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
        }
    }
}

/// iOS Keychain wrapper for key wrapping operations.
/// Uses AES-256-GCM with Keychain-stored symmetric keys.
/// Keys are stored with kSecAttrAccessibleWhenUnlockedThisDeviceOnly for security.
protocol KeychainManagerProtocol {
    func generateWrappingKey(alias: String) throws
    func encrypt(alias: String, data: Data) throws -> Data
    func decrypt(alias: String, data: Data) throws -> Data
    func deleteKey(alias: String)
    func hasKey(alias: String) -> Bool
}

/// Concrete Keychain manager using iOS Keychain Services.
final class KeychainManager: KeychainManagerProtocol {

    private let servicePrefix: String

    /// When true, sensitive wrapping keys are protected with biometric authentication
    /// (Face ID / Touch ID). The biometric check uses `.biometryCurrentSet`, meaning
    /// stored keys are invalidated if the enrolled biometrics change.
    ///
    /// - Important: Defaults to `false` for development.
    // TODO: Set requireBiometric = true before production release.
    let requireBiometric: Bool

    init(servicePrefix: String = "my.ssdid.wallet", requireBiometric: Bool = false) {
        self.servicePrefix = servicePrefix
        self.requireBiometric = requireBiometric
    }

    private func serviceKey(alias: String) -> String {
        "\(servicePrefix).\(alias)"
    }

    // MARK: - Key Generation

    /// Generates a new AES-256 wrapping key and stores it in the Keychain.
    /// If a key with the same alias already exists, it is overwritten.
    func generateWrappingKey(alias: String) throws {
        // Generate 32 bytes of random key material
        var keyData = Data(count: 32)
        let result = keyData.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
        }
        guard result == errSecSuccess else {
            throw KeychainError.keyGenerationFailed(result)
        }

        // Delete existing key if present
        deleteKey(alias: alias)

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

    // MARK: - Encryption

    /// Encrypts data using the AES-256-GCM key stored under the given alias.
    /// Returns nonce (12 bytes) + ciphertext + tag (16 bytes).
    func encrypt(alias: String, data: Data) throws -> Data {
        let keyData = try loadKey(alias: alias)
        let key = SymmetricKey(data: keyData)

        let sealedBox = try AES.GCM.seal(data, using: key)
        guard let combined = sealedBox.combined else {
            throw KeychainError.encryptionFailed("Failed to produce combined sealed box")
        }
        return combined
    }

    // MARK: - Decryption

    /// Decrypts data using the AES-256-GCM key stored under the given alias.
    /// Expects nonce (12 bytes) + ciphertext + tag (16 bytes).
    func decrypt(alias: String, data: Data) throws -> Data {
        let keyData = try loadKey(alias: alias)
        let key = SymmetricKey(data: keyData)

        let sealedBox = try AES.GCM.SealedBox(combined: data)
        return try AES.GCM.open(sealedBox, using: key)
    }

    // MARK: - Delete

    /// Deletes the key with the given alias from the Keychain.
    func deleteKey(alias: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Secret Storage (String Values)

    /// Saves a secret string value in the Keychain under the given alias.
    /// If an entry with the same alias already exists, it is overwritten.
    func saveSecret(alias: String, value: String) throws {
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
    func loadSecret(alias: String) throws -> String? {
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
    func deleteSecret(alias: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Has Key

    /// Returns true if a key with the given alias exists in the Keychain.
    func hasKey(alias: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceKey(alias: alias),
            kSecAttrAccount as String: alias,
            kSecReturnData as String: false
        ]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    // MARK: - Private

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
