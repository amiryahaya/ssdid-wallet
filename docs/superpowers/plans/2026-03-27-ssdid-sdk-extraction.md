# SSDID SDK Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the SSDID Wallet's domain and platform layers into `ssdid-core` and `ssdid-pqc` SDK modules (Android first, then iOS), with a public builder API, documentation, and sample apps.

**Architecture:** Monorepo extraction into `sdk/` directory. The wallet app becomes a consumer of the SDK. Domain code is decoupled from Android/Hilt/Sentry before extraction. SDK uses a builder pattern entry point (`SsdidSdk`) with capability sub-objects. PQC is an optional add-on module.

**Tech Stack:** Kotlin 2.2.10, Android Gradle Plugin 9.1.0, SPM (iOS), JVM 17, BouncyCastle, Retrofit 2, OkHttp, kotlinx-serialization, DataStore, WorkManager, Biometric

**Spec:** `docs/superpowers/specs/2026-03-27-ssdid-sdk-extraction-design.md`

---

## Phase 0: Decouple Domain from Android/Hilt/Sentry

### Task 1: Create SsdidLogger interface and replace Sentry in domain

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/logging/SsdidLogger.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/logging/SentryLogger.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/logging/SsdidLoggerTest.kt`

- [ ] **Step 1: Write failing test for SsdidLogger**

```kotlin
// SsdidLoggerTest.kt
package my.ssdid.wallet.domain.logging

import org.junit.Test
import com.google.common.truth.Truth.assertThat

class SsdidLoggerTest {
    @Test
    fun `NoOpLogger does not throw`() {
        val logger = NoOpLogger()
        logger.info("vault", "test message", mapOf("key" to "value"))
        logger.warning("vault", "warn message")
        logger.error("vault", "error message", RuntimeException("test"))
        // no-op — just verifying no exceptions
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.SsdidLoggerTest" 2>&1 | tail -5`
Expected: FAIL — class not found

- [ ] **Step 3: Create SsdidLogger interface and NoOpLogger**

```kotlin
// domain/logging/SsdidLogger.kt
package my.ssdid.wallet.domain.logging

interface SsdidLogger {
    fun info(category: String, message: String, data: Map<String, String> = emptyMap())
    fun warning(category: String, message: String, data: Map<String, String> = emptyMap())
    fun error(category: String, message: String, throwable: Throwable? = null, data: Map<String, String> = emptyMap())
}

class NoOpLogger : SsdidLogger {
    override fun info(category: String, message: String, data: Map<String, String>) {}
    override fun warning(category: String, message: String, data: Map<String, String>) {}
    override fun error(category: String, message: String, throwable: Throwable?, data: Map<String, String>) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.SsdidLoggerTest" 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 5: Create SentryLogger platform implementation**

```kotlin
// platform/logging/SentryLogger.kt
package my.ssdid.wallet.platform.logging

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import my.ssdid.wallet.domain.logging.SsdidLogger

class SentryLogger : SsdidLogger {
    override fun info(category: String, message: String, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.INFO
            data.forEach { (k, v) -> this.data[k] = v }
        })
    }
    override fun warning(category: String, message: String, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.WARNING
            data.forEach { (k, v) -> this.data[k] = v }
        })
    }
    override fun error(category: String, message: String, throwable: Throwable?, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.ERROR
            data.forEach { (k, v) -> this.data[k] = v }
        })
        throwable?.let { Sentry.captureException(it) }
    }
}
```

- [ ] **Step 6: Replace Sentry calls in VaultImpl.kt with SsdidLogger**

In `VaultImpl.kt`:
- Add constructor parameter: `private val logger: SsdidLogger = NoOpLogger()`
- Replace all `Sentry.addBreadcrumb(Breadcrumb().apply { category = "vault"; message = ...; level = SentryLevel.INFO; ... })` with `logger.info("vault", ..., mapOf(...))`
- Remove imports: `io.sentry.Breadcrumb`, `io.sentry.Sentry`, `io.sentry.SentryLevel`
- Add import: `my.ssdid.wallet.domain.logging.SsdidLogger`, `my.ssdid.wallet.domain.logging.NoOpLogger`

- [ ] **Step 7: Replace Sentry calls in SsdidClient.kt with SsdidLogger**

In `SsdidClient.kt`:
- Add constructor parameter: `private val logger: SsdidLogger = NoOpLogger()`
- Replace all Sentry breadcrumb calls with `logger.info(...)` / `logger.error(...)`
- Remove Sentry imports
- Add SsdidLogger imports

- [ ] **Step 8: Wire SentryLogger in AppModule.kt**

Add `SentryLogger()` as the logger parameter when constructing `VaultImpl` and `SsdidClient` in `AppModule.kt`.

- [ ] **Step 9: Run all tests to verify nothing broke**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All tests pass (SsdidClient and Vault tests use default NoOpLogger)

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "refactor: replace Sentry with SsdidLogger interface in domain layer"
```

---

