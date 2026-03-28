# Key Rotation

Key rotation replaces an identity's active key pair with a new one using the KERI (Key Event Receipt Infrastructure) pre-commitment pattern. The old key signs a rotation event that authorizes the new key, ensuring continuity of the DID.

## How It Works

1. **Prepare:** A new key pair is generated and its commitment hash is stored in the DID Document as a pre-rotation commitment. The new key is not yet active.
2. **Execute:** The old key signs a rotation event that activates the new key. The DID Document is updated on the registry with the new verification method.
3. The DID remains the same; only the key material changes.

## Prepare Rotation

Generates the next key pair and registers the pre-rotation commitment. Call this well before you need to rotate.

### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!
sdk.rotation.prepare(identity).onSuccess { commitmentHash ->
    println("Pre-rotation commitment: $commitmentHash")
    println("Rotation is now ready to execute when needed")
}
```

### Swift

```swift
let identity = try await sdk.vault.getIdentity(keyId: keyId)!
let commitment = try await sdk.keyRotationManager.prepareRotation(identity: identity)
print("Pre-rotation commitment: \(commitment)")
```

## Execute Rotation

Activates the pre-committed key and updates the DID Document on the registry.

### Kotlin

```kotlin
sdk.rotation.execute(identity).onSuccess { newIdentity ->
    println("Key rotated successfully")
    println("New key ID: ${newIdentity.keyId}")
    println("DID unchanged: ${newIdentity.did}")
    // Update your local reference to the identity
}
```

### Swift

```swift
let newIdentity = try await sdk.keyRotationManager.executeRotation(identity: identity)
print("New key ID: \(newIdentity.keyId)")
```

## Check Rotation Status

Query whether a rotation has been prepared and is ready to execute.

### Kotlin

```kotlin
val status = sdk.rotation.getStatus(identity)
println("Status: $status")
// RotationStatus values: NOT_PREPARED, PREPARED, ROTATED
```

### Swift

```swift
let status = try await sdk.keyRotationManager.getRotationStatus(identity: identity)
print("Status: \(status)")
```

## When to Rotate Keys

Rotate keys proactively in these situations:

- **Scheduled rotation:** Regular rotation (e.g., every 90 days) as a security hygiene practice
- **Suspected compromise:** If the device was lost, stolen, or potentially compromised
- **Algorithm upgrade:** Moving from a classical algorithm to post-quantum
- **Compliance requirements:** Some regulations mandate periodic key rotation
- **Device migration:** When transferring identity to a new device

Best practice: always call `prepare()` early so you have a pre-committed key ready to activate immediately if needed.

## See Also

- [Recovery](recovery.md) -- restoring access after key loss
- [Identity Management](identity-management.md) -- identity lifecycle
- [Device Management](device-management.md) -- multi-device key management
