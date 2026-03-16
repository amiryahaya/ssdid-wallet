# Secure Enclave-Backed Key Wrapping Design

## Problem

iOS KeychainManager stores AES-256 wrapping keys as `kSecClassGenericPassword` items — software-backed Keychain entries protected only by device unlock encryption. The raw key material exists in software and could be extracted by an attacker with Keychain access. Android already uses hardware-backed Keystore (TEE/StrongBox) for wrapping keys, making iOS the weaker link.

## Solution

Derive wrapping keys from a Secure Enclave (SE) P-256 private key via ECDH key agreement. The SE private key never leaves hardware. Each identity gets a per-identity ephemeral P-256 public key stored in Keychain; the AES-256 wrapping key is deterministically re-derived on every access via ECDH + HKDF. No wrapping key material is ever stored in software.

## Architecture

```
SECURE ENCLAVE (hardware, device-bound, non-extractable)
┌───────────────────────────────────────────┐
│  SE Master Key: P-256 private key         │
│  kSecAttrTokenIDSecureEnclave             │
│  Access: biometryCurrentSet + deviceOnly  │
│  Tag: "my.ssdid.wallet.se_master"         │
│  Created once per app install             │
└────────────────────┬──────────────────────┘
                     │
          ECDH key agreement with:
                     │
┌────────────────────▼──────────────────────┐
│  Per-Identity Ephemeral P-256 Public Key  │
│  Stored as kSecClassGenericPassword       │
│  (x963Representation bytes)              │
│  Tag: "my.ssdid.wallet.eph_{alias}"      │
│  Created per generateWrappingKey() call   │
└────────────────────┬──────────────────────┘
                     │
          SharedSecret → HKDF-SHA256
          info = Data("ssdid-wrap-{alias}".utf8)
          salt = Data() (empty, per RFC 5869)
                     │
┌────────────────────▼──────────────────────┐
│  Derived AES-256 Symmetric Key            │
│  Lives only in memory, never persisted    │
│  Deterministic: same inputs → same key    │
└────────────────────┬──────────────────────┘
                     │
          AES-256-GCM encrypt/decrypt
          (random nonce per operation, via CryptoKit default)
                     │
┌────────────────────▼──────────────────────┐
│  Encrypted Private Key File               │
│  Documents/ssdid_vault/pk_*.enc           │
│  .completeFileProtection                  │
└───────────────────────────────────────────┘
```

## Key Derivation Flow

### generateWrappingKey(alias)

1. Ensure SE master key exists (create if first call — may trigger biometric prompt)
2. Generate a new ephemeral P-256 key pair in software via `P256.KeyAgreement.PrivateKey()`
3. Store the ephemeral **public key** in Keychain as `kSecClassGenericPassword` using `.x963Representation` serialization, under tag `eph_{alias}`
4. Discard the ephemeral private key — not needed after this step

The ECDH shared secret is computed on-demand in encrypt/decrypt. Since the SE master key (fixed) and the stored ephemeral public key (fixed per alias) are both deterministic inputs, the derived AES-256 key is the same on every access.

### deriveKey(alias) — internal helper

```swift
func deriveKey(alias: String) throws -> SymmetricKey {
    let ephPubData = try loadEphemeralPublicKey(alias: alias)
    let ephPub = try P256.KeyAgreement.PublicKey(x963Representation: ephPubData)
    let seMasterKey = try loadSEMasterKey()
    let sharedSecret = try seMasterKey.sharedSecretFromKeyAgreement(with: ephPub)
    return sharedSecret.hkdfDerivedSymmetricKey(
        using: SHA256.self,
        salt: Data(),
        sharedInfo: Data("ssdid-wrap-\(alias)".utf8),
        outputByteCount: 32
    )
}
```

### encrypt(alias, data)

1. `aesKey = deriveKey(alias)`
2. `sealedBox = AES.GCM.seal(data, using: aesKey)` — CryptoKit generates a random 12-byte nonce
3. Return `sealedBox.combined` (nonce + ciphertext + 16-byte tag)

### decrypt(alias, data)

1. `aesKey = deriveKey(alias)`
2. `sealedBox = AES.GCM.SealedBox(combined: data)`
3. Return `AES.GCM.open(sealedBox, using: aesKey)`

**Nonce safety:** The same derived key is reused across encrypt calls for the same alias. AES-GCM requires unique nonces per key. CryptoKit's `AES.GCM.seal` generates a cryptographically random 12-byte nonce by default, making nonce collision negligible (birthday bound at 2^48 operations).

## SE Master Key Management

- **Tag:** `my.ssdid.wallet.se_master`
- **Created:** Once, on first identity creation or first app launch after upgrade
- **Type:** `SecureEnclave.P256.KeyAgreement.PrivateKey`
- **Access control:** `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` + `.biometryCurrentSet`
- **Non-extractable:** Key material never leaves the Secure Enclave
- **Device-bound:** Does not sync via iCloud Keychain
- **Survives app updates:** Yes (Keychain persists)
- **Lost on device wipe:** Yes — user needs backup file + passphrase