### Task 2: Strip Hilt/Dagger annotations from domain files

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/rotation/KeyRotationManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/backup/BackupManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/recovery/RecoveryManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/notify/NotifyManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/notify/NotifyStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotificationStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileMigration.kt`
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/history/ActivityModule.kt` → `android/app/src/main/java/my/ssdid/wallet/di/ActivityModule.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

- [ ] **Step 1: Remove @Inject/@Singleton from domain classes**

For each of these files, remove `@Singleton` class annotation and `@Inject constructor` — convert to regular `class Foo(...)` constructor:

1. `KeyRotationManager.kt` — remove `@Singleton`, `@Inject constructor` → `class KeyRotationManager(`
2. `BackupManager.kt` — same treatment
3. `RecoveryManager.kt` — same treatment
4. `NotifyManager.kt` — remove `@Singleton`, `@Inject constructor`; also remove `import android.util.Log` and replace `Log.d/e/w` with `logger.info/error/warning` (add SsdidLogger param)
5. `NotifyStorage.kt` — remove `@Singleton`, `@Inject constructor`
6. `LocalNotificationStorage.kt` — remove `@Singleton`, `@Inject constructor`
7. `ProfileMigration.kt` — remove `@Singleton`, `@Inject constructor`

Remove `import javax.inject.*` and `import dagger.*` from each file.

- [ ] **Step 2: Move ActivityModule.kt to di/ package**

Move `domain/history/ActivityModule.kt` to `di/ActivityModule.kt`. Update package declaration to `package my.ssdid.wallet.di`. The file already imports from `platform.storage.ActivityRepositoryImpl` so it belongs in the DI layer.

- [ ] **Step 3: Update AppModule.kt to construct classes without @Inject**

Since these classes no longer have `@Inject constructor`, AppModule.kt must provide them explicitly via `@Provides` functions. Add `@Provides @Singleton` methods for `NotifyManager`, `NotifyStorage`, `LocalNotificationStorage`, `KeyRotationManager`, `BackupManager`, `RecoveryManager`, `ProfileMigration`.

- [ ] **Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: strip Hilt annotations from domain layer"
```

---

### Task 3: Move Android-coupled notify files to platform; extract NotifyStorage interface

**Files:**
- Move: `domain/notify/AndroidNotifyDispatcher.kt` → `platform/notify/AndroidNotifyDispatcher.kt`
- Move: `domain/notify/UnifiedPushReceiver.kt` → `platform/notify/UnifiedPushReceiver.kt`
- Move: `domain/notify/NotifyLifecycleObserver.kt` → `platform/notify/NotifyLifecycleObserver.kt`
- Move: `domain/notify/LocalNotificationStorage.kt` → `platform/notify/LocalNotificationStorage.kt`
- Refactor: `domain/notify/NotifyStorage.kt` — extract a platform-agnostic interface to keep in domain, move the Android implementation to platform
- Keep: `domain/notify/NotifyManager.kt` (after removing android.util.Log in Task 2)
- Keep: `domain/notify/NotifyDispatcher.kt` (interface, no android imports)
- Keep: `domain/notify/LocalNotification.kt` (data class, no android imports)
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

Note: `NotifyStorage` currently uses `android.content.Context` and `dagger.hilt.android.qualifiers.ApplicationContext`. We need to split it into an interface (domain, SDK-safe) and an implementation (platform, Android-specific).

- [ ] **Step 1: Extract NotifyStorage interface**

Create a platform-agnostic `NotifyStorage` interface in `domain/notify/NotifyStorage.kt` (keep existing methods as interface methods). Rename the current concrete class to `DataStoreNotifyStorage` and move it to `platform/notify/DataStoreNotifyStorage.kt` implementing the new interface.

- [ ] **Step 2: Move the 4 Android-coupled files**

Move `AndroidNotifyDispatcher.kt`, `UnifiedPushReceiver.kt`, `NotifyLifecycleObserver.kt`, `LocalNotificationStorage.kt` to `platform/notify/`. Update package declarations.

- [ ] **Step 3: Update all imports across the codebase**

Update imports for all moved classes. Update `AppModule.kt` to provide `DataStoreNotifyStorage` as the `NotifyStorage` interface binding.

- [ ] **Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: move Android-coupled notify classes to platform, extract NotifyStorage interface"
```

---

### Task 4: Remove onboarding state from VaultStorage and clean up android imports in domain

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/storage/OnboardingStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/CredentialOffer.kt` (remove `android.net.Uri` usage)
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequest.kt` (remove `android.net.Uri` usage)
- Modify: Affected feature files that use onboarding state

- [ ] **Step 1: Remove onboarding methods from VaultStorage interface**

Remove lines 42-43 from `VaultStorage.kt`:
```kotlin
// Remove these:
suspend fun isOnboardingCompleted(): Boolean
suspend fun setOnboardingCompleted()
```

- [ ] **Step 2: Create OnboardingStorage in platform**

```kotlin
// platform/storage/OnboardingStorage.kt
package my.ssdid.wallet.platform.storage

interface OnboardingStorage {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
```

Move the implementation from `DataStoreVaultStorage` into a new `DataStoreOnboardingStorage` class (or keep it in `DataStoreVaultStorage` implementing both interfaces). Update DI accordingly.

- [ ] **Step 3: Replace android.net.Uri in CredentialOffer.kt and AuthorizationRequest.kt**

Replace `android.net.Uri.parse(...)` with `java.net.URI(...)` or a simple string parsing utility. These are URI-parsing calls that don't need Android.

- [ ] **Step 4: Consolidate CredentialIssuanceManager with OpenId4VciHandler**

`CredentialIssuanceManager.kt` overlaps with `OpenId4VciHandler.kt`. Deprecate `CredentialIssuanceManager` by marking it `@Deprecated` with a migration note pointing to `OpenId4VciHandler`. If `CredentialIssuanceManager` has unique non-OID4VCI flows, extract those into a clearly named class (e.g., `CustomIssuanceFlow`). The SDK should not ship two overlapping issuance APIs.

- [ ] **Step 5: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All tests pass (update any tests that reference onboarding through VaultStorage)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: remove onboarding state from VaultStorage, replace android.net.Uri in domain"
```

---

## Phase 1: Create Android SDK Module and Move Domain

### Task 5: Create ssdid-core Gradle module scaffold

**Files:**
- Create: `android/sdk/ssdid-core/build.gradle.kts`
- Create: `android/sdk/ssdid-core/src/main/AndroidManifest.xml`
- Create: `android/sdk/ssdid-core/consumer-rules.pro`
- Create: `android/sdk/ssdid-core/proguard-rules.pro`
- Modify: `android/settings.gradle.kts`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Create the module directory**

```bash
mkdir -p android/sdk/ssdid-core/src/main/java/my/ssdid/sdk
mkdir -p android/sdk/ssdid-core/src/test/java/my/ssdid/sdk
```

- [ ] **Step 2: Write build.gradle.kts for ssdid-core**

```kotlin
// android/sdk/ssdid-core/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "my.ssdid.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Network
    api("com.squareup.retrofit2:retrofit:2.11.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Crypto
    api("org.bouncycastle:bcprov-jdk18on:1.80")

    // DataStore (default storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (default sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Biometric (default authenticator)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
```

- [ ] **Step 3: Create AndroidManifest.xml**

```xml
<!-- android/sdk/ssdid-core/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
</manifest>
```

- [ ] **Step 4: Create consumer-rules.pro**

```proguard
# android/sdk/ssdid-core/consumer-rules.pro

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class my.ssdid.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class my.ssdid.sdk.**$$serializer { *; }
-keepclassmembers class my.ssdid.sdk.** {
    *** Companion;
}

# Retrofit API interfaces
-keep,allowobfuscation interface my.ssdid.sdk.domain.transport.*Api

# OkHttp
-dontwarn okhttp3.internal.platform.**
```

- [ ] **Step 5: Register module in settings.gradle.kts**

Add to `android/settings.gradle.kts`:
```kotlin
include(":sdk:ssdid-core")
```

- [ ] **Step 6: Add SDK dependency to wallet app**

Add to `android/app/build.gradle.kts` dependencies:
```kotlin
implementation(project(":sdk:ssdid-core"))
```

- [ ] **Step 7: Verify the build compiles**

Run: `cd android && ./gradlew :sdk:ssdid-core:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (empty module compiles)

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(sdk): create ssdid-core Android library module scaffold"
```

---

### Task 6: Move domain model, crypto, and DID packages to SDK

**Files:**
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/model/` → `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/model/`
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/CryptoProvider.kt` → `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/crypto/CryptoProvider.kt`
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/ClassicalProvider.kt` → SDK
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/KeyPairResult.kt` → SDK
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/Multibase.kt` → SDK
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/BouncyCastleInstaller.kt` → SDK
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/did/` → SDK (all 7 files)
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/logging/` → SDK
- Move corresponding tests to SDK test directory
- Update all imports across the wallet app

This is a large move. The approach:

- [ ] **Step 1: Move model package (9 files)**

Move all files from `domain/model/` to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/model/`. Update `package` declarations from `my.ssdid.wallet.domain.model` to `my.ssdid.sdk.domain.model`.

- [ ] **Step 2: Move crypto package (5 files, excluding PQC and kazsign)**

Move `CryptoProvider.kt`, `ClassicalProvider.kt`, `KeyPairResult.kt`, `Multibase.kt`, `BouncyCastleInstaller.kt` to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/crypto/`. Update package declarations.

Leave `PqcProvider.kt` and `kazsign/` in place for now (Task 9 — PQC extraction).

- [ ] **Step 3: Move DID package (7 files)**

Move `DidResolver.kt`, `MultiMethodResolver.kt`, `DidKeyResolver.kt`, `DidJwkResolver.kt`, `SsdidRegistryResolver.kt`, `Base58.kt`, `Multicodec.kt` to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/did/`. Update package declarations.

- [ ] **Step 4: Move logging package**

Move `SsdidLogger.kt`, `NoOpLogger` to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/logging/`. Update package.

- [ ] **Step 5: Move corresponding tests**

Move test files for model, crypto, DID, and logging from `app/src/test/.../domain/` to `sdk/ssdid-core/src/test/java/my/ssdid/sdk/domain/`. Update package declarations and imports.

Key test files to move:
- `domain/model/` tests (10 files: AlgorithmTest, AlgorithmJwaTest, DidTest, DidDocumentTest, DidValidationTest, IdentityProfileTest, ModelPrerequisitesTest, VerifiableCredentialTest, VerifiablePresentationTest, VerificationMethodJwkTest)
- `domain/crypto/ClassicalProviderTest.kt`, `MultibaseTest.kt`
- `domain/did/` tests (4 files: DidJwkResolverTest, DidKeyResolverTest, MultiMethodResolverTest, MulticodecTest)
- `domain/logging/SsdidLoggerTest.kt`

- [ ] **Step 6: Update imports across the wallet app**

Global find-and-replace:
- `my.ssdid.wallet.domain.model.` → `my.ssdid.sdk.domain.model.`
- `my.ssdid.wallet.domain.crypto.CryptoProvider` → `my.ssdid.sdk.domain.crypto.CryptoProvider` (and other moved crypto files)
- `my.ssdid.wallet.domain.crypto.ClassicalProvider` → `my.ssdid.sdk.domain.crypto.ClassicalProvider`
- `my.ssdid.wallet.domain.crypto.Multibase` → `my.ssdid.sdk.domain.crypto.Multibase`
- `my.ssdid.wallet.domain.crypto.KeyPairResult` → `my.ssdid.sdk.domain.crypto.KeyPairResult`
- `my.ssdid.wallet.domain.crypto.BouncyCastleInstaller` → `my.ssdid.sdk.domain.crypto.BouncyCastleInstaller`
- `my.ssdid.wallet.domain.did.` → `my.ssdid.sdk.domain.did.`
- `my.ssdid.wallet.domain.logging.` → `my.ssdid.sdk.domain.logging.`

**Important:** To reduce build breakage risk, move one package at a time, verify compilation, then commit:

- [ ] **Step 7: Verify model package compiles**

Run: `cd android && ./gradlew :sdk:ssdid-core:compileDebugKotlin 2>&1 | tail -5`
Fix any import issues before proceeding.

- [ ] **Step 8: Commit model package move**

```bash
git add -A && git commit -m "feat(sdk): move model package to ssdid-core"
```

- [ ] **Step 9: Verify crypto + DID + logging compile and tests pass**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All tests pass in both modules

- [ ] **Step 10: Commit remaining packages**

```bash
git add -A && git commit -m "feat(sdk): move crypto, DID, logging packages to ssdid-core"
```

---

### Task 7: Move vault, transport, and verifier packages to SDK

**Files:**
- Move: `domain/vault/` (4 files) → SDK
- Move: `domain/transport/` (all files including dto/) → SDK
- Move: `domain/verifier/` (all files including offline/) → SDK
- Move: `domain/history/ActivityRepository.kt` → SDK (interface only; keep ActivityModule in di/)
- Move: `domain/settings/` → SDK
- Move: `domain/auth/ClaimValidator.kt` → SDK
- Move corresponding tests
- Update imports

- [ ] **Step 1: Move vault package (4 files)**

Move `Vault.kt`, `VaultImpl.kt`, `VaultStorage.kt`, `KeystoreManager.kt` to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/vault/`. Update packages.

- [ ] **Step 2: Move transport package (all files including dto/)**

Move all 16 transport files to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/transport/`. Update packages.

- [ ] **Step 3: Move verifier package (all files including offline/ and offline/sync/)**

Move all 11 verifier files to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/domain/verifier/`. Update packages.

- [ ] **Step 4: Move history, settings, auth packages**

- `ActivityRepository.kt` → `sdk/.../domain/history/` (interface only)
- `domain/model/ActivityRecord.kt` — already moved in Task 6
- `SettingsRepository.kt`, `TtlProvider.kt` → `sdk/.../domain/settings/`
- `ClaimValidator.kt` → `sdk/.../domain/auth/`

- [ ] **Step 5: Move corresponding tests**

Move all vault, transport, verifier, settings, auth, and history tests to SDK test directory. Key files:
- `domain/vault/VaultTest.kt`, `VaultConcurrencyTest.kt`, `VaultGetCredentialsTest.kt`, `VaultUpdateProfileTest.kt`, `FakeVaultStorage.kt`
- `domain/transport/RetryInterceptorTest.kt`, `dto/AuthDtosTest.kt`, `dto/DtoSerializationTest.kt`
- `domain/verifier/VerifierImplTest.kt`, `offline/` tests (5 files)
- `domain/settings/SettingsRepositoryTest.kt`
- `domain/auth/ClaimValidatorTest.kt`
- `domain/history/ActivityRepositoryTest.kt`

- [ ] **Step 6: Update imports across wallet app**

Global find-and-replace for all moved packages (vault, transport, verifier, history, settings, auth).

- [ ] **Step 7: Verify build and tests**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All pass

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(sdk): move vault, transport, verifier, history, settings, auth to ssdid-core"
```

---

### Task 8: Move remaining domain packages to SDK

**Files:**
- Move: `domain/oid4vci/` (8 files) → SDK
- Move: `domain/oid4vp/` (7 files) → SDK
- Move: `domain/sdjwt/` (7 files) → SDK
- Move: `domain/recovery/` (5 files) → SDK
- Move: `domain/rotation/` (1 file) → SDK
- Move: `domain/revocation/` (4 files) → SDK
- Move: `domain/backup/` (2 files) → SDK
- Move: `domain/device/` (3 files) → SDK
- Move: `domain/notify/NotifyManager.kt`, `NotifyDispatcher.kt`, `LocalNotification.kt` → SDK
- Move: `domain/credential/CredentialIssuanceManager.kt` → SDK
- Move: `domain/SsdidClient.kt` → SDK
- Move corresponding tests

- [ ] **Step 1: Move OID4VCI (8 files) and OID4VP (7 files)**

Move to `sdk/.../domain/oid4vci/` and `sdk/.../domain/oid4vp/`. Update packages.

- [ ] **Step 2: Move SD-JWT (7 files)**

Move to `sdk/.../domain/sdjwt/`. Update packages.

- [ ] **Step 3: Move recovery (5 files), rotation (1 file), revocation (4 files)**

Move to `sdk/.../domain/recovery/`, `sdk/.../domain/rotation/`, `sdk/.../domain/revocation/`. Update packages.

- [ ] **Step 4: Move backup (2 files), device (3 files), credential (1 file)**

Move to `sdk/.../domain/backup/`, `sdk/.../domain/device/`, `sdk/.../domain/credential/`. Update packages.

- [ ] **Step 5: Move remaining notify files (3 files — NotifyManager.kt, NotifyDispatcher.kt, LocalNotification.kt)**

Move to `sdk/.../domain/notify/`. Update packages. The Android-coupled files stay in `platform/notify/`.

- [ ] **Step 6: Move SsdidClient.kt**

Move to `sdk/.../domain/SsdidClient.kt`. Update package to `my.ssdid.sdk.domain`.

- [ ] **Step 7: Move all corresponding tests**

Move test files for OID4VCI (7), OID4VP (7), SD-JWT (5), recovery (5), rotation (1), revocation (3), backup (1), device (1), credential (1), notify (1), SsdidClient (1) to SDK test directory.

- [ ] **Step 8: Update imports across wallet app**

Global find-and-replace for all remaining moved packages.

- [ ] **Step 9: Verify build and tests**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All pass

- [ ] **Step 10: Verify domain/ is empty (except profile/ProfileMigration.kt which stays)**

Run: `ls android/app/src/main/java/my/ssdid/wallet/domain/`
Expected: Only `profile/` directory remains (wallet-specific)

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "feat(sdk): move all remaining domain packages to ssdid-core"
```

---

## Phase 2: Move Platform Defaults and Create Builder API

### Task 9: Move platform default implementations to SDK

**Files:**
- Move: `platform/keystore/AndroidKeystoreManager.kt` → `sdk/.../platform/`
- Move: `platform/storage/DataStoreVaultStorage.kt` → SDK
- Move: `platform/storage/DataStoreBundleStore.kt` → SDK
- Move: `platform/storage/DataStoreCredentialRepository.kt` → SDK
- Move: `platform/storage/DataStoreSettingsRepository.kt` → SDK
- Move: `platform/storage/DataStoreSocialRecoveryStorage.kt` → SDK
- Move: `platform/storage/DataStoreInstitutionalRecoveryStorage.kt` → SDK
- Move: `platform/storage/ActivityRepositoryImpl.kt` → SDK
- Move: `platform/device/AndroidDeviceInfoProvider.kt` → SDK
- Move: `platform/sync/AndroidConnectivityMonitor.kt` → SDK
- Move: `platform/sync/WorkManagerBundleSyncScheduler.kt` → SDK
- Move: `platform/sync/BundleSyncWorker.kt` → SDK
- Move: `platform/sync/BundleSyncWorkerFactory.kt` → SDK
- Move: `platform/security/UrlValidator.kt` → SDK
- Keep in wallet: `platform/biometric/BiometricAuthenticator.kt`, `platform/deeplink/DeepLinkHandler.kt`, `platform/scan/QrScanner.kt`, `platform/i18n/LocalizationManager.kt`, `platform/lifecycle/AppLifecycleObserver.kt`, `platform/notify/` (all 5 files)

- [ ] **Step 1: Move keystore, storage, device, sync, and security files**

Move 13 platform files to `sdk/ssdid-core/src/main/java/my/ssdid/sdk/platform/`. Update package declarations from `my.ssdid.wallet.platform.*` to `my.ssdid.sdk.platform.*`.

- [ ] **Step 2: Move platform tests**

Move `platform/storage/DataStoreVaultStorageTest.kt`, `platform/storage/OnboardingStateTest.kt`, `platform/security/UrlValidatorTest.kt` to SDK test directory.

- [ ] **Step 3: Update imports in wallet app and DI modules**

- [ ] **Step 4: Verify build and tests**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(sdk): move platform default implementations to ssdid-core"
```

---

### Task 10: Create SsdidSdk builder and capability sub-objects

**Files:**
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/SsdidSdk.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/SsdidConfig.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/SsdidError.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/IdentityApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/VaultApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/CredentialsApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/IssuanceApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/PresentationApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/SdJwtApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/VerifierApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/OfflineApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/RecoveryApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/RotationApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/BackupApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/DeviceApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/NotificationsApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/FlowsApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/RevocationApi.kt`
- Create: `android/sdk/ssdid-core/src/main/java/my/ssdid/sdk/api/HistoryApi.kt`
- Create: `android/sdk/ssdid-core/src/test/java/my/ssdid/sdk/SsdidSdkBuilderTest.kt`

- [ ] **Step 1: Write failing test for SDK builder**

```kotlin
// SsdidSdkBuilderTest.kt
package my.ssdid.sdk

import org.junit.Test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import android.content.Context

class SsdidSdkBuilderTest {
    @Test
    fun `builder creates SDK with required registryUrl`() {
        val context = mockk<Context>(relaxed = true)
        val sdk = SsdidSdk.builder(context)
            .registryUrl("https://registry.ssdid.my")
            .build()
        assertThat(sdk).isNotNull()
        assertThat(sdk.identity).isNotNull()
        assertThat(sdk.vault).isNotNull()
        assertThat(sdk.credentials).isNotNull()
    }

    @Test(expected = IllegalStateException::class)
    fun `builder throws without registryUrl`() {
        val context = mockk<Context>(relaxed = true)
        SsdidSdk.builder(context).build()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest --tests "*.SsdidSdkBuilderTest" 2>&1 | tail -5`
Expected: FAIL

- [ ] **Step 3: Create SsdidError sealed class**

```kotlin
// SsdidError.kt
package my.ssdid.sdk

sealed class SsdidError : Exception() {
    data class NetworkError(override val cause: Throwable) : SsdidError()
    data class Timeout(val url: String) : SsdidError()
    data class ServerError(val statusCode: Int, val body: String?) : SsdidError()
    data class UnsupportedAlgorithm(val algorithm: String) : SsdidError()
    data class SigningFailed(val reason: String) : SsdidError()
    data class VerificationFailed(val reason: String) : SsdidError()
    data class StorageError(override val cause: Throwable) : SsdidError()
    data class IdentityNotFound(val did: String) : SsdidError()
    data class CredentialNotFound(val id: String) : SsdidError()
    data class DidResolutionFailed(val did: String, val reason: String) : SsdidError()
    data class IssuanceFailed(val reason: String) : SsdidError()
    data class PresentationFailed(val reason: String) : SsdidError()
    data class NoMatchingCredentials(val requestId: String) : SsdidError()
    data class RecoveryFailed(val reason: String) : SsdidError()
    data class RotationFailed(val reason: String) : SsdidError()
}
```

- [ ] **Step 4: Create SsdidConfig**

```kotlin
// SsdidConfig.kt
package my.ssdid.sdk

import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.logging.SsdidLogger
import my.ssdid.sdk.domain.vault.KeystoreManager
import my.ssdid.sdk.domain.vault.VaultStorage

data class SsdidConfig(
    val registryUrl: String,
    val notifyUrl: String?,
    val emailVerifyUrl: String?,
    val certificatePinningEnabled: Boolean,
    val customKeystoreManager: KeystoreManager?,
    val customVaultStorage: VaultStorage?,
    val additionalCryptoProviders: List<CryptoProvider>,
    val logger: SsdidLogger
)
```

- [ ] **Step 5: Create SsdidSdk builder and all capability sub-objects**

Create `SsdidSdk.kt` with:
- `companion object { fun builder(context: Context): Builder }`
- `Builder` class with fluent API: `registryUrl()`, `notifyUrl()`, `emailVerifyUrl()`, `certificatePinning()`, `keystoreManager()`, `vaultStorage()`, `addCryptoProvider()`, `logger()`, `build()`
- `build()` constructs all internal services and wires them up (replaces DI)
- Properties: `val identity: IdentityApi`, `val vault: VaultApi`, `val credentials: CredentialsApi`, `val issuance: IssuanceApi`, `val presentation: PresentationApi`, `val sdJwt: SdJwtApi`, `val verifier: VerifierApi`, `val offline: OfflineApi`, `val recovery: RecoveryApi`, `val rotation: RotationApi`, `val backup: BackupApi`, `val device: DeviceApi`, `val notifications: NotificationsApi`, `val flows: FlowsApi`, `val revocation: RevocationApi`, `val history: HistoryApi`

Each capability sub-object is a thin wrapper that delegates to the internal domain classes.

- [ ] **Step 6: Create all API sub-object classes**

Each class in `api/` wraps the underlying domain class. Example for `IdentityApi`:

```kotlin
package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.sdk.domain.SsdidClient

class IdentityApi internal constructor(
    private val vault: Vault,
    private val client: SsdidClient
) {
    suspend fun create(name: String, algorithm: Algorithm): Result<Identity> =
        client.initIdentity(name, algorithm)

    suspend fun list(): List<Identity> = vault.listIdentities()

    suspend fun get(did: String): Identity? = vault.getIdentity(did)

    suspend fun delete(did: String): Result<Unit> = vault.deleteIdentity(did)

    suspend fun buildDidDocument(keyId: String): Result<DidDocument> =
        vault.buildDidDocument(keyId)

    suspend fun updateDidDocument(keyId: String): Result<Unit> =
        client.updateDidDocument(keyId)
}
```

Follow the same pattern for all other sub-objects, delegating to the appropriate domain class.

- [ ] **Step 7: Run builder test**

Run: `cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest --tests "*.SsdidSdkBuilderTest" 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(sdk): create SsdidSdk builder, error model, and capability API sub-objects"
```

---

### Task 11: Simplify wallet app DI to use SsdidSdk

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/StorageModule.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/OfflineModule.kt`
- Modify: Feature ViewModels that inject domain classes directly

- [ ] **Step 1: Refactor AppModule to provide SsdidSdk**

Replace the numerous `@Provides` methods with a single `SsdidSdk` instance:

```kotlin
@Provides @Singleton
fun provideSsdidSdk(@ApplicationContext context: Context): SsdidSdk =
    SsdidSdk.builder(context)
        .registryUrl("https://registry.ssdid.my")
        .notifyUrl(BuildConfig.NOTIFY_URL)
        .emailVerifyUrl(BuildConfig.EMAIL_VERIFY_URL)
        .logger(SentryLogger())
        .addCryptoProvider(PqcProvider())
        .certificatePinning(enabled = !BuildConfig.DEBUG)
        .build()
```

Provide individual domain classes from the SDK for backward compatibility during migration:
```kotlin
@Provides fun provideVault(sdk: SsdidSdk): Vault = sdk.internalVault
@Provides fun provideVerifier(sdk: SsdidSdk): Verifier = sdk.internalVerifier
// etc. — gradually remove as ViewModels switch to sdk.* sub-objects
```

- [ ] **Step 2: Remove StorageModule.kt and OfflineModule.kt**

The SDK builder handles all storage and offline wiring internally. Delete these files and move any wallet-specific bindings to AppModule.

- [ ] **Step 3: Update a few key ViewModels to use SsdidSdk directly**

As a proof of concept, update 2-3 ViewModels to inject `SsdidSdk` instead of individual domain classes. Leave the rest for incremental migration.

- [ ] **Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: simplify wallet DI to use SsdidSdk builder"
```

---

### Task 11b: Create ssdid-core-testing artifact

**Files:**
- Create: `android/sdk/ssdid-core-testing/build.gradle.kts`
- Create: `android/sdk/ssdid-core-testing/src/main/java/my/ssdid/sdk/testing/FakeVaultStorage.kt`
- Create: `android/sdk/ssdid-core-testing/src/main/java/my/ssdid/sdk/testing/FakeCryptoProvider.kt`
- Create: `android/sdk/ssdid-core-testing/src/main/java/my/ssdid/sdk/testing/InMemoryBundleStore.kt`
- Create: `android/sdk/ssdid-core-testing/src/main/java/my/ssdid/sdk/testing/InMemoryActivityRepository.kt`
- Modify: `android/settings.gradle.kts`

Ship test doubles for SDK consumers so they can write tests without mocking SDK internals.

- [ ] **Step 1: Create module scaffold**

```bash
mkdir -p android/sdk/ssdid-core-testing/src/main/java/my/ssdid/sdk/testing
```

- [ ] **Step 2: Write build.gradle.kts**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "my.ssdid.sdk.testing"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":sdk:ssdid-core"))
}
```

- [ ] **Step 3: Move FakeVaultStorage from app tests to testing module**

Move `android/app/src/test/java/my/ssdid/wallet/domain/vault/FakeVaultStorage.kt` (already in SDK tests after Task 7) to `ssdid-core-testing`. Create `FakeCryptoProvider`, `InMemoryBundleStore`, `InMemoryActivityRepository` based on existing test fakes.

- [ ] **Step 4: Register module and verify build**

Add `include(":sdk:ssdid-core-testing")` to settings. Verify: `./gradlew :sdk:ssdid-core-testing:compileDebugKotlin`

- [ ] **Step 5: Update SDK tests to depend on testing module**

In `ssdid-core/build.gradle.kts`: `testImplementation(project(":sdk:ssdid-core-testing"))`

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(sdk): create ssdid-core-testing artifact with test doubles"
```

---

## Phase 3: Extract PQC Module

### Task 12: Create ssdid-pqc module and move PQC code

**Files:**
- Create: `android/sdk/ssdid-pqc/build.gradle.kts`
- Create: `android/sdk/ssdid-pqc/src/main/AndroidManifest.xml`
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/PqcProvider.kt` → `android/sdk/ssdid-pqc/src/main/java/my/ssdid/sdk/pqc/PqcProvider.kt`
- Move: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/kazsign/` → `android/sdk/ssdid-pqc/src/main/java/my/ssdid/sdk/pqc/kazsign/`
- Move: `android/app/src/main/cpp/` → `android/sdk/ssdid-pqc/src/main/cpp/`
- Move: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/PqcProviderTest.kt` → SDK PQC tests
- Move: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/kazsign/KazSignerTest.kt` → SDK PQC tests
- Modify: `android/settings.gradle.kts`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Create ssdid-pqc module structure**

```bash
mkdir -p android/sdk/ssdid-pqc/src/main/java/my/ssdid/sdk/pqc
mkdir -p android/sdk/ssdid-pqc/src/test/java/my/ssdid/sdk/pqc
```

- [ ] **Step 2: Write build.gradle.kts for ssdid-pqc**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "my.ssdid.sdk.pqc"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    api(project(":sdk:ssdid-core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
}
```

- [ ] **Step 3: Register in settings.gradle.kts**

```kotlin
include(":sdk:ssdid-pqc")
```

- [ ] **Step 4: Move PqcProvider, kazsign/, and native code**

Move files, update packages to `my.ssdid.sdk.pqc` and `my.ssdid.sdk.pqc.kazsign`. Move `app/src/main/cpp/` to `sdk/ssdid-pqc/src/main/cpp/`. Update CMakeLists.txt paths if needed.

- [ ] **Step 5: Move PQC tests**

Move `PqcProviderTest.kt` and `KazSignerTest.kt` to `sdk/ssdid-pqc/src/test/`.

- [ ] **Step 6: Update wallet app dependency**

In `android/app/build.gradle.kts`:
```kotlin
implementation(project(":sdk:ssdid-pqc"))
```

Remove `externalNativeBuild` and `cmake` config from `app/build.gradle.kts` (now in ssdid-pqc).

- [ ] **Step 7: Update imports and DI**

Update `AppModule.kt` to import PqcProvider from `my.ssdid.sdk.pqc.PqcProvider`.

- [ ] **Step 8: Verify build and tests**

Run: `cd android && ./gradlew :sdk:ssdid-pqc:testDebugUnitTest :app:testDebugUnitTest 2>&1 | tail -10`
Expected: All pass

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat(sdk): extract PQC into ssdid-pqc module"
```

---

## Phase 4: iOS SDK Extraction

### Task 13: Create SsdidCore Swift Package

**Files:**
- Create: `sdk/ios/SsdidCore/Package.swift`
- Create: `sdk/ios/SsdidCore/Sources/SsdidCore/SsdidSdk.swift`
- Create: `sdk/ios/SsdidCore/Sources/SsdidCore/SsdidConfig.swift`
- Create: `sdk/ios/SsdidCore/Sources/SsdidCore/SsdidError.swift`
- Move: `ios/SsdidWallet/Domain/` → `sdk/ios/SsdidCore/Sources/SsdidCore/Domain/`
- Move: `ios/SsdidWallet/Platform/Keychain/KeychainManager.swift` → SDK
- Move: `ios/SsdidWallet/Storage/` → SDK
- Move: `ios/SsdidWallet/Platform/Device/DeviceInfoProvider.swift` → SDK
- Move: `ios/SsdidWallet/Platform/Network/ConnectivityMonitor.swift` → SDK
- Move: `ios/SsdidWallet/Platform/Security/UrlValidator.swift` → SDK
- Move: `ios/SsdidWallet/Sync/BundleSyncManager.swift` → SDK
- Keep in wallet: `Platform/Biometric/`, `Platform/DeepLink/`, `Platform/I18n/`, `Platform/Sentry/`, Feature/, App/, UI/

- [ ] **Step 1: Create Package.swift**

```swift
// sdk/ios/SsdidCore/Package.swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SsdidCore",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "SsdidCore", targets: ["SsdidCore"]),
    ],
    targets: [
        .target(name: "SsdidCore"),
        .testTarget(name: "SsdidCoreTests", dependencies: ["SsdidCore"]),
    ]
)
```

- [ ] **Step 2: Audit iOS Domain/ for UIKit/SwiftUI imports**

Grep `ios/SsdidWallet/Domain/` for `import UIKit`, `import SwiftUI`, or other framework imports that don't belong in the SDK. Abstract them behind protocols.

- [ ] **Step 3: Remove Sentry from iOS Domain/**

Replace `SentrySDK` calls in `SsdidClient.swift` and any other domain files with a `SsdidLogger` protocol (mirror of Android).

- [ ] **Step 4: Move Domain/ to SDK**

Move all 72 domain files to `sdk/ios/SsdidCore/Sources/SsdidCore/Domain/`.

- [ ] **Step 5: Move platform defaults to SDK**

Move `KeychainManager.swift`, `VaultStorage.swift`, `FileCredentialRepository.swift`, `DeviceInfoProvider.swift`, `ConnectivityMonitor.swift`, `UrlValidator.swift`, `BundleSyncManager.swift` to `sdk/ios/SsdidCore/Sources/SsdidCore/Platform/`.

- [ ] **Step 6: Create SsdidSdk.swift builder (mirror Android API)**

```swift
public class SsdidSdk {
    public let identity: IdentityApi
    public let vault: VaultApi
    public let credentials: CredentialsApi
    // ... all sub-objects

    public class Builder { ... }
}
```

- [ ] **Step 7: Create SsdidError.swift (mirror Android)**

- [ ] **Step 8: Update iOS wallet app to depend on local SPM package**

In Xcode project, add local package dependency pointing to `sdk/ios/SsdidCore/`.

- [ ] **Step 9: Update all imports in iOS wallet from `SsdidWallet.Domain.*` to `SsdidCore.*`**

- [ ] **Step 10: Move iOS domain tests to SPM test target**

Move test files from `ios/SsdidWalletTests/Domain/` to `sdk/ios/SsdidCore/Tests/SsdidCoreTests/Domain/`. Update imports from `@testable import SsdidWallet` to `@testable import SsdidCore`.

- [ ] **Step 11: Verify iOS builds and tests pass**

Run: `swift build --package-path sdk/ios/SsdidCore && swift test --package-path sdk/ios/SsdidCore`
Run: `xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -10`

- [ ] **Step 12: Commit**

```bash
git add -A && git commit -m "feat(sdk): create SsdidCore iOS SPM package and migrate domain"
```

---

### Task 14: Create SsdidPqc iOS Package

**Files:**
- Create: `sdk/ios/SsdidPqc/Package.swift`
- Move: `ios/SsdidWallet/Domain/Crypto/PqcProvider.swift` → `sdk/ios/SsdidPqc/Sources/SsdidPqc/PqcProvider.swift`

- [ ] **Step 1: Create Package.swift**

```swift
// sdk/ios/SsdidPqc/Package.swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SsdidPqc",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "SsdidPqc", targets: ["SsdidPqc"]),
    ],
    dependencies: [
        .package(path: "../SsdidCore"),
    ],
    targets: [
        .target(name: "SsdidPqc", dependencies: ["SsdidCore"]),
    ]
)
```

- [ ] **Step 2: Move PqcProvider.swift**

- [ ] **Step 3: Update iOS wallet to depend on SsdidPqc**

- [ ] **Step 4: Verify iOS builds**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(sdk): create SsdidPqc iOS SPM package"
```

