# SSDID Wallet & SDK

Self-sovereign decentralized identity (SSI) wallet with post-quantum cryptography (PQC) support. Dual-platform: Android (Kotlin/Compose) and iOS (Swift/SwiftUI).

## Overview

This repository contains:
- **SSDID SDK** — reusable identity SDK for Android and iOS
- **SSDID Wallet** — reference wallet app built on the SDK

## SDK

The SDK provides self-sovereign identity capabilities as a library:

### Android

| Module | Artifact | Description |
|--------|----------|-------------|
| `ssdid-core` | `my.ssdid.sdk:ssdid-core` | Core identity, credentials, verification, recovery |
| `ssdid-pqc` | `my.ssdid.sdk:ssdid-pqc` | Optional post-quantum cryptography (KAZ-Sign) |
| `ssdid-core-testing` | `my.ssdid.sdk:ssdid-core-testing` | Test doubles for SDK consumers |

### iOS

| Package | Description |
|---------|-------------|
| `SsdidCore` | Core identity, credentials, verification, recovery |
| `SsdidPqc` | Optional post-quantum cryptography |

### Quick Start (Android)

```kotlin
// Initialize
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .build()

// Create identity
val identity = sdk.identity.create("Alice", Algorithm.ED25519).getOrThrow()

// Sign data
val signature = sdk.vault.sign(identity.keyId, data).getOrThrow()
```

### Quick Start (iOS)

```swift
let sdk = SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .build()

let identity = try await sdk.identity.create(name: "Alice", algorithm: .ed25519)
```

### SDK Capabilities

16 capability areas accessible through `sdk.*`:

| API | Description |
|-----|-------------|
| `sdk.identity` | Create, list, delete DIDs |
| `sdk.vault` | Sign data, create proofs |
| `sdk.credentials` | Store and manage verifiable credentials |
| `sdk.flows` | Register with services, authenticate, sign transactions |
| `sdk.issuance` | OID4VCI credential issuance |
| `sdk.presentation` | OID4VP credential presentation |
| `sdk.sdJwt` | SD-JWT parsing and storage |
| `sdk.verifier` | Verify credentials and signatures |
| `sdk.offline` | Offline credential verification |
| `sdk.recovery` | Recovery key, social (Shamir), institutional |
| `sdk.rotation` | KERI key pre-commitment rotation |
| `sdk.backup` | Encrypted backup/restore |
| `sdk.device` | Multi-device management |
| `sdk.notifications` | Push notification inbox |
| `sdk.revocation` | Credential revocation checking |
| `sdk.history` | Activity audit log |

### Documentation

See [sdk/docs/](sdk/docs/) for complete guides:
- [Getting Started](sdk/docs/getting-started.md)
- [Configuration](sdk/docs/configuration.md)
- [Identity Management](sdk/docs/identity-management.md)
- [Credentials](sdk/docs/credentials.md)
- [Presentations](sdk/docs/presentations.md)
- [Verification](sdk/docs/verification.md)
- [Recovery](sdk/docs/recovery.md)
- [Key Rotation](sdk/docs/key-rotation.md)
- [Custom Implementations](sdk/docs/custom-implementations.md)
- [Migration Guide](sdk/docs/migration-guide.md)

### Sample Apps

- `sdk/samples/android/basic-identity/` — minimal identity demo
- `sdk/samples/android/credential-flow/` — OID4VCI + OID4VP flow
- `sdk/samples/android/custom-storage/` — custom VaultStorage override
- `sdk/samples/ios/BasicIdentity/` — iOS identity demo
- `sdk/samples/ios/CredentialFlow/` — iOS credential flow
- `sdk/samples/ios/CustomStorage/` — iOS custom storage

## Wallet App

The wallet app is a full-featured SSI wallet built on the SDK.

### Build

```bash
# Android
cd android && ./gradlew assembleDebug

# iOS
open ios/SsdidWallet.xcodeproj
```

### Features

- Create and manage decentralized identities (DIDs)
- Receive and present verifiable credentials (OID4VCI/OID4VP)
- SD-JWT selective disclosure
- Post-quantum cryptography (KAZ-Sign)
- Offline credential verification
- Key recovery (personal, social via Shamir, institutional)
- KERI key rotation with pre-commitments
- Encrypted backup/restore
- Multi-device management
- Push notifications
- Biometric lock

## Architecture

```
ssdid-wallet/
├── android/
│   ├── app/                    # Wallet app
│   └── sdk/
│       ├── ssdid-core/         # SDK core module
│       ├── ssdid-core-testing/ # Test doubles
│       ├── ssdid-pqc/          # PQC module
│       └── samples/            # Sample apps
├── ios/
│   └── SsdidWallet/            # iOS wallet app
├── sdk/
│   ├── docs/                   # SDK documentation
│   ├── ios/
│   │   ├── SsdidCore/          # iOS SDK package
│   │   └── SsdidPqc/          # iOS PQC package
│   └── samples/ios/            # iOS sample apps
└── .github/workflows/          # CI pipelines
```

## Security

- Hardware-backed key storage (Android Keystore / iOS Secure Enclave)
- Biometric authentication for vault access
- Certificate pinning for registry and notification services
- AES-256-GCM key wrapping
- Post-quantum cryptography support
- `allowBackup=false` on Android

## License

Proprietary
