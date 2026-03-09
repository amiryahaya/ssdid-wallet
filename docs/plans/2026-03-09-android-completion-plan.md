# SSDID Wallet Android Completion — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete the Android wallet to production readiness across 4 phases (14 tasks).

**Architecture:** Jetpack Compose MVVM with Hilt DI. Domain layer (Vault, SsdidClient, crypto providers) is well-tested. Most work is wiring UI to domain, adding missing domain logic, and expanding test coverage. All code under `my.ssdid.wallet` package.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Hilt 2.53.1, Retrofit 2.11, BouncyCastle 1.80, DataStore Preferences, CameraX, kotlinx-serialization, JUnit 4 + Mockk + Truth + Robolectric 4.14.1

**Reference docs:**
- Design: `docs/plans/2026-03-09-android-completion-design.md`
- App design: `docs/plans/2026-03-06-ssdid-wallet-app-design.md`
- Gap analysis: `docs/12.SSDID-Gap-Analysis-And-Remediation.md`
- System flows: `docs/SSDID-System-Flows.md`

**Build/test from `android/` directory:**
```bash
./gradlew :app:compileDebugKotlin                    # compile
./gradlew :app:testDebugUnitTest                     # all tests
./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.SomeTest"  # single class
```

---

## Phase 1: Critical UX & Security Fixes

---

### Task 1: Onboarding Bypass

Skip onboarding if user already has identities. Currently `NavGraph.kt:29` hardcodes `startDestination = Screen.Onboarding.route`.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/onboarding/OnboardingScreen.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/platform/storage/OnboardingStateTest.kt`

**Step 1: Add interface method to VaultStorage**

In `VaultStorage.kt`, add after the rotation history methods:

```kotlin
// Onboarding state
suspend fun isOnboardingCompleted(): Boolean
suspend fun setOnboardingCompleted()
```

**Step 2: Implement in DataStoreVaultStorage**

In `DataStoreVaultStorage.kt`, add a new preference key and methods:

```kotlin
private val onboardingCompletedKey = stringPreferencesKey("onboarding_completed")

// Add to class body:
override suspend fun isOnboardingCompleted(): Boolean {
    return context.dataStore.data.map { it[onboardingCompletedKey] }.first() == "true"
}

override suspend fun setOnboardingCompleted() {
    context.dataStore.edit { prefs ->
        prefs[onboardingCompletedKey] = "true"
    }
}
```

**Step 3: Update FakeVaultStorage for tests**

In `android/app/src/test/java/my/ssdid/wallet/domain/vault/FakeVaultStorage.kt`, add:

```kotlin
private var onboardingCompleted = false

override suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted
override suspend fun setOnboardingCompleted() { onboardingCompleted = true }
```

**Step 4: Write test**

Create `android/app/src/test/java/my/ssdid/wallet/platform/storage/OnboardingStateTest.kt`:

```kotlin
package my.ssdid.wallet.platform.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.domain.vault.FakeVaultStorage
import org.junit.Test

class OnboardingStateTest {
    private val storage = FakeVaultStorage()

    @Test
    fun `onboarding not completed by default`() = runBlocking {
        assertThat(storage.isOnboardingCompleted()).isFalse()
    }

    @Test
    fun `onboarding completed after setting`() = runBlocking {
        storage.setOnboardingCompleted()
        assertThat(storage.isOnboardingCompleted()).isTrue()
    }
}
```

**Step 5: Run test**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.platform.storage.OnboardingStateTest"
```

Expected: PASS

**Step 6: Update NavGraph to use dynamic start destination**

In `NavGraph.kt`, the function signature and body need to change:

```kotlin
@Composable
fun SsdidNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        // ... all composable routes stay the same
    }
}
```

Then in `MainActivity.kt`, determine start destination:

```kotlin
// In setContent block, before calling SsdidNavGraph:
val storage = (application as SsdidApp).vaultStorage // or inject via Hilt
var startDest by remember { mutableStateOf<String?>(null) }
LaunchedEffect(Unit) {
    startDest = if (storage.isOnboardingCompleted()) {
        Screen.WalletHome.route
    } else {
        Screen.Onboarding.route
    }
}
if (startDest != null) {
    SsdidNavGraph(navController = navController, startDestination = startDest!!)
}
```

Note: The exact integration depends on how `MainActivity` is structured. Read `MainActivity.kt` before implementing to understand the Hilt injection pattern used. You may need to inject `VaultStorage` via `@AndroidEntryPoint` and `@Inject`.

**Step 7: Set onboarding flag on first identity creation**

In `CreateIdentityScreen.kt` (or its ViewModel), after successful identity creation, call:

```kotlin
storage.setOnboardingCompleted()
```

This should happen in the `onCreated` success path.

**Step 8: Run all tests to verify no regressions**

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

**Step 9: Commit**

```bash
git add -A && git commit -m "feat(android): skip onboarding for returning users"
```

---

### Task 2: Wire Biometric Gates

