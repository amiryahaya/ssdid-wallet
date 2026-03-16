# Secure Enclave Key Wrapping Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade iOS key wrapping from software Keychain AES-256 to Secure Enclave-backed ECDH key derivation, with transparent lazy migration for existing users.

**Architecture:** Generate a P-256 private key in the Secure Enclave (created once). For each identity, store a per-identity ephemeral P-256 public key in Keychain. Derive the AES-256 wrapping key on-demand via `SE_private.ECDH(ephemeral_public) → HKDF → AES key`. Existing users are lazily migrated on first key access. Simulator falls back to software P-256.

**Tech Stack:** Swift, CryptoKit (SecureEnclave.P256.KeyAgreement), iOS Keychain Services, AES-256-GCM

**Spec:** `docs/superpowers/specs/2026-03-16-secure-enclave-key-wrapping-design.md`

---

## File Structure

| File | Responsibility |
|------|---------------|
| `KeychainManager.swift` | SE master key lifecycle, ECDH-based generateWrappingKey/encrypt/decrypt, ephemeral key storage, legacy helpers |
| `VaultImpl.swift` | Migration orchestration in sign() path with lock + fallback |

Only 2 files change. The `KeychainManagerProtocol`, `Vault` protocol, `BackupManager`, and all other consumers remain untouched.

---

## Chunk 1: KeychainManager SE Support

### Task 1: Add SE master key management

**Files:**
- Modify: `ios/SsdidWallet/Platform/Keychain/KeychainManager.swift`

- [ ] **Step 1: Add SE error case and master key tag**

Add to `KeychainError` enum:
```swift
case secureEnclaveUnavailable
```

Add to `errorDescription`:
```swift
case .secureEnclaveUnavailable:
    return "Secure Enclave is not available on this device"
```

Add constants to `KeychainManager`:
```swift
private static let seMasterTag = "my.ssdid.wallet.se_master"
private static let ephemeralPrefix = "my.ssdid.wallet.eph_"
```

- [ ] **Step 2: Implement ensureSEMasterKey()**

Add to `KeychainManager`:

```swift
// MARK: - Secure Enclave Master Key

/// The SE master key, loaded lazily. Used for ECDH key derivation.
private var _seMasterKey: SecureEnclave.P256.KeyAgreement.PrivateKey?

/// Ensures the SE master key exists. Creates it on first call.
/// Falls back to software P-256 on simulator (no SE).
private func ensureSEMasterKey() throws -> SecureEnclave.P256.KeyAgreement.PrivateKey {
    if let cached = _seMasterKey { return cached }

    // Try to load existing SE key
    if let existing = try? loadSEMasterKey() {
        _seMasterKey = existing
        return existing
    }

    // Create new SE key
    guard SecureEnclave.isAvailable else {
        throw KeychainError.secureEnclaveUnavailable
    }

    var accessControl: SecAccessControl?
    if requireBiometric {
        var error: Unmanaged<CFError>?
        accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            [.privateKeyUsage, .biometryCurrentSet],
            &error
        )
        guard accessControl != nil else {
            throw KeychainError.accessControlCreationFailed
        }
    }

    let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(
        accessControl: accessControl ?? SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .privateKeyUsage,
            nil
        )!
    )

    // Store the key's dataRepresentation in Keychain for reload
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: Self.seMasterTag,
        kSecAttrAccount as String: "se_master",
        kSecValueData as String: key.dataRepresentation,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    ]
    SecItemDelete(query as CFDictionary) // Remove stale if any
    let status = SecItemAdd(query as CFDictionary, nil)
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
    guard status == errSecSuccess, let data = result as? Data else {
        return nil
    }

    return try SecureEnclave.P256.KeyAgreement.PrivateKey(dataRepresentation: data)
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && git add ios/SsdidWallet/Platform/Keychain/KeychainManager.swift && git commit -m "feat(ios): add Secure Enclave master key management to KeychainManager"
```

---

### Task 2: Add ECDH-based key derivation and ephemeral key storage

**Files:**
- Modify: `ios/SsdidWallet/Platform/Keychain/KeychainManager.swift`

- [ ] **Step 1: Implement deriveKey(alias) using ECDH + HKDF**

Add to `KeychainManager`:

```swift
// MARK: - ECDH Key Derivation

/// Derives an AES-256 wrapping key via ECDH(SE_master, ephemeral_pub) → HKDF.
/// The derived key is deterministic for a given alias and SE master key.
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
```

- [ ] **Step 2: Implement ephemeral public key storage/load**

```swift
// MARK: - Ephemeral Public Key Storage

private func ephemeralServiceKey(alias: String) -> String {
    "\(Self.ephemeralPrefix)\(alias)"
}

/// Returns true if an SE-derived ephemeral key exists for this alias.
func hasEphemeralKey(alias: String) -> Bool {
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
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: ephemeralServiceKey(alias: alias),
        kSecAttrAccount as String: alias,
        kSecValueData as String: data,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    ]
    // Delete existing if present
    let deleteQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: ephemeralServiceKey(alias: alias),
        kSecAttrAccount as String: alias
    ]
    SecItemDelete(deleteQuery as CFDictionary)

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
        throw KeychainError.keyNotFound("ephemeral key for \(alias)")
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
```

