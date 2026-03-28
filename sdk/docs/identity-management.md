# Identity Management

The `sdk.identity` API manages the full lifecycle of decentralized identities (DIDs).

## Create an Identity

`create(name, algorithm)` generates a key pair, creates a DID, and registers it with the SSDID Registry.

### Algorithm Selection Guide

| Algorithm | Use Case | Post-Quantum |
|-----------|----------|:------------:|
| `ED25519` | General purpose, fast, recommended default | No |
| `ECDSA_P256` | Legacy system interop (WebAuthn, JOSE) | No |
| `ECDSA_P384` | Higher security classical curves | No |
| `KAZ_SIGN_128` | Future-proof, quantum-resistant | Yes |
| `KAZ_SIGN_192` | Higher PQC security level | Yes |
| `KAZ_SIGN_256` | Maximum PQC security level | Yes |

### Kotlin

```kotlin
import my.ssdid.sdk.domain.model.Algorithm

// Recommended default
val result = sdk.identity.create("Alice", Algorithm.ED25519)
result.onSuccess { identity ->
    println("DID: ${identity.did}")
    println("Key ID: ${identity.keyId}")
    println("Algorithm: ${identity.algorithm}")
}

// Post-quantum (requires ssdid-pqc module)
val pqcResult = sdk.identity.create("Alice PQC", Algorithm.KAZ_SIGN_128)
```

### Swift

```swift
let identity = try await sdk.client.initIdentity(name: "Alice", algorithm: .ed25519)
print("DID: \(identity.did)")
```

## List Identities

### Kotlin

```kotlin
val identities = sdk.identity.list()
identities.forEach { println("${it.name}: ${it.did}") }
```

### Swift

```swift
let identities = try await sdk.vault.listIdentities()
for id in identities {
    print("\(id.name): \(id.did)")
}
```

## Get a Specific Identity

### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)  // returns Identity?
```

### Swift

```swift
let identity = try await sdk.vault.getIdentity(keyId: keyId)
```

## Delete an Identity

Removes the identity from the local vault. Does **not** deactivate the DID on the registry.

### Kotlin

```kotlin
sdk.identity.delete(keyId).onSuccess {
    println("Identity deleted locally")
}
```

### Swift

```swift
try await sdk.vault.deleteIdentity(keyId: keyId)
```

## Build a DID Document

Constructs a W3C DID Core 1.1 compliant DID Document from the local identity.

### Kotlin

```kotlin
sdk.identity.buildDidDocument(keyId).onSuccess { doc ->
    println("DID Document ID: ${doc.id}")
    println("Verification methods: ${doc.verificationMethod.size}")
}
```

### Swift

```swift
let doc = try await sdk.vault.buildDidDocument(keyId: keyId)
print("DID Document ID: \(doc.id)")
```

## Update a DID Document

Pushes the current local DID Document to the SSDID Registry.

### Kotlin

```kotlin
sdk.identity.updateDidDocument(keyId).onSuccess {
    println("DID Document updated on registry")
}
```

## Deactivate a DID

Permanently deactivates the DID on the SSDID Registry.

**Warning:** This operation is irreversible. The DID can never be used again after deactivation.

### Kotlin

```kotlin
sdk.identity.deactivate(keyId).onSuccess {
    println("DID deactivated permanently")
}
```

### Swift

```swift
try await sdk.client.deactivateDid(keyId: keyId)
```
