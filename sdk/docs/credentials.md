# Credentials

The SDK supports credential issuance via OID4VCI, local storage, and retrieval.

## CredentialSigner

Signing operations in the issuance and presentation flows use the `CredentialSigner` interface instead of a raw lambda. This is a `fun interface` (Kotlin) / closure (Swift), so you can pass a lambda directly.

### Kotlin

```kotlin
fun interface CredentialSigner {
    suspend fun sign(data: ByteArray): ByteArray
}
```

### Swift

On iOS, the signer parameter is a `@Sendable (Data) -> Data` closure.

## OID4VCI Issuance Flow

The OID4VCI (OpenID for Verifiable Credential Issuance) flow has two steps: parse the offer, then accept it.

### Step 1: Process the Offer

Parse a credential offer URI received from an issuer (typically via QR code or deep link).

#### Kotlin

```kotlin
val reviewResult = sdk.issuance.processOffer(offerUri)
reviewResult.onSuccess { review ->
    println("Issuer: ${review.metadata.credentialIssuer}")
    println("Available credentials: ${review.metadata.credentialConfigurationsSupported.keys}")
    // Pick a credential configuration to accept
    val selectedConfigId = review.metadata.credentialConfigurationsSupported.keys.first()
}
```

#### Swift

```swift
let handler = OpenId4VciHandler(/* ... */)
let review = try handler.processOffer(uri: offerUri)
print("Issuer: \(review.metadata.credentialIssuer)")
```

### Step 2: Accept the Offer

Accept the offer with the selected configuration. You provide a `CredentialSigner` that uses the vault to sign the proof JWT.

#### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!
val issuanceResult = sdk.issuance.acceptOffer(
    offer = review.offer,
    metadata = review.metadata,
    selectedConfigId = selectedConfigId,
    txCode = null, // PIN code if required by issuer
    walletDid = identity.did,
    keyId = identity.keyId,
    algorithm = identity.algorithm.w3cType,
    signer = CredentialSigner { data ->
        sdk.vault.sign(identity.keyId, data).getOrThrow()
    }
)
issuanceResult.onSuccess { result ->
    println("Credential issued: ${result.credential}")
}
```

### Processing Raw JSON Offers (iOS)

On iOS, you can also process a credential offer from raw JSON (e.g., when the offer is embedded in a notification payload rather than a URI).

#### Swift

```swift
let review = try await sdk.issuance.processOfferJson(json)
print("Issuer: \(review.metadata.credentialIssuer)")
```

## Store a Credential

Store a `VerifiableCredential` in the local vault.

### Kotlin

```kotlin
sdk.credentials.store(credential).onSuccess {
    println("Credential stored")
}
```

### Swift

```swift
try await sdk.vault.storeCredential(credential)
```

## List Credentials

### Kotlin

```kotlin
val credentials = sdk.credentials.list()
credentials.forEach { vc ->
    println("${vc.type}: ${vc.id}")
}
```

### Swift

```swift
let credentials = try await sdk.vault.listCredentials()
for vc in credentials {
    print("\(vc.type): \(vc.id)")
}
```

## Get Credentials for a DID

Retrieve all credentials associated with a specific DID.

### Kotlin

```kotlin
val creds = sdk.credentials.getForDid(identity.did)
```

### Swift

```swift
let creds = try await sdk.vault.getCredentialsForDid(did: identity.did)
```

## Delete a Credential

### Kotlin

```kotlin
sdk.credentials.delete(credentialId).onSuccess {
    println("Credential deleted")
}
```

### Swift

```swift
try await sdk.vault.deleteCredential(credentialId: credentialId)
```