- [ ] **Step 3: Commit**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && git add ios/SsdidWallet/Platform/Keychain/KeychainManager.swift && git commit -m "feat(ios): add ECDH key derivation and ephemeral key storage"
```

---

### Task 3: Rewrite generateWrappingKey/encrypt/decrypt to use SE

**Files:**
- Modify: `ios/SsdidWallet/Platform/Keychain/KeychainManager.swift`

- [ ] **Step 1: Rewrite generateWrappingKey()**

Replace the current `generateWrappingKey` (lines 76-118) with:

```swift
/// Generates a new SE-backed wrapping key for the given alias.
/// Creates an ephemeral P-256 key pair. The public key is stored in Keychain.
/// The wrapping key is derived on-demand via ECDH(SE_master, ephemeral_pub) → HKDF.
func generateWrappingKey(alias: String) throws {
    if SecureEnclave.isAvailable {
        // Ensure SE master key exists
        _ = try ensureSEMasterKey()

        // Generate ephemeral P-256 key pair
        let ephemeralKey = P256.KeyAgreement.PrivateKey()

        // Store only the public key — private key is discarded
        try saveEphemeralPublicKey(alias: alias, publicKey: ephemeralKey.publicKey)

        // Delete any legacy software key for this alias
        deleteLegacyKey(alias: alias)
    } else {
        // Simulator fallback: generate random AES-256 key (legacy path)
        generateLegacyWrappingKey(alias: alias)
    }
}
```

- [ ] **Step 2: Rewrite encrypt() and decrypt()**

Replace `encrypt` (lines 124-133):

```swift
/// Encrypts data using the SE-derived AES-256-GCM key for the given alias.
/// Returns nonce (12 bytes) + ciphertext + tag (16 bytes).
func encrypt(alias: String, data: Data) throws -> Data {
    let key: SymmetricKey
    if hasEphemeralKey(alias: alias) {
        key = try deriveKey(alias: alias)
    } else {
        // Legacy path (pre-migration or simulator)
        let keyData = try loadKey(alias: alias)
        key = SymmetricKey(data: keyData)
    }

    let sealedBox = try AES.GCM.seal(data, using: key)
    guard let combined = sealedBox.combined else {
        throw KeychainError.encryptionFailed("Failed to produce combined sealed box")
    }
    return combined
}
```

Replace `decrypt` (lines 139-145):

```swift
/// Decrypts data using the SE-derived AES-256-GCM key for the given alias.
/// Expects nonce (12 bytes) + ciphertext + tag (16 bytes).
func decrypt(alias: String, data: Data) throws -> Data {
    let key: SymmetricKey
    if hasEphemeralKey(alias: alias) {
        key = try deriveKey(alias: alias)
    } else {
        // Legacy path (pre-migration or simulator)
        let keyData = try loadKey(alias: alias)
        key = SymmetricKey(data: keyData)
    }

    let sealedBox = try AES.GCM.SealedBox(combined: data)
    return try AES.GCM.open(sealedBox, using: key)
}
```

- [ ] **Step 3: Add legacy helpers and update hasKey/deleteKey**

Rename old `generateWrappingKey` logic into a private helper and add migration methods:

```swift
// MARK: - Legacy Key Support (pre-SE migration)

/// Generates a legacy random AES-256 key in software Keychain.
/// Used only as simulator fallback.
private func generateLegacyWrappingKey(alias: String) {
    var keyData = Data(count: 32)
    _ = keyData.withUnsafeMutableBytes {
        SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
    }
    deleteKey(alias: alias)
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceKey(alias: alias),
        kSecAttrAccount as String: alias,
        kSecValueData as String: keyData,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    ]
    SecItemAdd(query as CFDictionary, nil)
}

/// Decrypts using the legacy software AES-256 key. Used during migration.
func decryptLegacy(alias: String, data: Data) throws -> Data {
    let keyData = try loadKey(alias: alias) // loads from kSecClassGenericPassword
    let key = SymmetricKey(data: keyData)
    let sealedBox = try AES.GCM.SealedBox(combined: data)
    return try AES.GCM.open(sealedBox, using: key)
}

/// Deletes only the legacy software key (kSecClassGenericPassword under serviceKey).
func deleteLegacyKey(alias: String) {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceKey(alias: alias),
        kSecAttrAccount as String: alias
    ]
    SecItemDelete(query as CFDictionary)
}

