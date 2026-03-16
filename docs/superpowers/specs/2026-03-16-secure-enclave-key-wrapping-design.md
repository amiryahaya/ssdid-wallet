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
│  Stored in Keychain as kSecClassKey       │
│  Tag: "my.ssdid.wallet.eph_{alias}"      │
│  Created per generateWrappingKey() call   │
└────────────────────┬──────────────────────┘
                     │
          SharedSecret → HKDF-SHA256
          info = "ssdid-wrap-{alias}"
                     │
┌────────────────────▼──────────────────────┐
│  Derived AES-256 Symmetric Key            │
│  Lives only in memory, never persisted    │
│  Deterministic: same inputs → same key    │
└────────────────────┬──────────────────────┘
                     │
          AES-256-GCM encrypt/decrypt
                     │
┌────────────────────▼──────────────────────┐
│  Encrypted Private Key File               │
│  Documents/ssdid_vault/pk_*.enc           │
│  .completeFileProtection                  │
└───────────────────────────────────────────┘
```

## Key Derivation Flow

### generateWrappingKey(alias)

1. Ensure SE master key exists (create if first call)
2. Generate a new ephemeral P-256 key pair (in software, via CryptoKit)
3. Store the ephemeral **public key** in Keychain under tag `eph_{alias}`
4. Discard the ephemeral private key — it's only used once to create the pair
5. The wrapping key will be derived on-demand in encrypt/decrypt

Wait — correction. ECDH requires both a private key and the other party's public key. The SE has the private key. We need the ephemeral public key for the other side. But ECDH is `SE_private × ephemeral_public`. To make this work:

**Revised approach:**
1. Generate ephemeral P-256 key pair in software
2. Perform ECDH: `sharedSecret = SE_private_key.sharedSecret(with: ephemeral_public_key)`
3. Derive AES key: `HKDF<SHA256>.deriveKey(sharedSecret, salt: [], info: "ssdid-wrap-{alias}", outputByteCount: 32)`
4. Store `ephemeral_public_key` in Keychain (needed for re-derivation)
5. The ephemeral private key is **not needed** after step 2 — discard it

Wait — that's wrong too. ECDH needs `our_private × their_public`. If the SE holds the private key, we do `SE.sharedSecret(with: ephemeral_public)`. But to re-derive later, we need to reproduce the same shared secret, which requires the same `ephemeral_public`. Since the SE private key is fixed, and the ephemeral public key is stored, the shared secret is deterministic. ✅

But actually, for the initial `sharedSecret` call, CryptoKit's `SecureEnclave.P256.KeyAgreement.PrivateKey` does `privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)`. This works. On subsequent calls, we reload the ephemeral public key from Keychain and call the SE private key again. Same inputs → same shared secret → same derived AES key. ✅

### encrypt(alias, data)

1. Load ephemeral public key for `alias` from Keychain
2. Load SE master private key reference
3. `sharedSecret = seMasterKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)`
4. `aesKey = HKDF<SHA256>.deriveKey(sharedSecret, info: "ssdid-wrap-{alias}".data, outputByteCount: 32)`
5. `sealedBox = AES.GCM.seal(data, using: aesKey)`
6. Return `sealedBox.combined`

### decrypt(alias, data)

Same as encrypt steps 1-4, then:
5. `sealedBox = AES.GCM.SealedBox(combined: data)`
6. `return AES.GCM.open(sealedBox, using: aesKey)`

## SE Master Key Management

- **Tag:** `my.ssdid.wallet.se_master`
- **Created:** Once, on first identity creation (or first app launch after upgrade)
- **Type:** `SecureEnclave.P256.KeyAgreement.PrivateKey`
- **Access control:** `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` + `.biometryCurrentSet`
- **Non-extractable:** Key material never leaves the Secure Enclave
- **Device-bound:** Does not sync via iCloud Keychain
- **Survives app updates:** Yes (Keychain persists across updates)
- **Lost on device wipe:** Yes — user needs backup file + passphrase to restore

### Fallback for Devices Without Secure Enclave

All modern iPhones (iPhone 5s+, 2013+) and iPads have Secure Enclave. The iOS simulator does NOT. For simulator builds:
- Detect SE availability via `SecureEnclave.isAvailable`
- Fall back to software P-256 key stored in Keychain (same ECDH flow, just not hardware-backed)
- Log a warning: "Secure Enclave not available, using software key agreement"

## Migration: Lazy Re-Wrap on First Access

Existing users have wrapping keys stored as `kSecClassGenericPassword` (raw AES-256 bytes). Migration happens transparently on first `decrypt()` call per identity.

### Detection

When `decrypt(alias, data)` is called:
1. Check if ephemeral public key exists for `alias` → **new format**, use SE-derived key
2. If not, check if `kSecClassGenericPassword` exists for `alias` → **old format**, migrate

### Migration Steps

```
decrypt(alias, encryptedPrivateKey):
  1. ephemeralKey = loadEphemeralPublicKey(alias)
  2. if ephemeralKey != nil:
       → SE path: derive AES key via ECDH, decrypt, return
  3. else:
       → Legacy path:
       a. oldAesKey = loadLegacyKey(alias)          // old kSecClassGenericPassword
       b. rawPrivateKey = AES.GCM.open(data, oldAesKey)
       c. generateWrappingKey(alias)                  // creates new SE-derived key
       d. newEncrypted = encrypt(alias, rawPrivateKey) // re-encrypt with SE key
       e. save newEncrypted to disk (overwrite pk_*.enc file)
       f. deleteLegacyKey(alias)                      // remove old software key
       g. zero(rawPrivateKey)                          // scrub memory
       h. return rawPrivateKey