---

## Phase 5: Publishing, Documentation, and Samples

### Task 15: Set up GitHub Packages publishing for Android

**Files:**
- Create: `android/sdk/build-logic/settings.gradle.kts`
- Create: `android/sdk/build-logic/build.gradle.kts`
- Create: `android/sdk/build-logic/src/main/kotlin/sdk-publish.gradle.kts`
- Modify: `android/sdk/ssdid-core/build.gradle.kts`
- Modify: `android/sdk/ssdid-pqc/build.gradle.kts`
- Create: `android/sdk/gradle.properties` (version)

- [ ] **Step 1: Create convention plugin for publishing**

Create `sdk-publish.gradle.kts` convention plugin that applies `maven-publish`, configures `groupId = "my.ssdid.sdk"`, reads version from `gradle.properties`, and publishes to GitHub Packages.

- [ ] **Step 2: Apply convention plugin to both SDK modules**

- [ ] **Step 3: Register build-logic as included build**

Add to `android/settings.gradle.kts`:
```kotlin
includeBuild("sdk/build-logic")
```

- [ ] **Step 4: Add API compatibility validator**

Add `org.jetbrains.kotlinx:binary-compatibility-validator` plugin to both modules. Run `apiDump` to generate the initial `.api` baseline file:

Run: `cd android && ./gradlew :sdk:ssdid-core:apiDump :sdk:ssdid-pqc:apiDump`