/// Returns true if a legacy software key exists for this alias.
func hasLegacyKey(alias: String) -> Bool {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceKey(alias: alias),
        kSecAttrAccount as String: alias,
        kSecReturnData as String: false
    ]
    return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess
}
```

Update `hasKey` to check both formats:

```swift
/// Returns true if a key (SE-derived or legacy) exists for the given alias.
func hasKey(alias: String) -> Bool {
    hasEphemeralKey(alias: alias) || hasLegacyKey(alias: alias)
}
```

Update `deleteKey` to clean up both formats:

```swift
/// Deletes all key material (SE ephemeral + legacy) for the given alias.
func deleteKey(alias: String) {
    deleteEphemeralPublicKey(alias: alias)
    deleteLegacyKey(alias: alias)
}
```

- [ ] **Step 4: Update protocol**

Add new methods to `KeychainManagerProtocol`:

```swift
protocol KeychainManagerProtocol {
    func generateWrappingKey(alias: String) throws
    func encrypt(alias: String, data: Data) throws -> Data
    func decrypt(alias: String, data: Data) throws -> Data
    func deleteKey(alias: String)
    func hasKey(alias: String) -> Bool

    // Migration support
    func hasEphemeralKey(alias: String) -> Bool
    func hasLegacyKey(alias: String) -> Bool
    func decryptLegacy(alias: String, data: Data) throws -> Data
    func deleteLegacyKey(alias: String)
}
```

- [ ] **Step 5: Commit**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && git add ios/SsdidWallet/Platform/Keychain/KeychainManager.swift && git commit -m "feat(ios): rewrite KeychainManager to use Secure Enclave ECDH key derivation"
```

---

## Chunk 2: Migration + Verification

### Task 4: Add lazy migration to VaultImpl.sign()

**Files:**
- Modify: `ios/SsdidWallet/Domain/Vault/VaultImpl.swift`

- [ ] **Step 1: Add migration lock**

Add to `VaultImpl` class properties:

```swift
private let migrationLock = NSLock()
```

- [ ] **Step 2: Update sign() with migration logic**

Replace the `sign` method (currently lines 105-127):

```swift
func sign(keyId: String, data: Data) async throws -> Data {
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
        // Attempt SE-derived decrypt
        do {
            var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
            defer {
                privateKey.withUnsafeMutableBytes { ptr in
                    if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
                }
            }
            let cryptoProvider = provider(for: identity.algorithm)
            return try cryptoProvider.sign(algorithm: identity.algorithm, privateKey: privateKey, data: data)
        } catch {
            // SE decrypt failed — ephemeral key may be stale from interrupted migration
            // Fall through to legacy path if legacy key exists
            if !keychainManager.hasLegacyKey(alias: wrappingAlias) {
                throw error // No fallback available
            }
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
private func migrateAndSign(
    identity: Identity,
    wrappingAlias: String,
    encryptedPrivateKey: Data,
    data: Data
) async throws -> Data {
    migrationLock.lock()
    defer { migrationLock.unlock() }

    // Double-check after acquiring lock
    if keychainManager.hasEphemeralKey(alias: wrappingAlias),
       !keychainManager.hasLegacyKey(alias: wrappingAlias) {
        // Another thread already migrated — use SE path
        var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
        defer {
            privateKey.withUnsafeMutableBytes { ptr in
                if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
            }
        }
        return try provider(for: identity.algorithm).sign(algorithm: identity.algorithm, privateKey: privateKey, data: data)
    }

    // Decrypt with legacy key
    var privateKey = try keychainManager.decryptLegacy(alias: wrappingAlias, data: encryptedPrivateKey)
    defer {
        privateKey.withUnsafeMutableBytes { ptr in
            if let baseAddress = ptr.baseAddress { memset(baseAddress, 0, ptr.count) }
        }
    }

    // Generate new SE-derived wrapping key
    try keychainManager.generateWrappingKey(alias: wrappingAlias)

    // Re-encrypt private key with SE-derived key
    let newEncrypted = try keychainManager.encrypt(alias: wrappingAlias, data: privateKey)

    // Persist (FileVaultStorage uses .atomic write — crash-safe)
    try await storage.saveIdentity(identity, encryptedPrivateKey: newEncrypted)

    // Delete old software key (safe — new ciphertext persisted)
    keychainManager.deleteLegacyKey(alias: wrappingAlias)

    // Sign with the decrypted key
    return try provider(for: identity.algorithm).sign(algorithm: identity.algorithm, privateKey: privateKey, data: data)
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && git add ios/SsdidWallet/Domain/Vault/VaultImpl.swift && git commit -m "feat(ios): add lazy SE migration to VaultImpl.sign()"
```

---

### Task 5: Verify iOS build

- [ ] **Step 1: Build iOS**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,id=81450DA8-4C56-4DCA-8A11-A5B9874B8C29' build 2>&1 | grep -E "BUILD|error:" | tail -10`
Expected: BUILD SUCCEEDED

- [ ] **Step 2: Fix any compilation errors**

If errors, read the error messages and fix. Common issues:
- Missing import for `CryptoKit` (already imported)
- `SecureEnclave.P256.KeyAgreement.PrivateKey` requires `import CryptoKit`
- Access control for SE key may need different flags on simulator

- [ ] **Step 3: Verify Android still builds**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (no Android changes in this feature)

- [ ] **Step 4: Final commit if cleanup needed**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && git add -A && git commit -m "chore(ios): fix compilation for SE key wrapping"
```
