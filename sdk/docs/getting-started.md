# Getting Started

This guide walks you through adding the SSDID SDK to your project, initializing it, creating an identity, and signing data.

## Installation

### Android (Gradle / GitHub Packages)

Add the GitHub Packages repository and dependency to your module-level `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/amiryahaya/ssdid-wallet")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("my.ssdid.sdk:ssdid-core:0.1.0")
    // Optional — add PQC (KAZ-Sign) support:
    implementation("my.ssdid.sdk:ssdid-pqc:0.1.0")
}
```

Store credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

### iOS (Swift Package Manager)

In Xcode, go to **File > Add Package Dependencies** and enter:

```
https://github.com/amiryahaya/ssdid-wallet
```

Select the `SsdidCore` library. For PQC support, also add `SsdidPqc`.

Alternatively, add to `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/amiryahaya/ssdid-wallet", from: "0.1.0")
],
targets: [
    .target(name: "YourApp", dependencies: [
        .product(name: "SsdidCore", package: "ssdid-wallet"),
    ])
]
```

## Initialization

### Kotlin

```kotlin
import my.ssdid.sdk.SsdidSdk

val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .build()
```

### Swift

```swift
import SsdidCore

let sdk = SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .build()
```

## Create Your First Identity

### Kotlin

```kotlin
import my.ssdid.sdk.domain.model.Algorithm

val result = sdk.identity.create("Alice", Algorithm.ED25519)
result.onSuccess { identity ->
    println("DID: ${identity.did}")
    println("Key ID: ${identity.keyId}")
}
```

### Swift

```swift
let identity = try await sdk.client.initIdentity(name: "Alice", algorithm: .ed25519)
print("DID: \(identity.did)")
print("Key ID: \(identity.keyId)")
```

## Sign Data

### Kotlin

```kotlin
val data = "Hello, SSDID!".toByteArray()
val signature = sdk.vault.sign(identity.keyId, data)
signature.onSuccess { sig ->
    println("Signature: ${sig.size} bytes")
}
```

### Swift

```swift
let data = "Hello, SSDID!".data(using: .utf8)!
let signature = try await sdk.vault.sign(keyId: identity.keyId, data: data)
print("Signature: \(signature.count) bytes")
```

## Next Steps

- [Configuration](configuration.md) -- all builder options
- [Identity Management](identity-management.md) -- full identity lifecycle
- [Credentials](credentials.md) -- OID4VCI issuance and storage
