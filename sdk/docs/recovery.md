# Recovery

The SDK supports three recovery strategies to restore access to identities after key loss.

## Recovery Key

The simplest approach: generate a recovery key and store it securely offline. If the primary key is lost, use the recovery key to create a new identity linked to the same DID.

### Generate a Recovery Key

#### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!
sdk.recovery.generateRecoveryKey(identity).onSuccess { recoveryKeyBytes ->
    val recoveryKeyBase64 = Base64.encodeToString(recoveryKeyBytes, Base64.NO_WRAP)
    // Store this securely offline (e.g., printed QR code, hardware token)
    println("Recovery key generated. Store safely!")
}
```

#### Swift

```swift
let identity = try await sdk.vault.getIdentity(keyId: keyId)!
let recoveryKey = try await sdk.recoveryManager.generateRecoveryKey(identity: identity)
let recoveryKeyBase64 = recoveryKey.base64EncodedString()
// Store securely offline
```

### Check for Recovery Key

#### Kotlin

```kotlin
val hasKey = sdk.recovery.hasRecoveryKey(keyId)
println("Recovery key exists: $hasKey")
```

### Restore with Recovery Key

#### Kotlin

```kotlin
sdk.recovery.restoreWithRecoveryKey(
    did = "did:ssdid:abc123",
    recoveryPrivateKeyBase64 = recoveryKeyBase64,
    name = "Alice (recovered)",
    algorithm = Algorithm.ED25519
).onSuccess { newIdentity ->
    println("Identity restored: ${newIdentity.did}")
    println("New key ID: ${newIdentity.keyId}")
}
```

#### Swift

```swift
let restored = try await sdk.recoveryManager.restoreWithRecoveryKey(
    did: "did:ssdid:abc123",
    recoveryPrivateKeyBase64: recoveryKeyBase64,
    name: "Alice (recovered)",
    algorithm: .ed25519
)
print("Restored: \(restored.did)")
```

## Social Recovery (Shamir Secret Sharing)

Social recovery splits the recovery secret into multiple shares distributed among trusted contacts. A threshold number of shares (e.g., 3 of 5) is required to reconstruct the secret.

### Concept

1. The wallet generates a recovery secret and splits it into `n` shares using Shamir's Secret Sharing
2. Each share is distributed to a different trusted contact (guardian)
3. To recover, collect at least `t` (threshold) shares from guardians
4. The secret is reconstructed and used to restore the identity

This is useful when you do not want a single point of failure (one recovery key). The guardians individually cannot reconstruct the secret -- cooperation of the threshold number is required.

### Usage

Social recovery is managed through the `SocialRecoveryManager` in the domain layer. The `RecoveryManager` exposes this internally. Wallet applications typically build UI flows around:

1. **Setup:** Splitting the secret and distributing shares
2. **Collection:** Gathering shares from guardians during recovery
3. **Reconstruction:** Combining shares and restoring the identity

### Builder Configuration

On Android, you can provide a custom `SocialRecoveryStorage` implementation via the builder. The default uses DataStore.

```kotlin
SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .socialRecoveryStorage(MySocialRecoveryStorage())
    .build()
```

## Institutional Recovery

Institutional recovery delegates key custody to a trusted institution (e.g., an enterprise IT department or a regulated custodian).

### Concept

1. The institution holds an encrypted copy of the identity's recovery material
2. The user authenticates with the institution through an out-of-band process (e.g., in-person verification, multi-factor auth)
3. The institution releases the recovery material
4. The wallet restores the identity using the released material

This approach suits enterprise deployments where regulatory requirements demand custodial backup of identity keys.

### Builder Configuration

On Android, you can provide a custom `InstitutionalRecoveryStorage` implementation via the builder. The default uses DataStore.

```kotlin
SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .institutionalRecoveryStorage(MyInstitutionalRecoveryStorage())
    .build()
```

## Choosing a Strategy

| Strategy | Best For | Trade-off |
|----------|----------|-----------|
| Recovery Key | Individual users | Single point of failure |
| Social Recovery | Privacy-conscious users | Coordination overhead |
| Institutional | Enterprise/regulated | Custodial trust required |

## See Also

- [Backup](backup.md) -- encrypted vault backup/restore
- [Key Rotation](key-rotation.md) -- proactive key replacement