This creates `api/ssdid-core.api` and `api/ssdid-pqc.api` files that CI will check against.

- [ ] **Step 5: Verify publish task exists**

Run: `cd android && ./gradlew :sdk:ssdid-core:tasks --group=publishing 2>&1 | grep publish`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(sdk): configure GitHub Packages publishing and API compatibility"
```

---

### Task 16: Write SDK documentation

**Files:**
- Create: `sdk/docs/getting-started.md`
- Create: `sdk/docs/configuration.md`
- Create: `sdk/docs/identity-management.md`
- Create: `sdk/docs/credentials.md`
- Create: `sdk/docs/presentations.md`
- Create: `sdk/docs/sd-jwt.md`
- Create: `sdk/docs/verification.md`
- Create: `sdk/docs/recovery.md`
- Create: `sdk/docs/key-rotation.md`
- Create: `sdk/docs/backup.md`
- Create: `sdk/docs/notifications.md`
- Create: `sdk/docs/device-management.md`
- Create: `sdk/docs/custom-implementations.md`
- Create: `sdk/docs/migration-guide.md`

- [ ] **Step 1: Write getting-started.md**

Cover: dependency setup (Maven/SPM), basic initialization, create first identity, sign data. Include both Kotlin and Swift code snippets.

- [ ] **Step 2: Write configuration.md**

Cover: all builder options, certificate pinning, custom implementations, PQC setup, logging.

- [ ] **Step 3: Write identity-management.md**

Cover: create, list, get, delete identities. DID documents. Algorithm selection guide.

- [ ] **Step 4: Write credentials.md and presentations.md**

Cover: OID4VCI flow, credential storage, OID4VP flow, selective disclosure.

- [ ] **Step 5: Write remaining doc pages**

Cover: SD-JWT, verification (online + offline), recovery (all 3 types), key rotation, backup, notifications, device management.

- [ ] **Step 6: Write custom-implementations.md**

Cover: how to override each interface (VaultStorage, KeystoreManager, etc.) with examples.

- [ ] **Step 7: Write migration-guide.md**

Cover: migrating from direct domain imports to SDK usage.

- [ ] **Step 8: Configure Dokka for Android API reference**

Add Dokka plugin to `ssdid-core/build.gradle.kts`:
```kotlin
id("org.jetbrains.dokka") version "2.0.0"
```
Configure output to `sdk/docs/api-reference/android/`. Run: `./gradlew :sdk:ssdid-core:dokkaHtml`

- [ ] **Step 9: Configure DocC for iOS API reference**

Add DocC catalog to `sdk/ios/SsdidCore/Sources/SsdidCore/SsdidCore.docc/`. Build: `swift package generate-documentation --target SsdidCore`

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "docs(sdk): add user manual, Dokka, and DocC API reference"
```