### Biometric Prompt Frequency

Every `sharedSecretFromKeyAgreement()` call triggers biometric authentication (because the SE key has `.biometryCurrentSet`). To avoid repeated prompts within a single user action (e.g., backup exports all identities sequentially):

- Create a single `LAContext` per user-initiated operation
- Pass it to all SE key access calls within that operation via `kSecUseAuthenticationContext`
- The `LAContext` remains valid for its configured evaluation interval (default: ~10 seconds after successful auth)
- Do NOT cache the derived `SymmetricKey` beyond the operation scope

### Biometric Re-Enrollment Invalidation

`.biometryCurrentSet` means the SE master key is **invalidated if the user re-enrolls Face ID / Touch ID** (e.g., adds a new face, resets biometrics). When this happens:

1. All `decrypt()` calls will fail with `errSecAuthFailed`
2. Detect this in `loadSEMasterKey()` — if key load fails, check if it was invalidated
3. Show user: "Your biometrics have changed. Please restore your wallet from backup."
4. User restores from backup file + passphrase → new SE master key generated → all keys re-wrapped

This is the same UX as a device wipe. The backup file is the recovery mechanism.

### Fallback for Devices Without Secure Enclave

All modern iPhones (A7+, 2013+) and iPads have Secure Enclave. The iOS simulator does NOT.

- Detect SE availability via `SecureEnclave.isAvailable`
- Fall back to software `P256.KeyAgreement.PrivateKey` stored in Keychain (same ECDH flow, just not hardware-backed)
- Log a warning: "Secure Enclave not available, using software key agreement"

## Migration: Lazy Re-Wrap on First Access

Existing users have wrapping keys stored as `kSecClassGenericPassword` (raw AES-256 bytes). Migration happens transparently on first `decrypt()` call per identity.

### Detection

When `decrypt(alias, data)` is called:
1. Check if ephemeral public key exists for `alias` → **new format**, use SE-derived key
2. If not, check if legacy `kSecClassGenericPassword` exists for `alias` → **old format**, migrate
3. If neither → key not found error

### Migration Steps (executed in VaultImpl, not KeychainManager)

Migration runs in `VaultImpl.sign()` which has access to both `keychainManager` and `storage`:

```swift
func sign(keyId: String, data: Data) async throws -> Data {
    let identity = ...
    let wrappingAlias = "ssdid_wrap_\(did.methodSpecificId())"
    let encryptedPrivateKey = ...

    // Attempt SE-path decrypt
    if keychainManager.hasEphemeralKey(alias: wrappingAlias) {
        // New format — SE-derived key
        var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
        defer { privateKey.withUnsafeMutableBytes { memset($0.baseAddress!, 0, $0.count) } }
        return try provider(for: identity.algorithm).sign(...)
    }

    // Legacy format — migrate
    migrationLock.lock()
    defer { migrationLock.unlock() }

    // Double-check after acquiring lock (another thread may have migrated)
    if keychainManager.hasEphemeralKey(alias: wrappingAlias) {
        var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)
        defer { privateKey.withUnsafeMutableBytes { memset($0.baseAddress!, 0, $0.count) } }
        return try provider(for: identity.algorithm).sign(...)
    }

    // Decrypt with legacy key
    var privateKey = try keychainManager.decryptLegacy(alias: wrappingAlias, data: encryptedPrivateKey)
    defer { privateKey.withUnsafeMutableBytes { memset($0.baseAddress!, 0, $0.count) } }

    // Generate new SE-derived wrapping key
    try keychainManager.generateWrappingKey(alias: wrappingAlias)

    // Re-encrypt with new key
    let newEncrypted = try keychainManager.encrypt(alias: wrappingAlias, data: privateKey)

    // Atomic file write: write to temp, rename to final
    try await storage.saveIdentity(identity, encryptedPrivateKey: newEncrypted)

    // Delete old software key (safe — new ciphertext is persisted)
    keychainManager.deleteLegacyKey(alias: wrappingAlias)

    // Sign with the decrypted key
    return try provider(for: identity.algorithm).sign(...)
}
```

### Concurrency Safety

Add an `NSLock` to `VaultImpl` for migration serialization:

```swift
private let migrationLock = NSLock()
```

The lock is only held during migration (legacy path). SE-path decrypt does not acquire the lock.

### Crash Safety

If the app crashes between `saveIdentity` (new ciphertext written) and `deleteLegacyKey`:
- On next launch, `hasEphemeralKey(alias)` returns `true` (new key exists)
- Takes the SE path, decrypts with new key → works correctly
- Old legacy key is orphaned in Keychain but harmless (can be cleaned up lazily)

