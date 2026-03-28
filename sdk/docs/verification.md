# Verification

The SDK provides three verification modes: online, offline, and hybrid. Use the mode that fits your connectivity scenario.

## Online Verification

Online verification resolves the issuer's DID from the registry and checks the credential signature in real time.

### Verify a Credential

#### Kotlin

```kotlin
sdk.verifier.verifyCredential(credential).onSuccess { valid ->
    println("Credential valid: $valid")
}
```

#### Swift

```swift
let verifier = sdk.verificationOrchestrator
let result = try await verifier.verify(credential: credential)
print("Valid: \(result.isValid)")
```

### Verify a Raw Signature

#### Kotlin

```kotlin
sdk.verifier.verifySignature(
    did = identity.did,
    keyId = identity.keyId,
    signature = signatureBytes,
    data = originalData
).onSuccess { valid ->
    println("Signature valid: $valid")
}
```

### Resolve a DID

#### Kotlin

```kotlin
sdk.verifier.resolveDid("did:ssdid:abc123").onSuccess { didDocument ->
    println("DID Document: ${didDocument.id}")
    didDocument.verificationMethod.forEach { vm ->
        println("  Key: ${vm.id} (${vm.type})")
    }
}
```

## Offline Verification

Offline verification uses pre-fetched verification bundles (DID Documents and revocation status lists). Ideal for air-gapped environments or poor connectivity.

### Prefetch a Bundle

Download and cache the verification bundle for an issuer before going offline.

#### Kotlin

```kotlin
sdk.offline.prefetchBundle(
    issuerDid = "did:ssdid:issuer123",
    statusListUrl = "https://issuer.example.com/status/1"
).onSuccess { bundle ->
    println("Bundle cached, expires: ${bundle.expiresAt}")
}
```

### Check Bundle Freshness

#### Kotlin

```kotlin
val hasFresh = sdk.offline.hasFreshBundle("did:ssdid:issuer123")
if (!hasFresh) {
    sdk.offline.prefetchBundle("did:ssdid:issuer123")
}
```

### Verify Offline

#### Kotlin

```kotlin
val result = sdk.offline.verifyOffline(credential)
println("Offline valid: ${result.isValid}")
println("Bundle age: ${result.bundleAge}")
```

### Refresh Stale Bundles

Refresh all bundles that have exceeded their TTL.

#### Kotlin

```kotlin
val refreshed = sdk.offline.refreshStaleBundles()
println("Refreshed $refreshed bundles")
```

## Hybrid Verification (Orchestrator)

The `VerificationOrchestrator` automatically tries online verification first, falling back to offline if the network is unavailable.

### Kotlin

```kotlin
val result = sdk.offline.verify(credential)
println("Valid: ${result.isValid}")
println("Mode: ${result.mode}") // ONLINE or OFFLINE
```

### Swift

```swift
let result = try await sdk.verificationOrchestrator.verify(credential: credential)
print("Valid: \(result.isValid)")
print("Mode: \(result.mode)")
```

## See Also

- [Credentials](credentials.md) -- storing and retrieving credentials
- [Presentations](presentations.md) -- presenting credentials to verifiers
