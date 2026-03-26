# Mandatory Biometric Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make biometric authentication mandatory on every app open — no opt-out, no timeout, no skip during onboarding.

**Architecture:** Modify existing lock/biometric infrastructure on both platforms. Change initial lock state to `true`, remove timeout logic, remove Settings toggle, remove onboarding skip, add 3-state biometric detection with passcode fallback and one-time warning. Data migration forces `biometric_enabled=true` for existing users.

**Tech Stack:** Kotlin/Compose (Android), Swift/SwiftUI (iOS), AndroidX Biometric, LocalAuthentication

**Spec:** `docs/superpowers/specs/2026-03-27-mandatory-biometric-lock-design.md`

---

## File Structure

```
Android (MODIFY only):
  MainActivity.kt                     — cold start lock, remove timeout
  ui/components/LockScreen.kt         — 3-state detection, passcode fallback, warning
  feature/onboarding/BiometricSetupScreen.kt — remove Skip button, add states
  feature/settings/SettingsScreen.kt   — remove toggle + auto-lock rows
  feature/settings/SettingsViewModel.kt — deprecate biometric/autolock state
  platform/storage/DataStoreSettingsRepository.kt — migration

iOS (MODIFY only):
  App/SsdidWalletApp.swift             — cold start lock, remove timeout
  Feature/Onboarding/BiometricSetupScreen.swift — remove Skip, add states
  Feature/Settings/SettingsScreen.swift — remove toggle + auto-lock rows
  Platform/Biometric/BiometricAuthenticator.swift — add 3-state detection helper
```

---

## Task 1: Android — Cold Start Lock + Remove Timeout

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/MainActivity.kt`

- [ ] **Step 1: Change initial lock state to true**

At line 44, change:
```kotlin
private val isLocked = mutableStateOf(false)
```
to:
```kotlin
private val isLocked = mutableStateOf(true)
```

- [ ] **Step 2: Remove elapsed-time check in onResume**

Replace the onResume logic (lines 108-120) which checks `biometricEnabled`, `autoLockMinutes`, and elapsed time with:
```kotlin
override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
        isLocked.value = true
    }
}
```

Remove the `backgroundTimestamp` field (line 45) and the `onPause` timestamp capture if it exists.

- [ ] **Step 3: Verify compilation**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/MainActivity.kt
git commit -m "feat(android): mandatory lock on cold start and every foreground resume"
```

---

## Task 2: Android — LockScreen 3-State Detection + Passcode Fallback

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/components/LockScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/biometric/BiometricAuthenticator.kt`

- [ ] **Step 1: Add biometric state enum to BiometricAuthenticator**

Add after the existing `BiometricResult` sealed class:
```kotlin
enum class BiometricState {
    AVAILABLE,       // Biometric enrolled and ready
    NOT_ENROLLED,    // Hardware present, no biometric enrolled
    NO_HARDWARE      // No biometric hardware at all
}