Currently `TxSigningScreen`, `AuthFlowScreen`, and `BackupScreen` show biometric UI but never call `BiometricAuthenticator.authenticate()`.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/transaction/TxSigningScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt`

**Context:** `BiometricAuthenticator` is already provided by Hilt in `AppModule.kt`. It has:
- `canAuthenticate(): Boolean` — checks BIOMETRIC_STRONG
- `authenticate(activity, title, subtitle): BiometricResult` — suspend function
- `BiometricResult.Success`, `BiometricResult.Error(code, message)`, `BiometricResult.Cancelled`

**Step 1: Add biometric gate to TxSigningViewModel**

In `TxSigningScreen.kt`, the `TxSigningViewModel` class needs `BiometricAuthenticator` injected. Currently it injects `SsdidClient` and `Vault`. Add:

```kotlin
@HiltViewModel
class TxSigningViewModel @Inject constructor(
    private val ssdidClient: SsdidClient,
    private val vault: Vault,
    private val biometricAuth: BiometricAuthenticator,  // ADD THIS
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

Add a biometric check method:

```kotlin
suspend fun requireBiometric(activity: FragmentActivity): Boolean {
    if (!biometricAuth.canAuthenticate()) return true  // no biometric hardware, allow
    return when (biometricAuth.authenticate(activity, "Confirm Transaction", "Authenticate to sign")) {
        is BiometricResult.Success -> true
        else -> false
    }
}
```

In `confirmTransaction()`, call `requireBiometric()` before signing. The Screen composable needs to get the activity context:

```kotlin
val activity = LocalContext.current as FragmentActivity
```

Then gate the sign action:

```kotlin
Button(onClick = {
    scope.launch {
        if (viewModel.requireBiometric(activity)) {
            viewModel.confirmTransaction()
        }
    }
})
```

**Step 2: Add biometric gate to AuthFlowViewModel**

Same pattern in `AuthFlowScreen.kt`:

```kotlin
@HiltViewModel
class AuthFlowViewModel @Inject constructor(
    private val ssdidClient: SsdidClient,
    private val vault: Vault,
    private val biometricAuth: BiometricAuthenticator,  // ADD THIS
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

Gate the authenticate button similarly.

**Step 3: Add biometric gate to BackupViewModel**

In `BackupScreen.kt`, gate both `createBackup()` and `restoreBackup()` behind biometric.

**Step 4: Compile and verify**

```bash
cd android && ./gradlew :app:compileDebugKotlin
```

**Step 5: Run existing tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Note: ViewModel tests that mock these ViewModels may need updating if constructor signatures changed.

**Step 6: Commit**

```bash
git add -A && git commit -m "feat(android): wire biometric authentication gates"
```

---

### Task 3: Settings Persistence

Settings toggles are local `remember` state. Create a `SettingsRepository` backed by DataStore.

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/settings/SettingsRepository.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreSettingsRepository.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/settings/SettingsRepositoryTest.kt`

**Step 1: Create SettingsRepository interface**

```kotlin
package my.ssdid.wallet.domain.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun biometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun autoLockMinutes(): Flow<Int>
    suspend fun setAutoLockMinutes(minutes: Int)
    fun defaultAlgorithm(): Flow<String>
    suspend fun setDefaultAlgorithm(algorithm: String)
    fun language(): Flow<String>
    suspend fun setLanguage(language: String)
}
```

**Step 2: Create DataStoreSettingsRepository**

```kotlin
package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.ssdid.wallet.domain.settings.SettingsRepository

private val Context.settingsStore by preferencesDataStore(name = "ssdid_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private val biometricKey = booleanPreferencesKey("biometric_enabled")
    private val autoLockKey = intPreferencesKey("auto_lock_minutes")
    private val algorithmKey = stringPreferencesKey("default_algorithm")
    private val languageKey = stringPreferencesKey("language")

    override fun biometricEnabled(): Flow<Boolean> =
        context.settingsStore.data.map { it[biometricKey] ?: true }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[biometricKey] = enabled }
    }

    override fun autoLockMinutes(): Flow<Int> =
        context.settingsStore.data.map { it[autoLockKey] ?: 5 }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        context.settingsStore.edit { it[autoLockKey] = minutes }
    }

    override fun defaultAlgorithm(): Flow<String> =
        context.settingsStore.data.map { it[algorithmKey] ?: "KAZ_SIGN_192" }

    override suspend fun setDefaultAlgorithm(algorithm: String) {
        context.settingsStore.edit { it[algorithmKey] = algorithm }
    }

    override fun language(): Flow<String> =
        context.settingsStore.data.map { it[languageKey] ?: "en" }

    override suspend fun setLanguage(language: String) {
        context.settingsStore.edit { it[languageKey] = language }
    }
}
```

**Step 3: Create SettingsViewModel**

```kotlin
package my.ssdid.wallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    val biometricEnabled = settings.biometricEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoLockMinutes = settings.autoLockMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val defaultAlgorithm = settings.defaultAlgorithm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "KAZ_SIGN_192")

    val language = settings.language()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { settings.setAutoLockMinutes(minutes) }
    }

    fun setDefaultAlgorithm(algorithm: String) {
        viewModelScope.launch { settings.setDefaultAlgorithm(algorithm) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }
}
```

**Step 4: Provide in AppModule**

Add to `AppModule.kt`:

```kotlin
@Provides
@Singleton
fun provideSettingsRepository(
    @ApplicationContext context: Context
): SettingsRepository = DataStoreSettingsRepository(context)
```

**Step 5: Update SettingsScreen to use ViewModel**

Replace local `remember` state with ViewModel's StateFlows. Add `@HiltViewModel` and `hiltViewModel()`. The screen should `collectAsState()` from the ViewModel flows and call ViewModel methods on toggle changes.

**Step 6: Write test**

Create test with a fake in-memory `SettingsRepository` to verify defaults, persistence of changes, and flow emissions.

**Step 7: Run tests and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): persist settings with DataStore"
```

---

### Task 4: Wire Activity Logging

`ActivityRepository` exists but SsdidClient doesn't call `addActivity()`.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/rotation/KeyRotationManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/backup/BackupManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/SsdidClientActivityTest.kt`