```

### Safety

- If migration fails at any step, the old key is still intact (deletion is last)
- If the app crashes between steps (e) and (f), next launch will detect old key still exists and re-migrate (idempotent — re-encrypting an already-migrated key is a no-op since the old key is gone)
- Private key raw bytes exist in memory only during the migration window

## Interface Changes

`KeychainManagerProtocol` — **no changes**. The interface stays:
```swift
func generateWrappingKey(alias: String) throws
func encrypt(alias: String, data: Data) throws -> Data
func decrypt(alias: String, data: Data) throws -> Data
func deleteKey(alias: String)
func hasKey(alias: String) -> Bool
```

All changes are internal to `KeychainManager`. No other files need modification except for the migration integration (VaultImpl needs to pass the encrypted file path for re-save during migration, or KeychainManager needs a callback/delegate for the re-save).

### Migration Integration Option

Since migration needs to re-save the encrypted file, and `KeychainManager` doesn't know about file storage, the cleanest approach is:

**Option: Migrate in VaultImpl.decrypt path**

`VaultImpl.sign()` already calls `keychainManager.decrypt()` and has access to `storage`. Add a post-decrypt migration check:

```swift
func sign(keyId: String, data: Data) async throws -> Data {
    // ... existing code to get identity, wrappingAlias, encryptedPrivateKey ...

    var privateKey = try keychainManager.decrypt(alias: wrappingAlias, data: encryptedPrivateKey)

    // Migrate if this key was decrypted with legacy software key
    if keychainManager.needsMigration(alias: wrappingAlias) {
        let newEncrypted = try keychainManager.encrypt(alias: wrappingAlias, data: privateKey)
        try await storage.saveIdentity(identity, encryptedPrivateKey: newEncrypted) // overwrite
        keychainManager.deleteLegacyKey(alias: wrappingAlias)
    }

    // ... sign and return ...
}
```

This keeps the migration logic in the domain layer where it has access to both keychain and storage.

## Backup/Restore Compatibility

**No changes needed.**

- **Backup:** `BackupManager.createBackup()` calls `keychainManager.decrypt()` → gets raw private key → encrypts with passphrase. The ECDH derivation is transparent.
- **Restore:** `BackupManager.restoreBackup()` calls `keychainManager.generateWrappingKey()` (creates new SE-derived key on target device) → `keychainManager.encrypt()` (wraps with new key). Portable across devices.
- **Cross-device:** Each device has its own SE master key. Backup file + passphrase is the bridge.

## Security Properties

| Property | Before (Software) | After (Secure Enclave) |
|----------|-------------------|----------------------|
| Wrapping key extractable | Yes (Keychain read) | No (SE hardware) |
| Wrapping key in memory | Yes (loaded on decrypt) | No (derived in SE, only shared secret exposed) |
| Biometric protection | Optional flag | Built into SE access control |
| Device binding | `ThisDeviceOnly` flag | Hardware-enforced |
| Survives jailbreak | Possibly extractable | SE remains isolated |
| Side-channel resistance | None | SE has dedicated crypto processor |

## Files to Change

| File | Change |
|------|--------|
| `KeychainManager.swift` | Add SE master key management, ECDH derivation, ephemeral key storage, legacy detection, migration helpers |
| `VaultImpl.swift` | Add post-decrypt migration check in `sign()` and `updateIdentityProfile()` |
| `KeychainManagerProtocol` | Add `needsMigration(alias:)` and `deleteLegacyKey(alias:)` methods |

## Testing

- Unit test: ECDH derivation produces deterministic AES key from same SE key + ephemeral public key
- Unit test: encrypt/decrypt round-trip with SE-derived key
- Unit test: migration detects old format and re-wraps
- Unit test: migration is idempotent (running twice doesn't break)
- Unit test: simulator fallback works when SE unavailable
- Integration test: backup → restore across migration boundary (backup with old key, restore creates new SE key)

## Out of Scope

- Android changes (already uses hardware-backed Keystore)
- Changing the encrypted file format (stays AES-256-GCM)
- Changing the Vault/VaultStorage interface
- Changing BackupManager
