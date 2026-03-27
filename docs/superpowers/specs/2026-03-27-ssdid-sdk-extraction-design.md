# SSDID SDK Extraction Design

**Date:** 2026-03-27
**Status:** Approved

## Overview

Extract the SSDID Wallet's domain and platform layers into a standalone SDK (`ssdid-core` + optional `ssdid-pqc`) that both internal and external consumers can use to integrate SSDID identity, credentials, and related capabilities into their own apps.

## Requirements

- **Platforms:** Android (Kotlin) and iOS (Swift), shipped simultaneously
- **Scope:** Full stack — identity, credentials (OID4VCI/OID4VP), SD-JWT, recovery, key rotation, offline verification, notifications, backup, device management
- **Distribution:** Private via GitHub Packages (Maven for Android, SPM for iOS)
- **Platform implementations:** Ship sensible defaults (AndroidKeystore, Keychain, DataStore, etc.) with overridable interfaces
- **PQC:** Separate optional module (`ssdid-pqc`) to avoid bundling native binaries for consumers who don't need post-quantum support
- **Documentation:** User manual, API reference (Dokka/DocC), and sample apps
- **Min platform versions:** Android `minSdk = 26` (Android 8.0), iOS 15+
- **Error model:** Sealed `SsdidError` hierarchy (no mixed Result/exception patterns)
- **Threading:** All SDK suspend functions are main-safe; SDK dispatches to IO internally
- **Logging:** Pluggable `SsdidLogger` interface (no Sentry/android.util.Log dependency)

## Approach

Monorepo extraction — SDK modules live under `sdk/` in the existing `ssdid-wallet` repo. The wallet app becomes a consumer of the SDK. This enables atomic cross-cutting changes and fast iteration, with the option to split into a separate repo later.

## Module Structure

### Android (Gradle)

```
sdk/android/
├── ssdid-core/
│   └── src/main/java/my/ssdid/sdk/
│       ├── SsdidSdk.kt            # Entry point (builder pattern)
│       ├── domain/
│       │   ├── vault/             # Vault, VaultImpl, VaultStorage, KeystoreManager
│       │   ├── crypto/            # CryptoProvider, ClassicalProvider, Multibase, Multicodec, Base58
│       │   ├── did/               # DidResolver, MultiMethodResolver, DidKeyResolver, DidJwkResolver, SsdidRegistryResolver
│       │   ├── verifier/          # Verifier, VerifierImpl, OfflineVerifier, VerificationOrchestrator
│       │   ├── transport/         # SsdidHttpClient, APIs (Registry, Server, Issuer, Drive, Email, Notify), DTOs, RetryInterceptor, NetworkResult
│       │   ├── oid4vci/           # OpenId4VciHandler, IssuerMetadataResolver, TokenClient, ProofJwtBuilder, NonceManager, CredentialOffer
│       │   ├── oid4vp/            # OpenId4VpHandler, PresentationDefinitionMatcher, DcqlMatcher, VpTokenBuilder, AuthorizationRequest
│       │   ├── sdjwt/             # SdJwtParser, SdJwtIssuer, SdJwtVerifier, StoredSdJwtVc, Disclosure, KeyBindingJwt
│       │   ├── recovery/          # RecoveryManager, SocialRecoveryManager (Shamir), InstitutionalRecoveryManager
│       │   ├── rotation/          # KeyRotationManager
│       │   ├── revocation/        # RevocationManager, StatusListCredential, BitstringParser, HttpStatusListFetcher
│       │   ├── backup/            # BackupManager, BackupFormat
│       │   ├── device/            # DeviceManager, DeviceInfoProvider, DeviceInfo
│       │   ├── notify/            # NotifyManager, NotifyStorage, NotifyDispatcher, LocalNotificationStorage
│       │   ├── history/           # ActivityRepository, ActivityRecord
│       │   ├── settings/          # SettingsRepository, TtlProvider
│       │   └── model/             # Identity, VerifiableCredential, DidDocument, Did, Algorithm, Proof, CredentialStatus, VerifiablePresentation
│       └── platform/
│           ├── AndroidKeystoreManager.kt
│           ├── DataStoreVaultStorage.kt
│           ├── DataStoreBundleStore.kt
│           ├── DataStoreCredentialRepository.kt
│           ├── DataStoreSettingsRepository.kt
│           ├── DataStoreSocialRecoveryStorage.kt
│           ├── DataStoreInstitutionalRecoveryStorage.kt
│           ├── AndroidDeviceInfoProvider.kt
│           ├── AndroidConnectivityMonitor.kt
│           ├── AndroidNotifyDispatcher.kt
│           └── WorkManagerBundleSyncScheduler.kt
│
├── ssdid-pqc/
│   └── src/
│       ├── main/java/my/ssdid/sdk/pqc/
│       │   └── PqcProvider.kt
│       └── main/cpp/              # KAZ-Sign native + prebuilt OpenSSL
│
└── build-logic/                   # Shared Gradle conventions (publishing, versioning, Kotlin config)
```