**Step 1: Add ActivityRepository to SsdidClient constructor**

```kotlin
class SsdidClient(
    private val vault: Vault,
    private val verifier: Verifier,
    private val httpClient: SsdidHttpClient,
    private val activityRepo: ActivityRepository  // ADD
) {
```

**Step 2: Log in each flow**

Create a helper in SsdidClient:

```kotlin
private suspend fun logActivity(
    type: ActivityType,
    did: String,
    serviceUrl: String? = null,
    details: Map<String, String> = emptyMap()
) {
    activityRepo.addActivity(
        ActivityRecord(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            did = did,
            serviceUrl = serviceUrl,
            timestamp = java.time.Instant.now().toString(),
            status = ActivityStatus.SUCCESS,
            details = details
        )
    )
}
```

Add calls after each successful flow:
- `initIdentity`: `logActivity(IDENTITY_CREATED, identity.did, details = mapOf("algorithm" to algorithm.name))`
- `registerWithService`: `logActivity(SERVICE_REGISTERED, identity.did, serverUrl)` + `logActivity(CREDENTIAL_RECEIVED, identity.did, serverUrl)`
- `authenticate`: `logActivity(AUTHENTICATED, credential.credentialSubject["did"] ?: "", serverUrl)`
- `signTransaction`: `logActivity(TX_SIGNED, identity.did, serverUrl)`

**Step 3: Update AppModule**

```kotlin
@Provides
@Singleton
fun provideSsdidClient(
    vault: Vault,
    verifier: Verifier,
    httpClient: SsdidHttpClient,
    activityRepo: ActivityRepository  // ADD
): SsdidClient = SsdidClient(vault, verifier, httpClient, activityRepo)
```

Also add the ActivityRepository provider if not already present:

```kotlin
@Provides
@Singleton
fun provideActivityRepository(
    @ApplicationContext context: Context
): ActivityRepository = ActivityRepositoryImpl(context)
```

**Step 4: Wire logging in KeyRotationManager and BackupManager**

Add `ActivityRepository` to their constructors and log `KEY_ROTATED` / `BACKUP_CREATED` after successful operations. Update `AppModule` providers accordingly.

**Step 5: Write test**

Test that after calling `initIdentity()` on a mocked SsdidClient, the activity repo contains an `IDENTITY_CREATED` record.

**Step 6: Run tests and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): wire activity logging into all flows"
```

---

## Phase 2: Incomplete Features

---

### Task 5: Localize Onboarding Strings

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/onboarding/OnboardingScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ms/strings.xml`
- Modify: `android/app/src/main/res/values-zh/strings.xml`

**Step 1: Add string resources**

In `res/values/strings.xml`:

```xml
<string name="onboarding_title_1">Your Identity, Your Control</string>
<string name="onboarding_desc_1">Own your digital identity with self-sovereign technology. No central authority holds your keys.</string>
<string name="onboarding_title_2">Post-Quantum Security</string>
<string name="onboarding_desc_2">Protected by KAZ-Sign post-quantum cryptography, future-proofing your identity against quantum threats.</string>
<string name="onboarding_title_3">Seamless Verification</string>
<string name="onboarding_desc_3">Authenticate and sign transactions with a simple scan. Your device, your keys, your proof.</string>
<string name="onboarding_next">Next</string>
<string name="onboarding_get_started">Get Started</string>
```

Add translations in `values-ms/strings.xml` and `values-zh/strings.xml`.

**Step 2: Replace hardcoded strings in OnboardingScreen.kt**

Replace the hardcoded slide data with `stringResource(R.string.onboarding_title_1)` etc. The slides list should be constructed inside the Composable:

```kotlin
val slides = listOf(
    Triple(stringResource(R.string.onboarding_title_1), stringResource(R.string.onboarding_desc_1), Accent),
    Triple(stringResource(R.string.onboarding_title_2), stringResource(R.string.onboarding_desc_2), Pqc),
    Triple(stringResource(R.string.onboarding_title_3), stringResource(R.string.onboarding_desc_3), Success),
)
```

**Step 3: Compile and commit**

