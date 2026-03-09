# SSDID Wallet Android Completion — Design Document

**Date:** 2026-03-09
**Status:** Draft
**Scope:** Complete the Android reference implementation to production readiness

## Goal

Close all gaps between the current Android implementation and the specifications in:
- `docs/plans/2026-03-06-ssdid-wallet-app-design.md` (app design)
- `docs/12.SSDID-Gap-Analysis-And-Remediation.md` (gap analysis)
- `docs/SSDID-System-Flows.md` (protocol flows)

Organized into 4 phases by priority: critical fixes, incomplete features, missing features, and robustness.

---

## Current State

### What's Built (Working)
- 17 Compose screens across 13 feature modules
- Full navigation graph with deep link handling (`ssdid://` scheme)
- Hilt DI with dual crypto providers (Classical + PQC)
- 4 core SsdidClient flows: `initIdentity`, `registerWithService`, `authenticate`, `signTransaction`
- Hardware keystore (TEE/StrongBox) with AES-256-GCM key wrapping
- QR scanning (CameraX + ML Kit)
- Key rotation (KERI two-step pre-commitment) — local only
- Recovery key generation (Tier 1) — local only
- Backup creation/restore (AES-256-GCM + PBKDF2 + HMAC) — no file I/O
- Verifier with W3C Data Integrity proof verification
- Activity history model and repository
- String resources in 3 locales (en, ms, zh)
- 19 unit tests covering domain layer
- Dark theme (Material 3)

### What's Broken or Missing