### iOS (Swift Package Manager)

```
sdk/ios/
├── SsdidCore/
│   ├── Package.swift
│   └── Sources/SsdidCore/
│       ├── SsdidSdk.swift
│       ├── Domain/                # Mirror of Android domain layer
│       └── Platform/              # Keychain, file storage, BGTask, NWPathMonitor defaults
│
└── SsdidPqc/
    ├── Package.swift
    └── Sources/SsdidPqc/
        └── PqcProvider.swift
```

## Public API Surface

### Entry Point

```kotlin
// Android — basic
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .build()

// Android — with overrides and PQC
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .notifyUrl("https://notify.ssdid.my")
    .emailVerifyUrl("https://email.ssdid.my")
    .keystoreManager(myCustomKeystoreManager)
    .vaultStorage(myCustomStorage)
    .addCryptoProvider(PqcProvider())
    .certificatePinning(enabled = true, pins = listOf(...))
    .logger(myLogger)
    .build()
```

```swift
// iOS — basic
let sdk = SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .build()

// iOS — with PQC
let sdk = SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .addCryptoProvider(PqcProvider())
    .build()
```

### Capability Sub-objects

| Sub-object | Key Methods |
|---|---|
| `sdk.identity` | `create(name, algorithm)`, `list()`, `get(did)`, `delete(did)`, `buildDidDocument(keyId)`, `updateDidDocument(keyId)` |
| `sdk.vault` | `sign(keyId, data)`, `createProof(keyId, document, proofPurpose, challenge, domain)` |
| `sdk.credentials` | `store(credential)`, `list()`, `getForDid(did)`, `delete(id)` |
| `sdk.flows` | `registerWithService(identity, serviceUrl)`, `authenticate(identity, serviceUrl, challenge)`, `signTransaction(identity, serviceUrl, txBody)` |
| `sdk.issuance` | `handleOffer(offerUri): IssuanceResult` |
| `sdk.presentation` | `handleRequest(requestUri): PresentationReviewResult`, `submit(reviewResult, selectedCredentials)` |
| `sdk.sdJwt` | `parse(compactSdJwt)`, `store(sdJwtVc)`, `list()` |
| `sdk.verifier` | `verifyCredential(credential)`, `verifySignature(did, keyId, signature, data)`, `resolveDid(did)` |
| `sdk.offline` | `syncBundles()`, `verifyOffline(credential)`, `scheduleBundleSync(intervalHours)` |
| `sdk.recovery` | `generateRecoveryKey(identity)`, `restoreWithRecoveryKey(...)` |
| `sdk.recovery.social` | `setupGuardians(identity, guardians, threshold)`, `restore(did, shares)` |
| `sdk.recovery.institutional` | `backup(identity)`, `restore(did, token)` |
| `sdk.rotation` | `prepare(identity)`, `complete(identity, accessCode)`, `rollback(identity)` |
| `sdk.backup` | `export(identities, credentials, password)`, `import(backupData, password)` |
| `sdk.device` | `register(identity)`, `deregister(identity)` |
| `sdk.notifications` | `createInbox(identity)`, `fetch()`, `dismiss(notificationId)` |
| `sdk.revocation` | `checkStatus(credential): RevocationStatus` |
| `sdk.history` | `log(record)`, `list(did)` |

### Overridable Interfaces

| Interface | Android Default | iOS Default | Purpose |
|---|---|---|---|
| `KeystoreManager` | `AndroidKeystoreManager` | `KeychainManager` | Key wrapping/encryption |
| `VaultStorage` | `DataStoreVaultStorage` | `FileVaultStorage` | Identity & credential persistence |
| `CryptoProvider` | `ClassicalProvider` | `ClassicalProvider` | Signing & verification |
| `ConnectivityMonitor` | `AndroidConnectivityMonitor` | `NWPathMonitor` | Network state for sync |
| `BundleSyncScheduler` | `WorkManagerScheduler` | `BGTaskScheduler` | Background bundle sync |
| `DeviceInfoProvider` | `AndroidDeviceInfoProvider` | `IOSDeviceInfoProvider` | Device fingerprint |
| `NotifyDispatcher` | `AndroidNotifyDispatcher` | `IOSNotifyDispatcher` | Push notification delivery |
| `ActivityRepository` | In-memory default | In-memory default | Audit logging |
| `SettingsRepository` | `DataStoreSettingsRepository` | `UserDefaultsSettings` | Persistent settings |
| `SsdidLogger` | No-op default | No-op default | Pluggable logging/telemetry |