```bash
cd android && ./gradlew :app:compileDebugKotlin
git add -A && git commit -m "fix(android): localize onboarding screen strings"
```

---

### Task 6: Backup File I/O

Add SAF file picker for saving and loading backup files.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt`

**Step 1: Add activity result launchers**

In `BackupScreen` composable, add launchers:

```kotlin
val context = LocalContext.current

// For saving backup
val saveBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
) { uri ->
    uri?.let {
        val bytes = viewModel.lastBackupBytes ?: return@let
        context.contentResolver.openOutputStream(it)?.use { out ->
            out.write(bytes)
        }
        viewModel.onBackupSaved()
    }
}

// For loading backup
val loadBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let {
        val bytes = context.contentResolver.openInputStream(it)?.use { input ->
            input.readBytes()
        } ?: return@let
        viewModel.onBackupFileLoaded(bytes)
    }
}
```

**Step 2: Update BackupViewModel**

Add state for loaded file bytes and wire save flow:

```kotlin
var lastBackupBytes: ByteArray? = null
    private set

private val _loadedFileBytes = MutableStateFlow<ByteArray?>(null)

fun createBackup(passphrase: String) {
    viewModelScope.launch {
        _state.value = BackupState.Creating
        backupManager.createBackup(passphrase)
            .onSuccess { bytes ->
                lastBackupBytes = bytes
                _state.value = BackupState.Success(bytes)
            }
            .onFailure { _state.value = BackupState.Error(it.message ?: "Backup failed") }
    }
}

fun onBackupSaved() {
    lastBackupBytes = null
    // optionally update state to show "Saved successfully"
}

fun onBackupFileLoaded(bytes: ByteArray) {
    _loadedFileBytes.value = bytes
    // transition to passphrase input for restore
}

fun restoreBackup(passphrase: String) {
    val bytes = _loadedFileBytes.value ?: return
    viewModelScope.launch {
        _state.value = BackupState.Restoring
        backupManager.restoreBackup(bytes, passphrase)
            .onSuccess { count -> _state.value = BackupState.RestoreSuccess(count) }
            .onFailure { _state.value = BackupState.Error(it.message ?: "Restore failed") }
    }
}
```

**Step 3: Wire buttons**

- On backup success: show "Save to File" button that calls `saveBackupLauncher.launch("ssdid-backup-${date}.enc")`
- On "Import Backup" button: call `loadBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))`

**Step 4: Compile and commit**

```bash
cd android && ./gradlew :app:compileDebugKotlin
git add -A && git commit -m "feat(android): add file picker for backup save/restore"
```

---

### Task 7: DID Update & Deactivation

Expose unused Registry API endpoints. Required by Tasks 8 and 9.

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/RegistryDtos.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/transport/RegistryApi.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/IdentityDetailScreen.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/transport/dto/RegistryDtosTest.kt`

**Step 1: Create RegistryDtos**

```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.Proof

@Serializable
data class RegisterDidRequest(val document: DidDocument, val proof: Proof)

@Serializable
data class UpdateDidRequest(val document: DidDocument, val proof: Proof)

@Serializable
data class DeactivateDidRequest(val proof: Proof)

@Serializable
data class ResolveDidResponse(val document: DidDocument, val status: String)

@Serializable
data class ChallengeResponse(val challenge: String)
```

Note: `RegisterDidRequest` already exists in `ServerDtos.kt` — check if it's there or in a separate file. If it exists, just add the missing DTOs.

**Step 2: Update RegistryApi if needed**

Verify `RegistryApi.kt` has proper request/response types. Current endpoints:
- `updateDid(@Path did, @Body request: UpdateDidRequest)` — verify it accepts `UpdateDidRequest`
- `deactivateDid(@Path did, @Body request: DeactivateDidRequest)` — may need body added

**Step 3: Add methods to SsdidClient**

```kotlin
/** Update DID Document on Registry (used by rotation and recovery) */
suspend fun updateDidDocument(keyId: String): Result<Unit> = runCatching {
    val identity = vault.getIdentity(keyId).getOrThrow()
        ?: throw IllegalArgumentException("Identity not found: $keyId")
    val didDoc = vault.buildDidDocument(keyId).getOrThrow()
    val didDocJson = wireJson.encodeToString(didDoc)
    val didDocJsonObject = wireJson.parseToJsonElement(didDocJson).jsonObject
    val proof = vault.createProof(keyId, didDocJsonObject, "capabilityInvocation").getOrThrow()
    httpClient.registry.updateDid(identity.did, UpdateDidRequest(didDoc, proof))
}

/** Deactivate DID — irreversible */
suspend fun deactivateDid(keyId: String): Result<Unit> = runCatching {
    val identity = vault.getIdentity(keyId).getOrThrow()
        ?: throw IllegalArgumentException("Identity not found: $keyId")
    val deactivateData = wireJson.parseToJsonElement(
        """{"id":"${identity.did}","deactivated":true}"""
    ).jsonObject
    val proof = vault.createProof(keyId, deactivateData, "capabilityInvocation").getOrThrow()
    httpClient.registry.deactivateDid(identity.did, DeactivateDidRequest(proof))
    vault.deleteIdentity(keyId).getOrThrow()
    logActivity(ActivityType.IDENTITY_CREATED, identity.did, details = mapOf("action" to "deactivated"))
}
```