---

### Task 17: Create sample apps

**Files:**
- Create: `sdk/samples/android/basic-identity/` (Android app module)
- Create: `sdk/samples/android/credential-flow/` (Android app module)
- Create: `sdk/samples/ios/BasicIdentity/` (Xcode project)
- Create: `sdk/samples/ios/CredentialFlow/` (Xcode project)

- [ ] **Step 1: Create Android basic-identity sample**

Minimal single-activity Compose app: init SDK, create identity, display DID, sign a message, verify signature.

- [ ] **Step 2: Create Android credential-flow sample**

Multi-screen Compose app: scan QR → handle OID4VCI offer → show credential → handle OID4VP request → present.

- [ ] **Step 3: Create Android custom-storage sample**

Android app demonstrating how to override `VaultStorage` with Room DB and `KeystoreManager` with a custom implementation.

- [ ] **Step 4: Create iOS BasicIdentity sample**

SwiftUI app mirroring the Android basic-identity sample.

- [ ] **Step 5: Create iOS CredentialFlow sample**

SwiftUI app mirroring the Android credential-flow sample.

- [ ] **Step 6: Create iOS CustomStorage sample**

SwiftUI app demonstrating how to override `VaultStorage` with Core Data.

- [ ] **Step 7: Register Android sample apps as Gradle modules**

