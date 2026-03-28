# Backup

The SDK provides encrypted backup and restore of the entire vault (identities, credentials, and key material).

## Create a Backup

Encrypts all vault data with a passphrase and returns the backup as a byte array. The passphrase is used to derive an encryption key via a KDF.

### Kotlin

```kotlin
val passphrase = "strong-user-chosen-passphrase".toCharArray()
sdk.backup.create(passphrase).onSuccess { backupData ->
    // Save backupData to file, cloud storage, etc.
    println("Backup created: ${backupData.size} bytes")
}
// Clear passphrase from memory
passphrase.fill(' ')
```

### Swift

```swift
let passphrase = Array("strong-user-chosen-passphrase".utf8)
let backupData = try await sdk.backupManager.createBackup(passphrase: passphrase)
// Save backupData to file, iCloud, etc.
print("Backup created: \(backupData.count) bytes")
```

## Restore from Backup

Decrypts backup data and restores all identities and credentials into the vault. Returns the number of identities restored.

### Kotlin

```kotlin
val passphrase = "strong-user-chosen-passphrase".toCharArray()
sdk.backup.restore(backupData, passphrase).onSuccess { count ->
    println("Restored $count identities")
}
passphrase.fill(' ')
```

### Swift

```swift
let passphrase = Array("strong-user-chosen-passphrase".utf8)
let count = try await sdk.backupManager.restoreBackup(backupData: backupData, passphrase: passphrase)
print("Restored \(count) identities")
```

## Backup Format Overview

The backup is a self-contained encrypted blob with the following structure:

1. **Header:** Format version and encryption parameters (salt, IV, iteration count)
2. **Encrypted payload:** All vault entries serialized as JSON, then encrypted with AES-256-GCM
3. **Key derivation:** The passphrase is run through PBKDF2 (or Argon2, depending on platform) to produce the AES key

The backup includes:
- All identity records (DID, key material, algorithm, metadata)
- All stored verifiable credentials
- All stored SD-JWT credentials
- Recovery key material (if generated)

The backup does **not** include:
- Activity/audit logs
- Notification state
- Offline verification bundles (these can be re-fetched)

## Security Considerations

- Use a strong passphrase (16+ characters recommended)
- Clear the passphrase from memory after use (`CharArray.fill()` in Kotlin)
- Store backups in a secure location (encrypted cloud storage or offline media)
- Consider creating a new backup after key rotation or identity changes
- The backup format is forward-compatible; newer SDK versions can read older backups

## See Also

- [Recovery](recovery.md) -- recovery without a full backup
- [Key Rotation](key-rotation.md) -- rotating keys after restore