**Step 4: Add deactivation UI to IdentityDetailScreen**

Add a "Deactivate DID" button with a confirmation dialog:

```kotlin
var showDeactivateDialog by remember { mutableStateOf(false) }

// Button at bottom of screen
Button(
    onClick = { showDeactivateDialog = true },
    colors = ButtonDefaults.buttonColors(containerColor = Danger)
) { Text("Deactivate Identity") }

if (showDeactivateDialog) {
    AlertDialog(
        onDismissRequest = { showDeactivateDialog = false },
        title = { Text("Deactivate Identity?") },
        text = { Text("This is irreversible. Your DID will be permanently deactivated on the registry.") },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.deactivateIdentity()
                    showDeactivateDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Danger)
            ) { Text("Deactivate") }
        },
        dismissButton = {
            TextButton(onClick = { showDeactivateDialog = false }) { Text("Cancel") }
        }
    )
}
```

**Step 5: Write DTO serialization test and compile**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): add DID update and deactivation support"
```

---

### Task 8: Key Rotation → Registry Sync

After local rotation, publish updated DID Document to Registry.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/rotation/KeyRotationManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/rotation/KeyRotationRegistrySyncTest.kt`

**Step 1: Add SsdidClient to KeyRotationManager**

```kotlin
class KeyRotationManager(
    private val storage: VaultStorage,
    private val classical: CryptoProvider,
    private val pqc: CryptoProvider,
    private val keystoreManager: KeystoreManager,
    private val ssdidClient: SsdidClient  // ADD
) {
```

Update `AppModule.kt` provider.

**Step 2: Publish pre-commitment hash**

In `prepareRotation()`, after storing the pre-rotated key locally, update the DID Document with `nextKeyHash`:

```kotlin
// After storing pre-rotated key:
ssdidClient.updateDidDocument(keyId).getOrThrow()
```

This requires `VaultImpl.buildDidDocument()` to include `nextKeyHash` from the identity's metadata. In `VaultImpl.kt`, update `buildDidDocument()`:

```kotlin
override suspend fun buildDidDocument(keyId: String): Result<DidDocument> = runCatching {
    val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found")
    val did = Did.fromValue(identity.did)

    // Check for pre-rotated key hash
    val nextKeyHash = if (identity.preRotatedKeyId != null) {
        val preRotated = storage.getPreRotatedKey(identity.preRotatedKeyId!!)
        if (preRotated != null) {
            val sha3 = java.security.MessageDigest.getInstance("SHA3-256")
            val hash = sha3.digest(preRotated.publicKey)
            my.ssdid.wallet.domain.crypto.Multibase.encode(hash)
        } else null
    } else null

    DidDocument.build(did, identity.keyId, identity.algorithm, identity.publicKeyMultibase)
        .copy(nextKeyHash = nextKeyHash)
}
```

**Step 3: Publish rotation**

In `executeRotation()`, after promoting the new key, call:

```kotlin
ssdidClient.updateDidDocument(newIdentity.keyId).getOrThrow()
```

**Step 4: Write test verifying rotation triggers update**

Mock `SsdidClient.updateDidDocument()` and verify it's called after `executeRotation()`.

**Step 5: Run tests and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): sync key rotation with registry"
```

---

### Task 9: Recovery Restoration Flow

Allow users to restore access on a new device using their offline recovery key.

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/recovery/RecoveryRestoreScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/recovery/RecoveryManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/recovery/RecoveryRestorationTest.kt`

**Step 1: Add restoration method to RecoveryManager**

```kotlin
/**
 * Restore identity using offline recovery private key.
 * 1. Resolve DID from Registry to get recovery public key
 * 2. Verify provided key matches
 * 3. Generate new primary keypair
 * 4. Sign DID Document update with recovery key
 * 5. Publish to Registry
 * 6. Store new identity locally
 */
suspend fun restoreWithRecoveryKey(
    did: String,
    recoveryPrivateKeyBase64: String,
    name: String,
    algorithm: Algorithm
): Result<Identity> = runCatching {
    val recoveryPrivateKey = Base64.getDecoder().decode(recoveryPrivateKeyBase64)

    // Generate new primary keypair
    val provider = if (algorithm.isPostQuantum) pqc else classical
    val kp = provider.generateKeyPair(algorithm)

    // Create new identity
    val keyId = "$did#${java.util.UUID.randomUUID().toString().take(8)}"
    val publicKeyMultibase = Multibase.encode(kp.publicKey)
    val now = java.time.Instant.now().toString()
    val identity = Identity(
        name = name,
        did = did,
        keyId = keyId,
        algorithm = algorithm,
        publicKeyMultibase = publicKeyMultibase,
        createdAt = now
    )

    // Encrypt and store new private key
    val alias = stableAlias(keyId)
    keystoreManager.createWrappingKey(alias)
    val encryptedKey = keystoreManager.encrypt(alias, kp.privateKey)
    kp.privateKey.fill(0)
    storage.saveIdentity(identity, encryptedKey)

    // The caller (SsdidClient) will handle publishing the updated DID Document
    // using the recovery key to sign the update proof

    identity
}
```

