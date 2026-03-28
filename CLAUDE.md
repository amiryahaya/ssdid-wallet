# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SSDID Wallet — a self-sovereign decentralized identity (SSI) wallet with post-quantum cryptography (PQC) support. Dual-platform implementation: Android (Kotlin/Compose) and iOS (Swift/SwiftUI). The project includes a reusable SDK (`ssdid-core`, `ssdid-pqc`) that encapsulates domain logic, alongside the wallet app which consumes it.

**App Name:** SSDID Wallet
**Package:** `my.ssdid.wallet` (wallet app), `my.ssdid.sdk` (SDK)
**Registry:** `https://registry.ssdid.my`

## Build & Test Commands

### Wallet App

All commands run from the `android/` directory using the Gradle wrapper.

```bash
# Build
./gradlew compileDebugKotlin                    # Compile Kotlin sources
./gradlew :app:compileDebugUnitTestKotlin       # Compile tests only

# Test
./gradlew :app:testDebugUnitTest                # Run all unit tests
./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.crypto.ClassicalProviderTest"  # Single test class
./gradlew :app:testDebugUnitTest --tests "*.VaultTest.testSignData"  # Single test method

# Code quality
./gradlew lint                                  # Android lint
./gradlew koverHtmlReportDebug                  # Coverage report (HTML)
./gradlew koverXmlReportDebug                   # Coverage report (XML)
```

### SDK (Android)

```bash
# SDK Build
./gradlew :sdk:ssdid-core:compileDebugKotlin       # Build SDK core
./gradlew :sdk:ssdid-pqc:compileDebugKotlin        # Build SDK PQC module
./gradlew :sdk:ssdid-core-testing:compileDebugKotlin # Build SDK test doubles

# SDK Test
./gradlew :sdk:ssdid-core:testDebugUnitTest         # Run SDK core tests
./gradlew :sdk:ssdid-pqc:testDebugUnitTest          # Run SDK PQC tests

# SDK API compatibility
./gradlew :sdk:ssdid-core:apiCheck                  # Check API hasn't changed
./gradlew :sdk:ssdid-core:apiDump                   # Update API baseline
```

### SDK (iOS)

```bash
cd sdk/ios/SsdidCore && swift build                  # Build iOS SDK
cd sdk/ios/SsdidCore && swift test                   # Test iOS SDK
```

**Gradle wrapper version:** 8.11.1 | **JVM target:** Java 17 | **Robolectric SDK:** 34

## Architecture

### Layer Structure

```
sdk/android/
  ssdid-core/         → SDK: domain logic + platform defaults (my.ssdid.sdk)
  ssdid-core-testing/ → SDK: test doubles for consumers
  ssdid-pqc/          → SDK: optional PQC module (my.ssdid.sdk.pqc)
  samples/            → Sample apps demonstrating SDK usage

sdk/ios/
  SsdidCore/          → iOS SDK: SPM package (domain + platform)
  SsdidPqc/           → iOS SDK: optional PQC SPM package

android/app/          → Wallet app (consumes SDK)
  feature/            → UI screens (Compose + ViewModels)
  platform/           → Wallet-specific platform code (biometric, deeplink, scan)
  di/                 → Hilt modules (AppModule, StorageModule)
  ui/                 → Shared UI (navigation, theme)

ios/SsdidWallet/      → iOS wallet app (consumes SsdidCore)
```

### Key Architectural Decisions

- **SDK entry point:** `SsdidSdk.builder(context)` is the main entry point for SDK consumers. Provides 16 capability sub-objects: identity, vault, credentials, flows, issuance, presentation, sdJwt, verifier, offline, recovery, rotation, backup, device, notifications, revocation, history.
- **Domain in SDK:** Domain code now lives in the SDK at `my.ssdid.sdk.domain.*`. The domain layer has zero Android/Hilt/Sentry dependencies.
- **PQC as optional add-on:** PQC support is an optional module (`ssdid-pqc`) that can be included separately.
- **Dual crypto provider pattern:** `CryptoProvider` interface implemented by `ClassicalProvider` (BouncyCastle: Ed25519, ECDSA P-256/384) and `PqcProvider` (KAZ-Sign via JNI: 128/192/256-bit). Injected via `@Named("classical")` and `@Named("pqc")` Hilt qualifiers in the wallet app.
- **Vault abstraction:** `Vault` → `VaultImpl` → `VaultStorage` (DataStore) + `KeystoreManager` (hardware TEE/StrongBox). PQC keys are wrapped with hardware-backed AES-256 keys.
- **SsdidClient orchestrator:** Central entry point with 4 flows — `initIdentity()`, `registerWithService()`, `authenticate()`, `signTransaction()`.
- **Logging:** `SsdidLogger` interface replaces Sentry in the SDK, allowing consumers to plug in their own logging backend.
- **Internal API protection:** `@InternalSsdidApi` annotation protects migration-only properties from external use.
- **Transport:** Retrofit 2 + kotlinx-serialization + OkHttp. Registry endpoint: `https://registry.ssdid.my`.
- **DID method:** `did:ssdid:<Base64url(128-bit random)>`, W3C DID Core 1.1 compliant.

### Native Code (C/JNI)

KAZ-Sign PQC library lives in `android/sdk/ssdid-pqc/src/main/cpp/`:
- `kazsign_jni.c` — JNI bridge
- `kazsign/` — C source (sign, hash, DER, KDF, security)
- `libs/openssl/` — Prebuilt OpenSSL per ABI
- Built via CMake 3.22.1, targets: arm64-v8a, x86_64

### Transaction Signing Protocol

1. Get challenge from server
2. SHA3-256 hash of transaction body
3. Sign `challenge || Base64(txHash)`
4. Multibase-encode signature
5. Submit to server

## Test Stack

JUnit 4 + Mockk + Truth + Robolectric. Wallet app tests live in `android/app/src/test/java/my/ssdid/wallet/`. SDK tests live in `android/sdk/ssdid-core/src/test/`. The `ssdid-core-testing` module provides test doubles for SDK consumers. Key test areas: crypto providers, vault operations, DID/model serialization, key rotation, recovery, backup, URL validation, deep links.

## SDK Documentation

SDK documentation and guides live in `sdk/docs/`.

## Conventions

- **Commit style:** Conventional commits (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`)
- **Proof types follow W3C format:** e.g., `Ed25519Signature2020`, `KazSign128Signature2024`
- **6 supported algorithms** defined in `domain/model/Algorithm.kt` enum
- **Deep link scheme:** `ssdid://`
- **Localization:** English, Malay, Chinese (via `LocalizationManager`)
- **Security:** `allowBackup=false`, biometric-gated vault, hardware keystore, URL validation on deep links
