# Mandatory Biometric Lock Design

**Date:** 2026-03-27
**Status:** Draft
**Scope:** Enforce biometric authentication on every app open, both platforms

## Overview

Make biometric authentication mandatory — no opt-out, no timeout. The lock screen appears on every cold start and every return from background. Devices without biometric hardware fall back to device passcode with a one-time warning.

## Requirements

- Biometric lock on every app open (cold start + every foreground resume)
- No biometric toggle in Settings (always on)
- No "Skip" option during onboarding biometric setup
- Device passcode fallback for hardware without biometric
- One-time warning when using passcode fallback
- Block onboarding if device has no passcode at all
- Data migration for existing users who previously disabled biometric

## Section 1: Lock Behavior

### When the lock screen appears

| Trigger | Current | New |
|---------|---------|-----|
| Cold start (app launched) | No lock | Lock always |
| Process-death restart | No lock | Lock always |
| Return from background (any duration) | Lock only after 5min timeout | Lock always |
| Screen on while app in foreground | No lock | No lock (unchanged) |

**Design decision — no grace period:** Every foreground resume triggers authentication, even after a 1-second switch to another app. This is a deliberate security choice for a wallet holding cryptographic keys and identity credentials. The friction is accepted as the cost of maximum protection.

**Process death handling:**
- Android: `isLocked` is initialized as `mutableStateOf(true)` in the field initializer, so both fresh cold starts and process-death restorations start locked regardless of `savedInstanceState`.
- iOS: `@State private var isLocked = true` reinitializes on process death, ensuring the lock is always shown.

### Changes

1. Remove auto-lock timeout logic — every foreground event triggers lock
2. Remove biometric toggle from Settings — mandatory, no opt-out
3. Remove "Skip" button from BiometricSetupScreen — must enable during onboarding
4. Add lock on cold start — app starts in locked state

### Device state detection (three states)

The app must distinguish three device states, not just "has biometric" vs "doesn't":

| State | Detection | Behavior |
|-------|-----------|----------|
| **Biometric available + enrolled** | Android: `BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS`. iOS: `LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics)` returns true | Use biometric authentication. Best experience. |
| **Biometric hardware present but not enrolled** | Android: `canAuthenticate() == BIOMETRIC_ERROR_NONE_ENROLLED`. iOS: `canEvaluatePolicy` fails with `LAError.biometryNotEnrolled` | Prompt user to enroll: "Your device supports Face ID/fingerprint but none are enrolled. Please set up biometric authentication in your device Settings for the best security experience." Fall back to device passcode in the meantime. |
| **No biometric hardware** | Android: `canAuthenticate() == BIOMETRIC_ERROR_NO_HARDWARE`. iOS: `canEvaluatePolicy` fails with `LAError.biometryNotAvailable` | Fall back to device passcode. Show one-time warning banner. |

### Passcode fallback

When biometric is unavailable (states 2 or 3 above):
- Use `DEVICE_CREDENTIAL` on Android, `.deviceOwnerAuthentication` on iOS
- Show one-time warning: "Your device doesn't support biometric authentication. Device passcode is being used instead. For stronger security, use a device with Face ID or fingerprint."
- Store per-device flag `ssdid_biometric_warning_shown` in the same DataStore/UserDefaults used for settings
- Reset the flag if biometric becomes available (e.g., user enrolls a fingerprint) — so the warning can reappear if they later remove biometric enrollment

**Security note:** A 4-digit PIN is significantly weaker than biometric authentication. This is an accepted tradeoff for device compatibility. Transaction signing (`authenticateWithCrypto`) continues to require `BIOMETRIC_STRONG` only (no passcode fallback) for hardware-backed key operations.

## Section 2: Code Changes

### Android