**Step 2: Add RecoveryRestore screen route**

In `Screen.kt`:

```kotlin
object RecoveryRestore : Screen("recovery_restore")
```

In `NavGraph.kt`, add composable route.

**Step 3: Create RecoveryRestoreScreen**

A screen with:
- DID input field (the DID to recover)
- Recovery key input field (Base64 private key, pasted from offline backup)
- Name input for the restored identity
- Algorithm selector
- "Restore" button
- Loading/success/error states

**Step 4: Write test for restoration logic**

Test `RecoveryManager.restoreWithRecoveryKey()` with a mock vault/storage.

**Step 5: Link from onboarding**

Add a "Restore from Recovery Key" link on the onboarding/create identity screen for users who are setting up a new device.

**Step 6: Run tests and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): add recovery restoration flow"
```

---

## Phase 3: Missing Features

---

### Task 10: In-App Language Switching

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/i18n/LocalizationManager.kt`
- Create: `android/app/src/main/res/xml/locales_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsViewModel.kt`

**Step 1: Create locales_config.xml**

`android/app/src/main/res/xml/locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ms" />
    <locale android:name="zh" />
</locale-config>
```

**Step 2: Update AndroidManifest.xml**

Add to `<application>` tag:

```xml
android:localeConfig="@xml/locales_config"
```

**Step 3: Create LocalizationManager**

```kotlin
package my.ssdid.wallet.platform.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocalizationManager {
    val supportedLocales = listOf("en", "ms", "zh")
    val localeNames = mapOf("en" to "English", "ms" to "Bahasa Melayu", "zh" to "中文")

    fun setLocale(languageTag: String) {
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getCurrentLocale(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "en" else locales[0]?.language ?: "en"
    }
}
```

**Step 4: Add language picker to SettingsScreen**

Add a dialog with radio buttons for each supported locale. On selection, call `LocalizationManager.setLocale(tag)` and `viewModel.setLanguage(tag)`.

**Step 5: Compile and commit**

```bash
cd android && ./gradlew :app:compileDebugKotlin
git add -A && git commit -m "feat(android): add in-app language switching"
```

---

### Task 11: Credential Issuance Flow

Support standalone credential offers via QR/deep link.

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/credential/CredentialIssuanceManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/IssuerApi.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/CredentialDtos.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/credentials/CredentialOfferScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/scan/QrScanner.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/credential/CredentialIssuanceTest.kt`

**Step 1: Create DTOs**

```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.VerifiableCredential

@Serializable
data class CredentialOfferResponse(
    val offer_id: String,
    val issuer_did: String,
    val credential_type: String,
    val claims: Map<String, String>,
    val expires_at: String? = null
)

@Serializable
data class CredentialAcceptRequest(
    val did: String,
    val key_id: String,
    val signed_acceptance: String
)

@Serializable
data class CredentialAcceptResponse(
    val credential: VerifiableCredential
)
```

**Step 2: Create IssuerApi**

```kotlin
package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.*

interface IssuerApi {
    @GET("credential-offer/{offerId}")
    suspend fun getOffer(@Path("offerId") offerId: String): CredentialOfferResponse

    @POST("credential-offer/{offerId}/accept")
    suspend fun acceptOffer(
        @Path("offerId") offerId: String,
        @Body request: CredentialAcceptRequest
    ): CredentialAcceptResponse
}
```

**Step 3: Create CredentialIssuanceManager**

```kotlin
package my.ssdid.wallet.domain.credential

import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.*
import my.ssdid.wallet.domain.vault.Vault

class CredentialIssuanceManager(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient
) {
    suspend fun fetchOffer(issuerUrl: String, offerId: String): Result<CredentialOfferResponse> = runCatching {
        val api = httpClient.issuerApi(issuerUrl)
        api.getOffer(offerId)
    }

    suspend fun acceptOffer(
        issuerUrl: String,
        offerId: String,
        identity: Identity
    ): Result<my.ssdid.wallet.domain.model.VerifiableCredential> = runCatching {
        val api = httpClient.issuerApi(issuerUrl)
        val acceptance = "accept:$offerId".toByteArray()
        val sig = vault.sign(identity.keyId, acceptance).getOrThrow()
        val resp = api.acceptOffer(offerId, CredentialAcceptRequest(
            did = identity.did,
            key_id = identity.keyId,
            signed_acceptance = Multibase.encode(sig)
        ))
        vault.storeCredential(resp.credential).getOrThrow()
        resp.credential
    }
}
```

**Step 4: Add issuerApi() factory to SsdidHttpClient**

```kotlin
fun issuerApi(baseUrl: String): IssuerApi {
    return buildRetrofit(baseUrl).create(IssuerApi::class.java)
}
```

**Step 5: Add deep link and QR support**

In `DeepLinkHandler.kt`, add `"credential-offer"` to valid actions.
In `QrScanner.kt`, add `"credential-offer"` to valid actions. Extract `offer_id` from payload.

In `Screen.kt`:

```kotlin
object CredentialOffer : Screen("credential_offer?issuerUrl={issuerUrl}&offerId={offerId}") {
    fun createRoute(issuerUrl: String, offerId: String) =
        "credential_offer?issuerUrl=${Uri.encode(issuerUrl)}&offerId=${Uri.encode(offerId)}"
}
```

**Step 6: Create CredentialOfferScreen**

Screen showing:
- Issuer DID
- Credential type
- Claims preview
- Accept/Reject buttons
- Identity selector (which identity to use)
- Loading/success/error states

**Step 7: Wire navigation in NavGraph and ScanQrScreen**

Add `"credential-offer"` action handling in `ScanQrScreen.onScanned` callback.

**Step 8: Write test and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): add standalone credential issuance flow"
```

