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

## Section 1: Lock Behavior

### When the lock screen appears

| Trigger | Current | New |
|---------|---------|-----|
| Cold start (app launched) | No lock | Lock always |
| Return from background (any duration) | Lock only after 5min timeout | Lock always |
| Screen on while app in foreground | No lock | No lock (unchanged) |

### Changes

1. Remove auto-lock timeout logic — every foreground event triggers lock
2. Remove biometric toggle from Settings — mandatory, no opt-out
3. Remove "Skip" button from BiometricSetupScreen — must enable during onboarding
4. Add lock on cold start — app starts in locked state

### Device without biometric hardware

If `BiometricAuthenticator.canAuthenticate()` returns false (no biometric enrolled or no hardware):
- Fall back to device passcode (`DEVICE_CREDENTIAL` on Android, `deviceOwnerAuthentication` on iOS)
- Show a one-time warning banner: "Your device doesn't support biometric authentication. Device passcode is being used instead. For stronger security, use a device with Face ID or fingerprint."
- Store flag `ssdid_biometric_warning_shown` (UserDefaults/DataStore) so warning appears only once

## Section 2: Code Changes

### Android

| File | Change |
|------|--------|
| `MainActivity.kt` | Set `isLocked = true` in `onCreate()`. Remove elapsed-time check in `onResume()` — always set `isLocked = true`. |
| `LockScreen.kt` | Add device-passcode fallback path. Show one-time warning banner if no biometric hardware. |
| `BiometricSetupScreen.kt` | Remove "Skip for now" button. Cannot proceed without enabling biometric or device passcode. |
| `SettingsScreen.kt` | Remove biometric toggle row. Remove "Auto-Lock" row. |
| `SettingsViewModel.kt` | Remove `biometricEnabled` state and `setBiometricEnabled()`. Remove `autoLockMinutes` state. |
| `SettingsRepository.kt` | Keep interface methods for backward compat but they are no longer read for lock decisions. |

### iOS

| File | Change |
|------|--------|
| `SsdidWalletApp.swift` | Set `isLocked = true` as initial `@State`. Remove elapsed-time check in `onChange(of: scenePhase)` — always lock on `.active` after `.background`. |
| `LockOverlay` (in SsdidWalletApp) | Add device-passcode fallback. Show one-time warning if no biometric. |
| `BiometricSetupScreen.swift` | Remove "Skip for now" button. |
| `SettingsScreen.swift` | Remove biometric toggle row. Remove "Auto-Lock" row. |

### Unchanged

- `BiometricAuthenticator` on both platforms — already supports biometric + device credential
- Transaction signing — still uses `authenticateWithCrypto()` for hardware-backed keys
- Vault encryption — keys remain Keystore/Secure Enclave protected

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

1. **Biometric hardware available:** Shows "Enable Biometric Authentication" button. User must tap and successfully authenticate to proceed.
2. **No biometric hardware but device passcode set:** Shows "Enable Device Passcode" button. User authenticates with PIN/passcode. One-time warning shown about biometric being recommended.
3. **No passcode set at all:** Shows message: "Please set up a device passcode in your phone's Settings to continue. SSDID Wallet requires authentication to protect your identity." with "Open Settings" button that deep-links to device security settings. No way to proceed until a passcode is configured.

No "Skip" or "Later" option exists.

## Out of Scope

- App-specific PIN/password (relies on device credentials)
- "Change Password" implementation (remains a stub)
- Per-screen authentication (e.g., requiring re-auth for specific sensitive actions beyond tx signing)
- Remote lock/wipe