| # | Issue | Severity |
|---|-------|----------|
| 1 | App always starts at onboarding (no skip if already set up) | Critical |
| 2 | Biometric gates are UI-only — signing/auth happens without biometric | Critical |
| 3 | Settings don't persist (toggle states reset on restart) | High |
| 4 | Activity logging not wired into SsdidClient flows | High |
| 5 | Backup has no file picker (can't save/load from storage) | High |
| 6 | Key rotation doesn't sync to Registry | High |
| 7 | Recovery restoration flow missing (can generate key, can't use it) | High |
| 8 | DID update/deactivation never called from client | Medium |
| 9 | Hardcoded strings in onboarding screens | Medium |
| 10 | Device management is a stub | Medium |
| 11 | No in-app language switching | Medium |
| 12 | No credential issuance beyond registration | Medium |
| 13 | No network error handling / retry / offline | Medium |
| 14 | 0 ViewModel tests, 0 instrumented tests | Medium |

---

## Phase 1: Critical UX & Security Fixes

### 1.1 Onboarding Bypass

**Problem:** `NavGraph.kt` hardcodes `startDestination = Screen.Onboarding.route`. After creating an identity, closing and reopening the app shows onboarding again.

**Design:**
- Add `hasCompletedOnboarding(): Boolean` to `VaultStorage` interface
- Check at NavGraph composition time: if identities exist OR onboarding flag set, start at `WalletHome`
- Set flag after first identity creation (not after viewing onboarding — user must complete setup)
- Store as a DataStore preference (`onboarding_completed: Boolean`)

**Files to modify:**
- `platform/storage/DataStoreVaultStorage.kt` — add preference key + getter/setter
- `domain/vault/VaultStorage.kt` — add interface method
- `ui/navigation/NavGraph.kt` — dynamic `startDestination`
- `feature/onboarding/OnboardingScreen.kt` — set flag on completion

### 1.2 Biometric Gates

**Problem:** `AuthFlowScreen`, `TxSigningScreen`, and `BackupScreen` show biometric UI cards but never call `BiometricAuthenticator.authenticate()`. Users can sign transactions and authenticate without biometric confirmation.

**Design:**
- Wire `BiometricAuthenticator` into ViewModels via Hilt injection
- Gate these operations behind successful biometric auth:
  - Transaction signing (`TxSigningViewModel.confirmTransaction()`)
  - Authentication (`AuthFlowViewModel.authenticate()`)
  - Backup creation (`BackupViewModel.createBackup()`)
  - Backup restore (`BackupViewModel.restoreBackup()`)
- On biometric failure: show error, do not proceed
- On biometric unavailable (device has no biometric): fall through (log warning)

**Files to modify:**
- `feature/transaction/TxSigningScreen.kt` — call biometric before signing
- `feature/auth/AuthFlowScreen.kt` — call biometric before auth
- `feature/backup/BackupScreen.kt` — call biometric before backup ops
- Each corresponding ViewModel — inject `BiometricAuthenticator`, add auth step

### 1.3 Settings Persistence

**Problem:** Settings toggles (biometric, auto-lock, language, default algorithm) are local `remember` state — lost on rotation or app restart.

**Design:**
- Create `SettingsRepository` backed by DataStore Preferences
- Keys: `biometric_enabled`, `auto_lock_minutes`, `default_algorithm`, `language`, `theme`
- Inject into `SettingsScreen` (or a `SettingsViewModel`)
- Read on screen load, write on toggle change
- `auto_lock_minutes` options: 1, 5, 15, 30 (default: 5)
- `default_algorithm` maps to `Algorithm` enum name

**Files to create:**
- `domain/settings/SettingsRepository.kt` — interface
- `platform/storage/DataStoreSettingsRepository.kt` — implementation
- `feature/settings/SettingsViewModel.kt` — ViewModel with state management

**Files to modify:**
- `feature/settings/SettingsScreen.kt` — use ViewModel instead of local state
- `di/AppModule.kt` — provide `SettingsRepository`

### 1.4 Activity Logging Integration

**Problem:** `ActivityRepository` exists with `addActivity()` method, but SsdidClient flows may not call it. Events happen silently.

**Design:**
- Inject `ActivityRepository` into `SsdidClient`
- Log after each successful flow:
  - `initIdentity` → `IDENTITY_CREATED`
  - `registerWithService` → `SERVICE_REGISTERED` + `CREDENTIAL_RECEIVED`
  - `authenticate` → `AUTHENTICATED`
  - `signTransaction` → `TX_SIGNED`
- Also log from managers:
  - `KeyRotationManager.executeRotation` → `KEY_ROTATED`
  - `BackupManager.createBackup` → `BACKUP_CREATED`
- Include relevant metadata: DID, service URL, algorithm

**Files to modify:**
- `domain/SsdidClient.kt` — inject `ActivityRepository`, log in each flow
- `domain/rotation/KeyRotationManager.kt` — log rotation
- `domain/backup/BackupManager.kt` — log backup creation
- `di/AppModule.kt` — provide `ActivityRepository` singleton

---

## Phase 2: Incomplete Features

### 2.1 Backup File I/O

**Problem:** Backup encrypt/decrypt logic works, but there's no file picker to save or load backup files from device storage.

**Design:**
- Use Android `ActivityResultContracts.CreateDocument` for save (produces SAF file picker)
- Use `ActivityResultContracts.OpenDocument` for restore
- File type: `application/octet-stream`, suggested name: `ssdid-backup-{date}.enc`
- Flow:
  1. User enters passphrase → `BackupManager.createBackup()` returns `ByteArray`
  2. SAF picker opens → user chooses location
  3. Write bytes to chosen URI via `ContentResolver`
  4. For restore: SAF picker → read bytes → `BackupManager.restoreBackup(bytes, passphrase)`

**Files to modify:**
- `feature/backup/BackupScreen.kt` — add `rememberLauncherForActivityResult` for save/load
- `feature/backup/BackupViewModel.kt` — handle URI-based I/O

### 2.2 Key Rotation → Registry Sync

**Problem:** `KeyRotationManager` generates new keys and stores locally, but never publishes the updated DID Document to the Registry.

**Design:**
- After `executeRotation()`:
  1. Build updated DID Document with new verification method
  2. Create proof with `capabilityInvocation` purpose (signed by old key, during grace period)
  3. Call `RegistryApi.updateDid(did, UpdateDidRequest(document, proof))`
- After `prepareRotation()`:
  1. Build updated DID Document adding `nextKeyHash` field
  2. Publish to Registry (pre-commitment is public)
- Add `nextKeyHash: String?` field to `DidDocument` model (per gap analysis spec)
- Grace period: 5 minutes where both old and new keys are valid

**Files to modify:**
- `domain/model/DidDocument.kt` — add `nextKeyHash` field
- `domain/rotation/KeyRotationManager.kt` — call Registry API after rotation
- `domain/transport/dto/RegistryDtos.kt` — add `UpdateDidRequest`/`UpdateDidResponse`
- `domain/vault/VaultImpl.kt` — `buildDidDocument()` should include all active keys

### 2.3 Recovery Restoration Flow

**Problem:** User can generate a recovery key (Tier 1), but there's no flow to use it to recover access on a new device.

**Design:**
- New screen: `RecoveryRestoreScreen` — user enters recovery private key (Base64)
- Flow:
  1. User inputs recovery private key on new device
  2. App verifies it matches the recovery public key stored in DID Document (via Registry resolve)
  3. App generates new primary keypair
  4. Signs DID Document update with recovery key (using `capabilityInvocation`)
  5. Publishes updated DID Document to Registry
  6. Stores new identity locally
- Recovery key must be listed in DID Document's `capabilityInvocation` during `RecoverySetup`

**Files to create:**
- `feature/recovery/RecoveryRestoreScreen.kt` — input UI
- `feature/recovery/RecoveryRestoreViewModel.kt` — restoration logic

**Files to modify:**
- `domain/recovery/RecoveryManager.kt` — add `restoreWithRecoveryKey()` method
- `domain/vault/VaultImpl.kt` — `buildDidDocument()` to include recovery key in `capabilityInvocation`
- `ui/navigation/NavGraph.kt` — add route
- `ui/navigation/Screen.kt` — add screen definition

### 2.4 DID Update & Deactivation

**Problem:** `RegistryApi` defines `updateDid()` and `deactivateDid()` but SsdidClient never calls them.

**Design:**
- Add to `SsdidClient`:
  - `updateDidDocument(keyId: String)` — rebuilds and publishes current DID Document
  - `deactivateDid(keyId: String)` — marks DID as deactivated (irreversible, with confirmation)
- Add deactivation UI to `IdentityDetailScreen` with destructive action confirmation dialog
- DID update is used internally by key rotation and recovery (not directly exposed as a screen)

**Files to modify:**
- `domain/SsdidClient.kt` — add `updateDidDocument()`, `deactivateDid()`
- `feature/identity/IdentityDetailScreen.kt` — add deactivate button with confirmation
- `domain/transport/dto/RegistryDtos.kt` — add request/response types if missing

### 2.5 Hardcoded Onboarding Strings

**Problem:** Onboarding slide titles/descriptions are hardcoded in English in `OnboardingScreen.kt` instead of using string resources.

**Design:**
- Move all hardcoded strings to `res/values/strings.xml`
- Add corresponding entries to `values-ms/strings.xml` and `values-zh/strings.xml`
- Reference via `stringResource(R.string.onboarding_*)` in Composables

**Files to modify:**
- `feature/onboarding/OnboardingScreen.kt` — replace hardcoded text
- `res/values/strings.xml`, `res/values-ms/strings.xml`, `res/values-zh/strings.xml` — add entries

---

## Phase 3: Missing Features

### 3.1 Device Management — Multi-Device Protocol

**Problem:** Stub screen only. Gap analysis (Gap 3) specifies a device enrollment protocol.

**Architecture:** The Registry already supports multiple verification methods per DID (max 10 keys). Device management maps to adding/removing verification methods with device metadata.

**Design — Wallet Side:**

**Data model:**
- `DeviceInfo(deviceId: String, name: String, platform: String, keyId: String, enrolledAt: String, isPrimary: Boolean)`
- Store device list in DID Document's `service` array (type: `SsdidDeviceService`) or derive from verification methods

**Enrollment flow (primary device initiates):**
1. Primary device generates QR containing: `{ did, pairingChallenge, registryUrl, action: "pair" }`
2. New device scans QR, generates local keypair
3. New device signs pairing challenge with new key
4. New device sends `{ publicKey, signedChallenge, deviceName, platform }` to primary device (via Registry relay endpoint)
5. Primary device verifies signature
6. Primary device builds updated DID Document (adds new key to `authentication` only — NOT `capabilityInvocation`)
7. Primary device signs and publishes update to Registry
8. New device resolves DID Document, confirms its key is present
9. New device stores identity locally

**Device revocation:**
- Primary device signs DID Document update removing the target device's verification method
- Publishes to Registry

**Constraint:** Only primary device holds `capabilityInvocation` — secondary devices can authenticate but cannot modify the DID Document.

**Registry changes needed:**
- Relay endpoint: `POST /api/did/:did/pair` — temporary storage for pairing data (TTL: 5 minutes)
- `GET /api/did/:did/pair/:challenge` — new device polls for approval
- `POST /api/did/:did/pair/:challenge/approve` — primary device approves
- No new Mnesia tables needed — pairing is ephemeral (ETS or process state)

**Files to create (wallet):**
- `domain/device/DeviceManager.kt` — enrollment/revocation logic
- `domain/device/DeviceInfo.kt` — data model
- `domain/transport/dto/DeviceDtos.kt` — pairing request/response DTOs
- `feature/device/DeviceEnrollScreen.kt` — QR display for primary, scan for secondary
- `feature/device/DeviceEnrollViewModel.kt`

**Files to modify (wallet):**
- `feature/device/DeviceManagementScreen.kt` — replace stub with real device list
- `feature/device/DeviceManagementViewModel.kt` — load devices from DID Document
- `domain/transport/RegistryApi.kt` — add pairing endpoints
- `ui/navigation/NavGraph.kt` — add enrollment route

**Files to create (registry):**
- `lib/ssdid_registry_web/controllers/pairing_controller.ex` — pairing relay
- `test/ssdid_registry_web/controllers/pairing_controller_test.exs`

### 3.2 In-App Language Switching

**Problem:** String resources exist in 3 locales but no runtime switching. Settings shows "English" hardcoded.

**Design:**
- Create `LocalizationManager` that wraps `AppCompatDelegate.setApplicationLocales()`
- Store selected locale in `SettingsRepository` (`language: String`, values: `en`, `ms`, `zh`)
- On change: call `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`
- This triggers Android's built-in per-app language support (API 33+ native, AppCompat backport)
- Add `android:localeConfig="@xml/locales_config"` to manifest

**Files to create:**
- `platform/i18n/LocalizationManager.kt` — wraps AppCompat locale API
- `res/xml/locales_config.xml` — declares supported locales

**Files to modify:**
- `feature/settings/SettingsScreen.kt` — language picker dialog
- `feature/settings/SettingsViewModel.kt` — call `LocalizationManager`
- `AndroidManifest.xml` — add `localeConfig` attribute

### 3.3 Credential Issuance Beyond Registration

**Problem:** VCs only arrive via `registerWithService()`. No standalone credential offer/accept flow.

**Design:**
- Support credential offer via deep link: `ssdid://credential-offer?url=...&offer_id=...`
- Flow:
  1. Issuer presents QR/deep link with credential offer URL
  2. Wallet fetches offer details from issuer endpoint
  3. User reviews credential details and accepts/rejects
  4. Wallet signs acceptance with identity key
  5. Issuer returns signed VC
  6. Wallet stores VC and logs `CREDENTIAL_RECEIVED`
- Transport: Add `IssuerApi` Retrofit interface with:
  - `GET /credential-offer/{offerId}` → offer details
  - `POST /credential-offer/{offerId}/accept` → returns VC

**Files to create:**
- `domain/credential/CredentialIssuanceManager.kt` — offer fetch, accept, store
- `domain/transport/IssuerApi.kt` — Retrofit interface
- `domain/transport/dto/CredentialDtos.kt` — offer/accept DTOs
- `feature/credentials/CredentialOfferScreen.kt` — review & accept UI
- `feature/credentials/CredentialOfferViewModel.kt`

**Files to modify:**
- `platform/deeplink/DeepLinkHandler.kt` — handle `credential-offer` action
- `platform/scan/QrScanner.kt` — parse `credential-offer` action
- `ui/navigation/NavGraph.kt` — add route
- `ui/navigation/Screen.kt` — add screen

---

## Phase 4: Robustness & Testing

### 4.1 Network Error Handling

**Problem:** SsdidClient throws raw exceptions from Retrofit. No retry, no timeout recovery, no offline graceful degradation.

**Design:**
- Create `NetworkResult<T>` sealed class: `Success(data)`, `NetworkError(cause)`, `ServerError(code, message)`, `Timeout`
- Wrap all `httpClient` calls in SsdidClient with `NetworkResult`
- ViewModels map `NetworkResult` to UI states with user-friendly messages
- Add OkHttp retry interceptor (max 2 retries for 5xx, exponential backoff)
- Add connection check before network calls (show offline banner)

**Files to create:**
- `domain/transport/NetworkResult.kt` — sealed result type
- `domain/transport/RetryInterceptor.kt` — OkHttp interceptor

**Files to modify:**
- `domain/SsdidClient.kt` — return `NetworkResult` instead of `Result`
- `domain/transport/SsdidHttpClient.kt` — add retry interceptor
- All ViewModels — handle `NetworkResult` variants

### 4.2 Test Coverage

**Current:** 19 test files, all domain layer. 0 ViewModel tests. 0 instrumented tests.

**Design — Priority tiers:**

**Tier 1 (Critical — security code):**
- `AndroidKeystoreManagerTest.kt` — hardware key wrapping (Robolectric)
- `DataStoreVaultStorageTest.kt` — identity/credential persistence (Robolectric)
- `KazSignerTest.kt` — PQC operations with mocked JNI (Mockk)

**Tier 2 (High — business logic):**
- `SsdidHttpClientTest.kt` — OkHttp configuration, interceptors
- `SettingsRepositoryTest.kt` — preference persistence
- `DeviceManagerTest.kt` — enrollment/revocation logic

**Tier 3 (Medium — UI logic):**
- `CreateIdentityViewModelTest.kt`
- `AuthFlowViewModelTest.kt`
- `TxSigningViewModelTest.kt`
- `RegistrationViewModelTest.kt`
- `BackupViewModelTest.kt`

**Tier 4 (Nice-to-have — integration):**
- Compose UI tests for critical flows (onboarding → create identity → wallet home)
- End-to-end test with mock server

---

## Dependency Graph

```
Phase 1.3 (Settings persistence) ← Phase 3.2 (Language switching)
Phase 1.1 (Onboarding bypass) ← standalone
Phase 1.2 (Biometric gates) ← standalone
Phase 1.4 (Activity logging) ← standalone

Phase 2.4 (DID update/deactivate) ← Phase 2.2 (Key rotation sync)
Phase 2.4 (DID update/deactivate) ← Phase 2.3 (Recovery restoration)
Phase 2.1 (Backup file I/O) ← standalone
Phase 2.5 (Onboarding strings) ← standalone

Phase 2.2 (Key rotation sync) ← Phase 3.1 (Device management, partial)
Phase 2.3 (Recovery restoration) ← Phase 3.1 (Device management, partial)

Phase 3.3 (Credential issuance) ← standalone
Phase 4.1 (Network errors) ← standalone (but improves all phases)
Phase 4.2 (Tests) ← after all features implemented
```

## Execution Order (Recommended)

1. Phase 1.1 — Onboarding bypass
2. Phase 1.2 — Biometric gates
3. Phase 1.3 — Settings persistence
4. Phase 1.4 — Activity logging
5. Phase 2.5 — Onboarding strings (quick win)
6. Phase 2.1 — Backup file I/O
7. Phase 2.4 — DID update & deactivation
8. Phase 2.2 — Key rotation → Registry sync
9. Phase 2.3 — Recovery restoration
10. Phase 3.2 — In-app language switching
11. Phase 3.3 — Credential issuance
12. Phase 3.1 — Device management (largest item)
13. Phase 4.1 — Network error handling
14. Phase 4.2 — Test coverage

---

## Out of Scope

- iOS / HarmonyOS NEXT implementations
- Social recovery (Tier 2) and institutional recovery (Tier 3) — future
- Credential revocation registry (Gap 4) — requires Registry changes beyond wallet
- Offline verification bundles (Gap 6) — future
- Registry HA (Gap 7) — infrastructure, not wallet
- Presentation exchange (W3C VCDM) — future, after credential issuance
- Push notifications — future
- Multi-device key sync (keys stay device-local; each device has its own key)