## Documentation & Sample Code

### Documentation

```
sdk/docs/
├── getting-started.md
├── configuration.md
├── identity-management.md
├── credentials.md
├── presentations.md
├── sd-jwt.md
├── verification.md
├── recovery.md
├── key-rotation.md
├── backup.md
├── notifications.md
├── device-management.md
├── custom-implementations.md
├── migration-guide.md
└── api-reference/
    ├── android/                # Dokka-generated
    └── ios/                    # DocC-generated
```

### Sample Apps

| Sample | What It Demonstrates |
|---|---|
| `samples/android/basic-identity/` | SDK init, DID creation, signing, verifying, DID document |
| `samples/android/credential-flow/` | QR scan, OID4VCI offer, credential storage, OID4VP presentation, selective disclosure |
| `samples/android/custom-storage/` | Override VaultStorage with Room DB, custom KeystoreManager |
| `samples/ios/BasicIdentity/` | SDK init, DID creation, signing, verifying, DID document |
| `samples/ios/CredentialFlow/` | QR scan, OID4VCI offer, credential storage, OID4VP presentation, selective disclosure |
| `samples/ios/CustomStorage/` | Override VaultStorage with Core Data |

Each documentation page includes inline Kotlin and Swift code snippets.

### Doc Generation

- **Android:** Dokka plugin generates API reference from KDoc comments
- **iOS:** DocC catalogs with code examples built into Xcode documentation

## Wallet App Migration

### Post-extraction Structure

```
android/app/src/main/java/my/ssdid/wallet/
├── di/            # Simplified — initializes SsdidSdk, injects into ViewModels
├── feature/       # UI screens (use sdk.* instead of direct domain classes)
└── ui/            # Shared UI (unchanged)
```

### Dependency Graph

```
wallet-app
  ├── ssdid-core        (implementation)
  ├── ssdid-pqc         (implementation, optional)
  ├── Compose / Hilt    (UI framework)
  └── app-specific code (features, navigation, theme)

ssdid-core
  ├── kotlinx-serialization
  ├── retrofit2 + okhttp
  ├── androidx-datastore
  ├── androidx-biometric
  ├── bouncycastle
  └── workmanager

ssdid-pqc
  ├── ssdid-core        (api dependency)
  └── native libs       (KAZ-Sign C/JNI + OpenSSL)
```

### Incremental Migration Phases

0. **Phase 0: Decouple domain from Android/Hilt/Sentry.** Before any code moves:
   - Remove Sentry imports from `SsdidClient.kt` and `VaultImpl.kt`; replace with `SsdidLogger` interface calls
   - Strip `javax.inject.*` / `dagger.*` annotations from all domain files (~10 files)
   - Move domain files with `android.*` imports (`AndroidNotifyDispatcher`, `NotifyStorage`, `NotifyLifecycleObserver`, `LocalNotificationStorage`, `UnifiedPushReceiver`) to `platform/`
   - Remove `VaultStorage.isOnboardingCompleted()` / `setOnboardingCompleted()` (wallet-specific, not SDK concern)
   - Move `ActivityModule.kt` (Hilt module) out of `domain/` to wallet's `di/`
   - Clarify `CredentialIssuanceManager` vs `OpenId4VciHandler` — consolidate or mark one as deprecated
   - Keep `UnifiedPushReceiver` and `NotifyLifecycleObserver` in wallet (they reference `SsdidApp`); expose callback hooks in SDK
1. **Phase 1:** Create `sdk/android/ssdid-core`, move `domain/` package. Wallet adds `implementation(project(":sdk:android:ssdid-core"))`. Update imports from `my.ssdid.wallet.domain.*` to `my.ssdid.sdk.*`.
2. **Phase 2:** Move `platform/` default implementations into `ssdid-core`. Wallet DI shrinks to `SsdidSdk.builder(context).build()`. Ship `consumer-rules.pro` for kotlinx-serialization + Retrofit keep rules.
3. **Phase 3:** Extract PQC into `ssdid-pqc` module.
4. **Phase 4:** iOS extraction:
   - Create `SsdidCore` SPM package, move Swift domain + platform code
   - Audit Domain/ for UIKit imports and remove (same treatment as Android Phase 0)
   - Verify `ServiceContainer` coupling is abstracted via SDK builder
   - Set minimum deployment target: iOS 15+
   - Ensure Swift concurrency contracts match Android (all public methods are async, main-safe)