Add to `android/settings.gradle.kts`:
```kotlin
include(":sdk:samples:basic-identity")
include(":sdk:samples:credential-flow")
include(":sdk:samples:custom-storage")
```

- [ ] **Step 8: Verify all sample apps build**

Run: `cd android && ./gradlew :sdk:samples:basic-identity:assembleDebug :sdk:samples:credential-flow:assembleDebug :sdk:samples:custom-storage:assembleDebug 2>&1 | tail -5`

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat(sdk): add sample apps for Android and iOS"
```

---

### Task 18: Set up SDK CI pipeline

**Files:**
- Create: `.github/workflows/sdk-ci.yml`

- [ ] **Step 1: Create CI workflow**

```yaml
name: SDK CI
on:
  pull_request:
    paths: ['android/sdk/**', 'sdk/**']
  push:
    tags: ['sdk-v*']

jobs:
  android-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 17, distribution: temurin }
      - run: cd android && ./gradlew :sdk:ssdid-core:testDebugUnitTest
      - run: cd android && ./gradlew :sdk:ssdid-pqc:testDebugUnitTest
      - run: cd android && ./gradlew :sdk:ssdid-core:apiCheck
      - run: cd android && ./gradlew :sdk:samples:basic-identity:assembleDebug :sdk:samples:credential-flow:assembleDebug :sdk:samples:custom-storage:assembleDebug

  android-publish:
    if: startsWith(github.ref, 'refs/tags/sdk-v')
    needs: android-build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 17, distribution: temurin }
      - run: cd android && ./gradlew :sdk:ssdid-core:publish :sdk:ssdid-pqc:publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  ios-build:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - run: swift build --package-path sdk/ios/SsdidCore
      - run: swift test --package-path sdk/ios/SsdidCore
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "ci: add SDK CI pipeline for Android and iOS"
```

---

## Summary

| Phase | Tasks | What It Delivers |
|-------|-------|------------------|
| Phase 0 | Tasks 1-4 | Domain decoupled from Android/Hilt/Sentry |
| Phase 1 | Tasks 5-8 | `ssdid-core` module with all domain code |
| Phase 2 | Tasks 9-11, 11b | Platform defaults + SsdidSdk builder API + test doubles |
| Phase 3 | Task 12 | `ssdid-pqc` optional module |
| Phase 4 | Tasks 13-14 | iOS SPM packages (SsdidCore + SsdidPqc) |
| Phase 5 | Tasks 15-18 | Publishing, docs (Dokka/DocC), samples (6 apps), CI |

Each task produces a compilable, testable state with a commit. The wallet app remains functional throughout.
