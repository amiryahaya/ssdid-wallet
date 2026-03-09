# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SSDID Wallet — a self-sovereign decentralized identity (SSI) wallet with post-quantum cryptography (PQC) support. Currently an Android reference implementation using Jetpack Compose, with planned iOS and HarmonyOS NEXT targets.

**Package:** `my.ssdid.wallet`

## Build & Test Commands

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

**Gradle wrapper version:** 8.11.1 | **JVM target:** Java 17 | **Robolectric SDK:** 34

## Architecture

### Layer Structure

```
feature/   → UI screens (Compose + ViewModels, one per feature)
domain/    → Business logic, models, crypto, vault, transport
platform/  → Android-specific implementations (keystore, biometric, storage)
di/        → Hilt modules (AppModule, StorageModule)
ui/        → Shared UI (navigation, theme)
```

### Key Architectural Decisions

- **Dual crypto provider pattern:** `CryptoProvider` interface implemented by `ClassicalProvider` (BouncyCastle: Ed25519, ECDSA P-256/384) and `PqcProvider` (KAZ-Sign via JNI: 128/192/256-bit). Injected via `@Named("classical")` and `@Named("pqc")` Hilt qualifiers.
- **Vault abstraction:** `Vault` → `VaultImpl` → `VaultStorage` (DataStore) + `KeystoreManager` (hardware TEE/StrongBox). PQC keys are wrapped with hardware-backed AES-256 keys.
- **SsdidClient orchestrator:** Central entry point with 4 flows — `initIdentity()`, `registerWithService()`, `authenticate()`, `signTransaction()`.
- **Transport:** Retrofit 2 + kotlinx-serialization + OkHttp. Registry endpoint: `https://registry.ssdid.my`.
- **DID method:** `did:ssdid:<Base64url(128-bit random)>`, W3C DID Core 1.1 compliant.

### Native Code (C/JNI)

KAZ-Sign PQC library lives in `android/app/src/main/cpp/`:
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

JUnit 4 + Mockk + Truth + Robolectric. Tests live in `android/app/src/test/java/my/ssdid/wallet/`. Key test areas: crypto providers, vault operations, DID/model serialization, key rotation, recovery, backup, URL validation, deep links.

## Conventions

- **Commit style:** Conventional commits (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`)
- **Proof types follow W3C format:** e.g., `Ed25519Signature2020`, `KazSign128Signature2024`
- **6 supported algorithms** defined in `domain/model/Algorithm.kt` enum
- **Deep link scheme:** `ssdid://`
- **Localization:** English, Malay, Chinese (via `LocalizationManager`)
- **Security:** `allowBackup=false`, biometric-gated vault, hardware keystore, URL validation on deep links