5. **Phase 5:** Publish to GitHub Packages. Wallet switches from `project()` to published artifact dependency. Add GPG signing for Maven artifacts.

### What Stays in the Wallet App

- All `feature/` screens and ViewModels
- Navigation and theme
- App-level lifecycle (biometric lock, onboarding)
- Deep link routing (delegates to `sdk.issuance` / `sdk.presentation`)
- Push notification receiver (forwards to `sdk.notifications`)

## Error Model

The SDK standardizes on `Result<T>` for all public methods — no thrown exceptions leak to consumers. Errors use a sealed hierarchy:

```kotlin
sealed class SsdidError : Exception() {
    // Network
    data class NetworkError(val cause: Throwable) : SsdidError()
    data class Timeout(val url: String) : SsdidError()
    data class ServerError(val statusCode: Int, val body: String?) : SsdidError()

    // Crypto
    data class UnsupportedAlgorithm(val algorithm: String) : SsdidError()
    data class SigningFailed(val reason: String) : SsdidError()
    data class VerificationFailed(val reason: String) : SsdidError()

    // Storage
    data class StorageError(val cause: Throwable) : SsdidError()
    data class IdentityNotFound(val did: String) : SsdidError()
    data class CredentialNotFound(val id: String) : SsdidError()

    // DID
    data class DidResolutionFailed(val did: String, val reason: String) : SsdidError()

    // Issuance / Presentation
    data class IssuanceFailed(val reason: String) : SsdidError()
    data class PresentationFailed(val reason: String) : SsdidError()
    data class NoMatchingCredentials(val requestId: String) : SsdidError()

    // Recovery / Rotation
    data class RecoveryFailed(val reason: String) : SsdidError()
    data class RotationFailed(val reason: String) : SsdidError()
}
```

iOS mirrors this as a Swift enum with associated values.

## Threading Contract

- All public SDK methods are `suspend` (Kotlin) / `async` (Swift)
- SDK is **main-safe**: all IO, network, and crypto work dispatches internally to background threads
- Consumers can call SDK methods from any dispatcher/thread
- Callbacks (e.g., `NotifyDispatcher`, `SsdidLogger`) are invoked on the caller's dispatcher — SDK does not force a thread

## ProGuard / R8

The SDK ships `consumer-rules.pro` that keeps:
- All `@Serializable` model classes (kotlinx-serialization)
- Retrofit API interfaces
- SDK public API surface

Consumers enabling R8/ProGuard get these rules applied automatically.

## Versioning & Publishing

### Versioning

- Semantic versioning: `MAJOR.MINOR.PATCH`
- `ssdid-core` and `ssdid-pqc` versioned together
- Git tags: `sdk-v1.0.0`

### Publishing

**Android (Maven / GitHub Packages):**
```kotlin
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "my.ssdid.sdk"
            artifactId = "ssdid-core" // or "ssdid-pqc"
            version = sdkVersion
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/amiryahaya/ssdid-sdk")
        }
    }
}
```

**iOS (SPM):** Consumers add via authenticated GitHub URL with version tag.

### Consumer Setup (Android)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/amiryahaya/ssdid-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_USER")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("my.ssdid.sdk:ssdid-core:1.0.0")
    implementation("my.ssdid.sdk:ssdid-pqc:1.0.0") // optional
}
```

### CI Pipeline

```
On PR (sdk/** changed):
  - Build ssdid-core + ssdid-pqc (Android)
  - Build SsdidCore + SsdidPqc (iOS)
  - Run SDK unit tests
  - Build sample apps (smoke test)
  - Lint + API compatibility check

On tag (sdk-v*):
  - All above + publish to GitHub Packages
  - Tag iOS SPM release
  - Generate Dokka + DocC API docs
```

### API Compatibility

- Kotlin Binary Compatibility Validator detects breaking changes
- CI fails if public API changes without explicit `.api` file update

## Testing Strategy

- **SDK unit tests:** Existing domain tests (`android/app/src/test/java/my/ssdid/wallet/domain/`) move into `sdk/android/ssdid-core/src/test/`. Tests for crypto, vault, DID, OID4VCI/VP, recovery, rotation, revocation, and models all belong in the SDK.
- **Wallet integration tests:** Tests exercising feature/UI logic stay in the wallet app.
- **SDK test fixtures:** Ship test doubles (`FakeVaultStorage`, `FakeCryptoProvider`, `InMemoryBundleStore`) as a `ssdid-core-testing` artifact so consumers can write their own tests without mocking SDK internals.
- **Sample app smoke tests:** CI builds all sample apps to catch API breakage.