---

### Task 12: Device Management — Multi-Device Protocol

Largest task. Implement device enrollment, listing, and revocation.

**This task requires both wallet and registry changes.** The registry work is out of scope for this plan — implement wallet-side with the assumption that pairing endpoints will exist on the registry.

**Files (wallet):**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/device/DeviceManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/device/DeviceInfo.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/DeviceDtos.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/device/DeviceEnrollScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/device/DeviceManagementScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/transport/RegistryApi.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/device/DeviceManagerTest.kt`

**Step 1: Create DeviceInfo model**

```kotlin
package my.ssdid.wallet.domain.device

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val platform: String,
    val keyId: String,
    val enrolledAt: String,
    val isPrimary: Boolean
)
```

**Step 2: Create DeviceDtos**

```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairingInitRequest(
    val did: String,
    val challenge: String,
    val primary_key_id: String
)

@Serializable
data class PairingInitResponse(val pairing_id: String)

@Serializable
data class PairingJoinRequest(
    val pairing_id: String,
    val public_key: String,
    val signed_challenge: String,
    val device_name: String,
    val platform: String
)

@Serializable
data class PairingJoinResponse(val status: String)

@Serializable
data class PairingApproveRequest(
    val did: String,
    val key_id: String,
    val signed_approval: String
)

@Serializable
data class PairingStatusResponse(
    val status: String,
    val device_name: String? = null,
    val public_key: String? = null,
    val signed_challenge: String? = null
)
```

**Step 3: Add pairing endpoints to RegistryApi**

```kotlin
@POST("api/did/{did}/pair")
suspend fun initPairing(@Path("did") did: String, @Body request: PairingInitRequest): PairingInitResponse

@POST("api/did/{did}/pair/{pairingId}/join")
suspend fun joinPairing(@Path("did") did: String, @Path("pairingId") pairingId: String, @Body request: PairingJoinRequest): PairingJoinResponse

@GET("api/did/{did}/pair/{pairingId}")
suspend fun getPairingStatus(@Path("did") did: String, @Path("pairingId") pairingId: String): PairingStatusResponse

@POST("api/did/{did}/pair/{pairingId}/approve")
suspend fun approvePairing(@Path("did") did: String, @Path("pairingId") pairingId: String, @Body request: PairingApproveRequest): Unit
```

**Step 4: Create DeviceManager**

Implements:
- `initiatePairing(identity)` — primary device generates challenge, calls `initPairing`, returns QR data
- `joinPairing(did, pairingId, challenge)` — secondary device generates key, signs challenge, calls `joinPairing`
- `approvePairing(identity, pairingId)` — primary device verifies secondary's signature, updates DID Document adding new key to `authentication` only
- `listDevices(did)` — derives device list from DID Document's verification methods
- `revokeDevice(identity, targetKeyId)` — removes key from DID Document

**Step 5: Create DeviceEnrollScreen**

Two modes:
- **Primary mode**: Shows QR code with pairing data, polls for secondary to join
- **Secondary mode**: Scans QR, generates key, signs challenge, waits for approval

**Step 6: Update DeviceManagementScreen**

Replace stub with real device list derived from DID Document. Enable "Enroll New Device" button.

**Step 7: Add navigation routes**

```kotlin
object DeviceEnroll : Screen("device_enroll/{keyId}?mode={mode}") {
    fun createRoute(keyId: String, mode: String) =
        "device_enroll/${Uri.encode(keyId)}?mode=$mode"
}
```

**Step 8: Write tests**

Test `DeviceManager` with mocked Registry API — verify pairing flow produces correct DTOs and DID Document updates.

**Step 9: Compile, test, commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): implement multi-device enrollment protocol"
```

---

## Phase 4: Robustness & Testing

---