| File | Change |
|------|--------|
| `MainActivity.kt` | Set `isLocked = mutableStateOf(true)` as field initializer (covers cold start + process death). Remove elapsed-time check in `onResume()` — always set `isLocked.value = true`. |
| `LockScreen.kt` | Detect biometric state (3 states above). If biometric unavailable, call `authenticateWithFallback()` using `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. Show one-time warning banner for no-hardware state. Show enrollment prompt for not-enrolled state. |
| `BiometricSetupScreen.kt` | Remove "Skip for now" button. Detect 3 biometric states and show appropriate button/message. |
| `SettingsScreen.kt` | Remove biometric toggle row. Remove "Auto-Lock" row. |
| `SettingsViewModel.kt` | Mark `biometricEnabled`/`setBiometricEnabled()` and `autoLockMinutes`/`setAutoLockMinutes()` as `@Deprecated`. They still function but are no longer called from UI. |
| `DataStoreSettingsRepository.kt` | Add migration on init: force `biometric_enabled = true` for existing users who previously disabled it. |

### iOS

| File | Change |
|------|--------|
| `SsdidWalletApp.swift` | Set `@State private var isLocked = true` (covers cold start + process death). Remove elapsed-time check in `onChange(of: scenePhase)` — always set `isLocked = true` on `.active` after `.background`. |
| `LockOverlay` (in SsdidWalletApp) | Switch from `.deviceOwnerAuthenticationWithBiometrics` to `.deviceOwnerAuthentication` (allows passcode fallback). Detect 3 biometric states. Show one-time warning/enrollment prompt as appropriate. |
| `BiometricSetupScreen.swift` | Remove "Skip for now" button. Detect 3 states, show appropriate button/message. |
| `SettingsScreen.swift` | Remove biometric toggle row (`@AppStorage("ssdid_biometric_enabled")`). Remove "Auto-Lock" row (`@AppStorage("ssdid_auto_lock_minutes")`). Remove these `@AppStorage` properties entirely from the view. |
| `UserDefaultsSettingsRepository.swift` | Add migration on init: force `ssdid_biometric_enabled = true` for existing users. |

### Unchanged

- `BiometricAuthenticator` on both platforms — already supports biometric + device credential
- Transaction signing — still uses `authenticateWithCrypto()` with `BIOMETRIC_STRONG` only (no passcode fallback)
- Vault encryption — keys remain Keystore/Secure Enclave protected

### Data Migration

On first launch after update, both platforms must:
1. Read `biometric_enabled` / `ssdid_biometric_enabled` from storage
2. If `false`, set to `true`
3. This ensures existing users who disabled biometric are re-enrolled into mandatory auth

This runs in the SettingsRepository init (DataStore migration on Android, `registerDefaults` + forced write on iOS).

## Section 3: Onboarding Flow

### Current
```
Welcome -> Display Name -> Email (optional) -> Algorithm -> Biometric Setup (skippable) -> Wallet Home
```

### New
```
Welcome -> Display Name -> Email (optional) -> Algorithm -> Biometric Setup (mandatory) -> Wallet Home
```

### BiometricSetupScreen behavior

1. **Biometric available + enrolled:** Shows "Enable Biometric Authentication" button. User must tap and successfully authenticate to proceed. Tapping triggers the system biometric prompt (Face ID, fingerprint, etc.).

2. **Biometric hardware present but not enrolled:** Shows enrollment guidance: "Your device supports [Face ID / fingerprint] but none are set up. Please enroll in your device Settings for the best security." Shows "Use Device Passcode for Now" button as fallback. Shows one-time warning. Tapping the button triggers the system passcode prompt — no custom input UI needed.

3. **No biometric hardware, device passcode set:** Shows "Enable Device Passcode" button. One-time warning about biometric being recommended. Tapping triggers the system passcode prompt — no custom input UI needed.

4. **No passcode set at all:** Shows message: "Please set up a device passcode in your phone's Settings to continue. SSDID Wallet requires authentication to protect your identity."
   - **Android:** "Open Settings" button using `Settings.ACTION_SECURITY_SETTINGS` intent
   - **iOS:** Instructional text: "Go to Settings > Face ID & Passcode" (no reliable deep link to device passcode settings on iOS; `UIApplication.openSettingsURL` only opens app settings)
   - No way to proceed until a passcode is configured. App checks on return from Settings.

No "Skip" or "Later" option exists.

## Section 4: Testing

### E2E Test Updates

- Remove or update any test that toggles biometric on/off in Settings (the toggle no longer exists)
- Remove any test that verifies auto-lock timeout behavior (no longer configurable)

### New Test Cases

| Test | Platform | Type |
|------|----------|------|
| Lock appears on cold start | Both | UI (Espresso/XCUITest) |
| Lock appears on every foreground resume (no grace period) | Both | UI |
| Onboarding cannot be skipped without biometric/passcode | Both | UI |
| Passcode fallback shown when no biometric hardware | Both | UI (mock BiometricAuthenticator) |
| One-time warning appears and doesn't repeat | Both | UI |
| Enrollment prompt for hardware-present-but-not-enrolled | Both | UI |
| Block onboarding when no passcode at all | Both | UI |
| Data migration: existing user with biometric=false gets re-enabled | Both | Unit |
| Transaction signing still requires BIOMETRIC_STRONG (no passcode fallback) | Both | Instrumented |

## Out of Scope

- App-specific PIN/password (relies on device credentials)
- "Change Password" implementation (remains a stub)
- Per-screen authentication (e.g., requiring re-auth for specific sensitive actions beyond tx signing)
- Remote lock/wipe
- Minimum passcode complexity enforcement (not possible via API)
