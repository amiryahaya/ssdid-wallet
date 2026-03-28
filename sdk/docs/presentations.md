# Presentations

The SDK supports OID4VP (OpenID for Verifiable Presentations) for responding to verification requests from relying parties.

## OID4VP Flow

### Step 1: Process the Request

Parse an authorization request URI received from a verifier (via QR code or deep link).

#### Kotlin

```kotlin
val reviewResult = sdk.presentation.processRequest(requestUri)
reviewResult.onSuccess { review ->
    println("Verifier: ${review.authRequest.clientId}")
    println("Requested credentials: ${review.matchResult}")
    // review.matchResult contains which of your credentials match
}
```

#### Swift

```swift
let handler = OpenId4VpHandler(/* ... */)
let review = try await handler.processRequest(uri: requestUri)
print("Verifier: \(review.authRequest.clientId)")
```

### Step 2: Select Claims and Submit

Choose which claims to disclose (for selective disclosure) and submit the presentation. The signer parameter takes a `CredentialSigner` (see [Credentials](credentials.md#credentialsigner)).

#### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!

sdk.presentation.submitPresentation(
    authRequest = review.authRequest,
    matchResult = review.matchResult,
    selectedClaims = listOf("name", "dateOfBirth"), // selective disclosure
    algorithm = identity.algorithm.w3cType,
    signer = CredentialSigner { data ->
        sdk.vault.sign(identity.keyId, data).getOrThrow()
    }
).onSuccess {
    println("Presentation submitted successfully")
}
```

## Selective Disclosure with SD-JWT

When presenting SD-JWT credentials, you control exactly which claims the verifier sees. The `selectedClaims` parameter specifies the claim names to disclose. All other claims remain hidden.

#### Kotlin

```kotlin
// Only disclose name and country -- age, address, etc. stay hidden
sdk.presentation.submitPresentation(
    authRequest = review.authRequest,
    matchResult = review.matchResult,
    selectedClaims = listOf("given_name", "country"),
    algorithm = identity.algorithm.w3cType,
    signer = CredentialSigner { data ->
        sdk.vault.sign(identity.keyId, data).getOrThrow()
    }
)
```

## Presentation Definition Matching

The SDK automatically matches your stored credentials against the verifier's `PresentationDefinition` or DCQL query. The `matchResult` in the review object tells you which credentials satisfy the request and which claims are requested.

### Kotlin

```kotlin
reviewResult.onSuccess { review ->
    val match = review.matchResult
    // Inspect which credentials matched
    // Let the user confirm before submitting
}
```

## See Also

- [SD-JWT](sd-jwt.md) -- parsing and storing SD-JWT credentials
- [Credentials](credentials.md) -- credential storage and retrieval
- [Verification](verification.md) -- verifying credentials you receive