### Task 13: Network Error Handling

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/NetworkResult.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/RetryInterceptor.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/transport/SsdidHttpClient.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/transport/RetryInterceptorTest.kt`

**Step 1: Create NetworkResult**

```kotlin
package my.ssdid.wallet.domain.transport

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class ServerError(val code: Int, val message: String) : NetworkResult<Nothing>()
    data class NetworkError(val cause: Throwable) : NetworkResult<Nothing>()
    object Timeout : NetworkResult<Nothing>()
}
```

**Step 2: Create RetryInterceptor**

```kotlin
package my.ssdid.wallet.domain.transport

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500 || attempt == maxRetries) return response
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
            // Exponential backoff
            Thread.sleep((1000L * (attempt + 1)).coerceAtMost(5000))
        }
        throw lastException ?: IOException("Retry exhausted")
    }
}
```

**Step 3: Add to SsdidHttpClient**

In `SsdidHttpClient.kt`, add the interceptor to OkHttp builder:

```kotlin
.addInterceptor(RetryInterceptor(maxRetries = 2))
```

**Step 4: Create wrapper function in SsdidClient**

```kotlin
private suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: java.net.SocketTimeoutException) {
        NetworkResult.Timeout
    } catch (e: retrofit2.HttpException) {
        NetworkResult.ServerError(e.code(), e.message())
    } catch (e: java.io.IOException) {
        NetworkResult.NetworkError(e)
    }
}
```

Optionally convert existing `Result<T>` returns to `NetworkResult<T>` in a follow-up. For now, the retry interceptor handles transparent retries.

**Step 5: Write RetryInterceptor test**

Use MockWebServer to test retry behavior on 500s and IOExceptions.

**Step 6: Run tests and commit**

```bash
cd android && ./gradlew :app:testDebugUnitTest
git add -A && git commit -m "feat(android): add network retry and error handling"
```

---

### Task 14: Test Coverage Expansion

**Files to create (priority order):**

**Tier 1 — Security-critical:**
- `android/app/src/test/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorageTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/domain/crypto/KazSignerTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/domain/settings/SettingsRepositoryTest.kt`

**Tier 2 — Business logic:**
- `android/app/src/test/java/my/ssdid/wallet/domain/device/DeviceManagerTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/domain/credential/CredentialIssuanceTest.kt`

**Tier 3 — ViewModels:**
- `android/app/src/test/java/my/ssdid/wallet/feature/identity/CreateIdentityViewModelTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/feature/auth/AuthFlowViewModelTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/feature/transaction/TxSigningViewModelTest.kt`
- `android/app/src/test/java/my/ssdid/wallet/feature/backup/BackupViewModelTest.kt`

**Step 1: DataStoreVaultStorageTest**

Use Robolectric with `ApplicationProvider.getApplicationContext()`:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreVaultStorageTest {
    private lateinit var storage: DataStoreVaultStorage

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = DataStoreVaultStorage(context)
    }

    @Test
    fun `save and retrieve identity`() = runBlocking {
        val identity = Identity(name = "Test", did = "did:ssdid:test", keyId = "did:ssdid:test#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "uabc", createdAt = "2026-01-01T00:00:00Z")
        storage.saveIdentity(identity, byteArrayOf(1, 2, 3))
        val retrieved = storage.getIdentity("did:ssdid:test#key-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.name).isEqualTo("Test")
    }
    // ... more tests for credentials, rotation history, pre-rotated keys
}
```

**Step 2: KazSignerTest**

Mock `KazSignNative` with Mockk to test `KazSigner` wrapper logic without JNI:

```kotlin
class KazSignerTest {
    @Test
    fun `generateKeyPair returns non-empty result`() {
        // Mock KazSignNative.generateKeyPair to return test bytes
        mockkObject(KazSignNative)
        every { KazSignNative.generateKeyPair(any()) } returns testKeyPairBytes
        // ...
    }
}
```

**Step 3: ViewModel tests**

Use Mockk to mock dependencies, test state transitions:

```kotlin
@Test
fun `createIdentity updates state to success`() = runTest {
    val vault = mockk<Vault>()
    coEvery { vault.createIdentity(any(), any()) } returns Result.success(testIdentity)
    // ... verify state flow emissions
}
```

**Step 4: Run full test suite**

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

**Step 5: Generate coverage report**

```bash
cd android && ./gradlew koverHtmlReportDebug
```

Open `android/app/build/reports/kover/htmlDebug/index.html` to review coverage.

**Step 6: Commit**

```bash
git add -A && git commit -m "test(android): expand test coverage across all layers"
```

---

## Execution Summary

| Task | Phase | Description | Depends On |
|------|-------|-------------|------------|
| 1 | 1 | Onboarding bypass | — |
| 2 | 1 | Biometric gates | — |
| 3 | 1 | Settings persistence | — |
| 4 | 1 | Activity logging | — |
| 5 | 2 | Onboarding strings | — |
| 6 | 2 | Backup file I/O | — |
| 7 | 2 | DID update/deactivation | — |
| 8 | 2 | Key rotation sync | 7 |
| 9 | 2 | Recovery restoration | 7 |
| 10 | 3 | Language switching | 3 |
| 11 | 3 | Credential issuance | — |
| 12 | 3 | Device management | 7 |
| 13 | 4 | Network error handling | — |
| 14 | 4 | Test coverage | all |

Tasks 1–7 and 11, 13 are independent and can be parallelized. Task 8 depends on 7. Task 9 depends on 7. Task 10 depends on 3. Task 12 depends on 7. Task 14 should run last.