If the app crashes before `saveIdentity`:
- On next launch, `hasEphemeralKey(alias)` returns `false` (generateWrappingKey may have created it, but the file still has old ciphertext)
- Need to handle: if ephemeral key exists but decrypt fails, fall back to legacy key and retry migration
- Add a try/catch in the SE path that falls back to legacy on `AES.GCM` decrypt failure

### File writes

`FileVaultStorage.saveIdentity` already uses `.atomic` write option, which writes to a temp file and renames — safe against crash-during-write.

## Interface Changes

`KeychainManagerProtocol` — add migration helpers:

```swift
protocol KeychainManagerProtocol {
    // Existing
    func generateWrappingKey(alias: String) throws
    func encrypt(alias: String, data: Data) throws -> Data
    func decrypt(alias: String, data: Data) throws -> Data
    func deleteKey(alias: String)
    func hasKey(alias: String) -> Bool

    // New for migration
    func hasEphemeralKey(alias: String) -> Bool
    func decryptLegacy(alias: String, data: Data) throws -> Data
    func deleteLegacyKey(alias: String)
}
```

- `hasKey(alias:)` updated to return `true` if EITHER ephemeral key (new) or legacy key (old) exists
- `hasEphemeralKey(alias:)` checks only for the new-format ephemeral public key
- `decryptLegacy(alias:)` decrypts using the old `kSecClassGenericPassword` key
- `deleteLegacyKey(alias:)` deletes only the old-format key

## Security Properties

| Property | Before (Software) | After (Secure Enclave) |
|----------|-------------------|----------------------|
| Wrapping key extractable | Yes (Keychain read) | No (SE hardware) |
| Wrapping key in memory | Yes (full AES key loaded) | Derived SymmetricKey only (transient) |
| ECDH computation | N/A | Runs inside SE hardware |
| AES-GCM computation | In-process (CryptoKit) | In-process (CryptoKit) — same as before |
| Biometric protection | Optional flag | Built into SE access control |
| Device binding | `ThisDeviceOnly` flag | Hardware-enforced non-extractable |
| Survives jailbreak | Possibly extractable | SE remains isolated |

Note: Only the ECDH scalar multiplication runs on the SE's dedicated crypto processor. The HKDF derivation and AES-GCM encrypt/decrypt run in the application processor via CryptoKit. The key security gain is that the SE private key (the root secret) is non-extractable.

## Backup/Restore Compatibility

**No changes needed.**

- **Backup:** `BackupManager.createBackup()` calls `keychainManager.decrypt()` → gets raw private key → encrypts with user's passphrase. The SE derivation is transparent.
- **Restore:** `BackupManager.restoreBackup()` calls `keychainManager.generateWrappingKey()` (creates new SE-derived key on target device) → `keychainManager.encrypt()` (wraps with new key). Portable across devices.
- **Cross-device:** Each device has its own SE master key. Backup file + passphrase is the bridge.

## Error Handling

| Error | Cause | Recovery |
|-------|-------|----------|
| SE key creation fails | Device has no SE (simulator) | Fall back to software P-256 |
| Biometric cancelled | User dismissed Face ID prompt | Show "Authentication required" |
| Biometric invalidated | User re-enrolled Face ID | Show "Restore from backup" flow |
| SE key not found | App reinstalled (Keychain cleared) | Show "Restore from backup" flow |
| ECDH fails | Corrupted ephemeral key | Fall back to legacy key if available, else backup restore |
| AES-GCM decrypt fails on SE path | Crash during migration left stale ephemeral key | Fall back to legacy key, retry migration |

## Files to Change

| File | Change |
|------|--------|
| `KeychainManager.swift` | SE master key lifecycle, ECDH derivation, ephemeral key storage/load, legacy helpers |
| `VaultImpl.swift` | Migration logic in `sign()`, migration lock, fallback handling |
| `KeychainManagerProtocol` (in KeychainManager.swift) | Add `hasEphemeralKey`, `decryptLegacy`, `deleteLegacyKey` |

## Testing

- Unit test: ECDH derivation produces deterministic AES key from same SE key + ephemeral public key
- Unit test: encrypt/decrypt round-trip with SE-derived key
- Unit test: encrypt with different aliases produces different keys (domain separation)
- Unit test: migration detects old format and re-wraps
- Unit test: migration is idempotent (running twice doesn't break)
- Unit test: crash recovery — ephemeral key exists but ciphertext is old format → falls back to legacy
- Unit test: simulator fallback works when `SecureEnclave.isAvailable` is false
- Unit test: `hasKey` returns true for both legacy and new format
- Integration test: backup with old key → upgrade → restore creates new SE key

## Out of Scope

- Android changes (already uses hardware-backed Keystore)
- Changing the encrypted file format (stays AES-256-GCM nonce+ciphertext+tag)
- Changing the Vault/VaultStorage interface
- Changing BackupManager
- SE master key rotation (future consideration — would require re-wrapping all identities)
- Migrating UserDefaults metadata to encrypted file storage (separate concern)
