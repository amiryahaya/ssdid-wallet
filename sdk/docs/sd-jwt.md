# SD-JWT

SD-JWT (Selective Disclosure JWT) allows credential holders to selectively disclose claims during presentations. The `sdk.sdJwt` API handles parsing, storage, and claim selection.

## Parse an SD-JWT

Parse a compact SD-JWT string into a structured `SdJwtVc` object. This extracts the issuer JWT, all disclosures, and the optional key binding JWT.

### Kotlin

```kotlin
val sdJwtVc = sdk.sdJwt.parse(compactSdJwt)
println("Issuer: ${sdJwtVc.issuerJwt}")
println("Disclosures: ${sdJwtVc.disclosures.size}")

// Inspect available claims
sdJwtVc.disclosures.forEach { disclosure ->
    println("Claim: ${disclosure.claimName} = ${disclosure.claimValue}")
}
```

### Swift

```swift
let sdJwtVc = SdJwtParser.parse(compactSdJwt)
print("Issuer JWT: \(sdJwtVc.issuerJwt)")
print("Disclosures: \(sdJwtVc.disclosures.count)")

for disclosure in sdJwtVc.disclosures {
    print("Claim: \(disclosure.claimName) = \(disclosure.claimValue)")
}
```

## Store an SD-JWT

Store a `StoredSdJwtVc` in the vault for later use in presentations.

### Kotlin

```kotlin
val stored = StoredSdJwtVc(
    id = "credential-123",
    did = identity.did,
    raw = compactSdJwt,
    issuer = "https://issuer.example.com",
    issuedAt = System.currentTimeMillis()
)
sdk.sdJwt.store(stored).onSuccess {
    println("SD-JWT stored")
}
```

### Swift

```swift
let stored = StoredSdJwtVc(
    id: "credential-123",
    did: identity.did,
    raw: compactSdJwt,
    issuer: "https://issuer.example.com",
    issuedAt: Date()
)
try await sdk.vault.storeStoredSdJwtVc(stored)
```

## List Stored SD-JWTs

### Kotlin

```kotlin
val sdJwts = sdk.sdJwt.list()
sdJwts.forEach { stored ->
    println("${stored.id} from ${stored.issuer}")
}
```

### Swift

```swift
let sdJwts = try await sdk.vault.listStoredSdJwtVcs()
for stored in sdJwts {
    print("\(stored.id) from \(stored.issuer)")
}
```

## Claim Selection for Presentations

When presenting an SD-JWT credential via OID4VP, you select which claims to disclose using the `selectedClaims` parameter on `sdk.presentation.submitPresentation()`. Only the disclosures matching the selected claim names are included in the presentation token.

### Kotlin

```kotlin
// Parse to see available claims
val sdJwtVc = sdk.sdJwt.parse(compactSdJwt)
val availableClaims = sdJwtVc.disclosures.map { it.claimName }
println("Available: $availableClaims")

// During presentation, select only what the verifier needs
sdk.presentation.submitPresentation(
    authRequest = authRequest,
    matchResult = matchResult,
    selectedClaims = listOf("given_name", "family_name"),
    algorithm = identity.algorithm.w3cType,
    signer = { data -> sdk.vault.sign(identity.keyId, data).getOrThrow() }
)
```

## See Also

- [Presentations](presentations.md) -- full OID4VP flow
- [Credentials](credentials.md) -- standard VC storage