fun getBiometricState(activity: FragmentActivity): BiometricState {
    val biometricManager = BiometricManager.from(activity)
    return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricState.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricState.NOT_ENROLLED
        else -> BiometricState.NO_HARDWARE
    }
}
```

- [ ] **Step 2: Add authenticate with fallback method**

Add a method that uses `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`:
```kotlin
suspend fun authenticateWithFallback(
    activity: FragmentActivity,
    title: String = "SSDID Authentication",
    subtitle: String = "Verify your identity to continue"
): BiometricResult {
    // Same as authenticate() but with BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    // ... same continuation pattern as authenticate()
}
```

- [ ] **Step 3: Update LockScreen to detect state and show warning**

Rewrite `LockScreen.kt` to:
1. Detect biometric state using `getBiometricState()`
2. If `AVAILABLE` — use biometric auth (existing behavior)
3. If `NOT_ENROLLED` or `NO_HARDWARE` — use `authenticateWithFallback()` (passcode)
4. Show one-time warning banner for `NO_HARDWARE` state
5. Show enrollment suggestion for `NOT_ENROLLED` state

```kotlin
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val biometricAuth = remember { BiometricAuthenticator() }
    val biometricState = remember { biometricAuth.getBiometricState(activity) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ssdid_settings", Context.MODE_PRIVATE) }
    var showWarning by remember { mutableStateOf(false) }
    var authAttempt by remember { mutableIntStateOf(0) }

    // Show one-time warning for no-hardware state
    LaunchedEffect(biometricState) {
        if (biometricState == BiometricAuthenticator.BiometricState.NO_HARDWARE) {
            val shown = prefs.getBoolean("ssdid_biometric_warning_shown", false)
            if (!shown) {
                showWarning = true
                prefs.edit().putBoolean("ssdid_biometric_warning_shown", true).apply()
            }
        }
    }

    // Authenticate on mount and on retry
    LaunchedEffect(authAttempt) {
        val result = when (biometricState) {
            BiometricAuthenticator.BiometricState.AVAILABLE ->
                biometricAuth.authenticate(activity)
            else ->
                biometricAuth.authenticateWithFallback(activity)
        }
        if (result is BiometricAuthenticator.BiometricResult.Success) {
            onUnlock()
        }
    }

    // UI: lock icon + retry button + optional warning banner
    Column(
        modifier = Modifier.fillMaxSize().background(BgPrimary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ... lock icon, app name, etc.

        if (showWarning) {
            Card(/* warning styling */) {
                Text("Your device doesn't support biometric authentication. Device passcode is being used instead. For stronger security, use a device with Face ID or fingerprint.")
            }
        }

        if (biometricState == BiometricAuthenticator.BiometricState.NOT_ENROLLED) {
            Text("Biometric not enrolled. Please set up fingerprint or face unlock in your device Settings.",
                style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }

        Button(onClick = { authAttempt++ }) {
            Text("Unlock")
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/ui/components/LockScreen.kt \
        android/app/src/main/java/my/ssdid/wallet/platform/biometric/BiometricAuthenticator.kt
git commit -m "feat(android): 3-state biometric detection with passcode fallback and warning"
```

---

## Task 3: Android — Remove Settings Toggle + Auto-Lock + Deprecate ViewModel

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsViewModel.kt`

- [ ] **Step 1: Remove biometric toggle and auto-lock from SettingsScreen**

In `SettingsScreen.kt`, remove the biometric toggle item (lines 62-68) and the auto-lock item (line 70). Keep the surrounding section structure intact.

- [ ] **Step 2: Deprecate ViewModel properties**

In `SettingsViewModel.kt`, add `@Deprecated` annotations:
```kotlin
@Deprecated("Biometric is now mandatory — always on")
val biometricEnabled = settings.biometricEnabled()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

@Deprecated("Auto-lock timeout removed — locks on every resume")
val autoLockMinutes = settings.autoLockMinutes()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

@Deprecated("Biometric is now mandatory")
fun setBiometricEnabled(enabled: Boolean) { /* no-op */ }

@Deprecated("Auto-lock timeout removed")
fun setAutoLockMinutes(minutes: Int) { /* no-op */ }
```

- [ ] **Step 3: Verify compilation**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt \
        android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsViewModel.kt
git commit -m "feat(android): remove biometric toggle and auto-lock from Settings"
```

---

## Task 4: Android — Mandatory Onboarding + Data Migration

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/onboarding/BiometricSetupScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreSettingsRepository.kt`

- [ ] **Step 1: Remove Skip button from BiometricSetupScreen**

Remove the "Skip for now" button (lines 132-140). Replace with 3-state behavior:
- If `AVAILABLE`: Show "Enable Biometric Authentication" button (existing)
- If `NOT_ENROLLED`: Show enrollment guidance text + "Use Device Passcode for Now" button
- If `NO_HARDWARE` with device passcode: Show "Enable Device Passcode" button + warning
- If no passcode at all: Show "Please set up a device passcode" message + "Open Settings" button using `Settings.ACTION_SECURITY_SETTINGS`

The screen cannot proceed without successful authentication.

- [ ] **Step 2: Add data migration in DataStoreSettingsRepository**

In the init block or a migration function:
```kotlin
// Force biometric_enabled = true for existing users who disabled it
init {
    runBlocking {
        val currentValue = context.settingsStore.data.first()[biometricKey]
        if (currentValue == false) {
            context.settingsStore.edit { it[biometricKey] = true }
        }
    }
}
```

- [ ] **Step 3: Verify compilation and run tests**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.settings.*" 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/onboarding/BiometricSetupScreen.kt \
        android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreSettingsRepository.kt
git commit -m "feat(android): mandatory onboarding biometric + data migration for existing users"
```

---

## Task 5: iOS — Cold Start Lock + Remove Timeout

**Files:**
- Modify: `ios/SsdidWallet/App/SsdidWalletApp.swift`

- [ ] **Step 1: Change initial lock state to true**

At line 95, change:
```swift
@State private var isLocked = false
```
to:
```swift
@State private var isLocked = true
```

- [ ] **Step 2: Remove elapsed-time check in scenePhase handler**

Replace the `.active` phase handler (lines 156-163) with:
```swift
case .active:
    if backgroundTimestamp != nil {
        isLocked = true
        backgroundTimestamp = nil
    }
```

Remove all references to `biometricEnabled`, `autoLockMinutes`, `effectiveMinutes`, and `elapsed` from the scene phase handler.

- [ ] **Step 3: Update LockOverlay to use passcode fallback**

In the `LockOverlay` view (lines 193-245), change the authentication call from:
```swift
biometricAuth.authenticate()
```
to:
```swift
biometricAuth.authenticateWithPasscodeFallback()
```

This ensures device passcode works as fallback on all devices.

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWallet/App/SsdidWalletApp.swift
git commit -m "feat(ios): mandatory lock on cold start and every foreground resume"
```

---

## Task 6: iOS — BiometricAuthenticator 3-State Detection

**Files:**
- Modify: `ios/SsdidWallet/Platform/Biometric/BiometricAuthenticator.swift`

- [ ] **Step 1: Add BiometricState enum and detection method**

```swift
enum BiometricState {
    case available       // Biometric enrolled and ready
    case notEnrolled     // Hardware present, no biometric enrolled
    case noHardware      // No biometric hardware
}

func getBiometricState() -> BiometricState {
    var error: NSError?
    let canEvaluate = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    if canEvaluate { return .available }
    guard let laError = error as? LAError else { return .noHardware }
    switch laError.code {
    case .biometryNotEnrolled: return .notEnrolled
    case .biometryNotAvailable: return .noHardware
    default: return .noHardware
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ios/SsdidWallet/Platform/Biometric/BiometricAuthenticator.swift
git commit -m "feat(ios): add 3-state biometric detection (available/notEnrolled/noHardware)"
```

---

## Task 7: iOS — Remove Settings Toggle + Auto-Lock

**Files:**
- Modify: `ios/SsdidWallet/Feature/Settings/SettingsScreen.swift`

- [ ] **Step 1: Remove @AppStorage properties and UI rows**

Remove these `@AppStorage` properties (lines 6-7):
```swift
@AppStorage("ssdid_biometric_enabled") private var biometricEnabled = true
@AppStorage("ssdid_auto_lock_minutes") private var autoLockMinutes = 5
```

Remove the biometric toggle row (line 54) and auto-lock row (line 55). Keep surrounding section structure.

- [ ] **Step 2: Commit**

```bash
git add ios/SsdidWallet/Feature/Settings/SettingsScreen.swift
git commit -m "feat(ios): remove biometric toggle and auto-lock from Settings"
```

---

## Task 8: iOS — Mandatory Onboarding + Data Migration

**Files:**
- Modify: `ios/SsdidWallet/Feature/Onboarding/BiometricSetupScreen.swift`
- Modify: `ios/SsdidWallet/Domain/Settings/SettingsRepository.swift` (or UserDefaultsSettingsRepository)

- [ ] **Step 1: Remove Skip button from BiometricSetupScreen**

Remove the "Skip for now" button (lines 61-70). Add 3-state detection using `BiometricAuthenticator().getBiometricState()`:
- `available`: Show "Enable Biometric Authentication" (existing)
- `notEnrolled`: Show enrollment guidance + "Use Device Passcode for Now" button
- `noHardware` with passcode: Show "Enable Device Passcode" button + one-time warning
- No passcode: Show message "Go to Settings > Face ID & Passcode to set up a device passcode" (iOS cannot deep-link to passcode settings)

- [ ] **Step 2: Add data migration**

In `UserDefaultsSettingsRepository` init or a `registerDefaults` call:
```swift
// Force biometric_enabled = true for existing users
if UserDefaults.standard.object(forKey: "ssdid_biometric_enabled") != nil {
    UserDefaults.standard.set(true, forKey: "ssdid_biometric_enabled")
}
```

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWallet/Feature/Onboarding/BiometricSetupScreen.swift \
        ios/SsdidWallet/Domain/Settings/SettingsRepository.swift
git commit -m "feat(ios): mandatory onboarding biometric + data migration for existing users"
```

---

## Task 9: Full Build + Test

- [ ] **Step 1: Android build + tests**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
cd android && ./gradlew :app:testDebugUnitTest 2>&1 | grep -E "tests completed|BUILD"
```

Expected: BUILD SUCCESSFUL, no new test failures.

- [ ] **Step 2: iOS build**

```bash
xcodebuild -project ios/SsdidWallet.xcodeproj -scheme SsdidWallet -destination 'platform=iOS Simulator,id=81450DA8-4C56-4DCA-8A11-A5B9874B8C29' build 2>&1 | tail -5
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Fix any issues and commit**

```bash
git commit -m "fix: address build issues from mandatory biometric lock"
```
