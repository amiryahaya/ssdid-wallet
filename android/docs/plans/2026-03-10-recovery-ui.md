# Recovery UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire up social recovery and institutional recovery domain logic to Compose UI screens, replacing "Coming Soon" placeholders.

**Architecture:** Three new screens (SocialRecoverySetupScreen, InstitutionalSetupScreen, SocialRecoveryRestoreScreen) follow the existing ViewModel+StateFlow pattern with Hilt DI. Two DataStore-backed storage implementations provide persistence for recovery configs. The existing RecoverySetupScreen and RecoveryRestoreScreen are updated to navigate to and integrate with the new screens.

**Tech Stack:** Jetpack Compose, Hilt, DataStore Preferences, kotlinx-serialization, StateFlow, Navigation Compose

---

### Task 1: DataStore Storage for Social Recovery

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreSocialRecoveryStorage.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/di/StorageModule.kt`

**Step 1: Create DataStoreSocialRecoveryStorage**

```kotlin
package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryConfig
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import javax.inject.Inject
import javax.inject.Singleton

private val Context.socialRecoveryStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_social_recovery")

@Singleton
class DataStoreSocialRecoveryStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SocialRecoveryStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configKey(did: String) = stringPreferencesKey("social_config_$did")

    override suspend fun saveSocialRecoveryConfig(config: SocialRecoveryConfig) {
        context.socialRecoveryStore.edit { prefs ->
            prefs[configKey(config.did)] = json.encodeToString(config)
        }
    }

    override suspend fun getSocialRecoveryConfig(did: String): SocialRecoveryConfig? {
        val jsonStr = context.socialRecoveryStore.data.map { it[configKey(did)] }.first()
            ?: return null
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteSocialRecoveryConfig(did: String) {
        context.socialRecoveryStore.edit { prefs ->
            prefs.remove(configKey(did))
        }
    }
}
```

**Step 2: Add to StorageModule**

Add these to `StorageModule.kt`:

```kotlin
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.wallet.platform.storage.DataStoreSocialRecoveryStorage

    @Provides
    @Singleton
    fun provideSocialRecoveryStorage(@ApplicationContext context: Context): SocialRecoveryStorage =
        DataStoreSocialRecoveryStorage(context)
```

**Step 3: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreSocialRecoveryStorage.kt app/src/main/java/my/ssdid/wallet/di/StorageModule.kt
git commit -m "feat: add DataStore storage for social recovery config"
```

---

### Task 2: DataStore Storage for Institutional Recovery

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreInstitutionalRecoveryStorage.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/di/StorageModule.kt`

**Step 1: Create DataStoreInstitutionalRecoveryStorage**

```kotlin
package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.wallet.domain.recovery.institutional.OrgRecoveryConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.institutionalRecoveryStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_institutional_recovery")

@Singleton
class DataStoreInstitutionalRecoveryStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : InstitutionalRecoveryStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configKey(userDid: String) = stringPreferencesKey("org_config_$userDid")

    override suspend fun saveOrgRecoveryConfig(config: OrgRecoveryConfig) {
        context.institutionalRecoveryStore.edit { prefs ->
            prefs[configKey(config.userDid)] = json.encodeToString(config)
        }
    }

    override suspend fun getOrgRecoveryConfig(userDid: String): OrgRecoveryConfig? {
        val jsonStr = context.institutionalRecoveryStore.data.map { it[configKey(userDid)] }.first()
            ?: return null
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteOrgRecoveryConfig(userDid: String) {
        context.institutionalRecoveryStore.edit { prefs ->
            prefs.remove(configKey(userDid))
        }
    }
}
```

**Step 2: Add to StorageModule**

Add these to `StorageModule.kt`:

```kotlin
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.wallet.platform.storage.DataStoreInstitutionalRecoveryStorage

    @Provides
    @Singleton
    fun provideInstitutionalRecoveryStorage(@ApplicationContext context: Context): InstitutionalRecoveryStorage =
        DataStoreInstitutionalRecoveryStorage(context)
```

**Step 3: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreInstitutionalRecoveryStorage.kt app/src/main/java/my/ssdid/wallet/di/StorageModule.kt
git commit -m "feat: add DataStore storage for institutional recovery config"
```

---

### Task 3: DI Providers for Recovery Managers

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

**Step 1: Add SocialRecoveryManager and InstitutionalRecoveryManager providers**

Add imports:
```kotlin
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryStorage
```

Add provider methods after `provideRecoveryManager`:
```kotlin
    @Provides
    @Singleton
    fun provideSocialRecoveryManager(
        recoveryManager: RecoveryManager,
        storage: SocialRecoveryStorage
    ): SocialRecoveryManager = SocialRecoveryManager(recoveryManager, storage)

    @Provides
    @Singleton
    fun provideInstitutionalRecoveryManager(
        recoveryManager: RecoveryManager,
        storage: InstitutionalRecoveryStorage
    ): InstitutionalRecoveryManager = InstitutionalRecoveryManager(recoveryManager, storage)
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "feat: add Hilt providers for social and institutional recovery managers"
```

---

### Task 4: Navigation Routes

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`

**Step 1: Add three new screen routes**

Add after `RecoveryRestore`:
```kotlin
    object SocialRecoverySetup : Screen("social_recovery_setup/{keyId}") {
        fun createRoute(keyId: String) = "social_recovery_setup/${Uri.encode(keyId)}"
    }
    object InstitutionalSetup : Screen("institutional_setup/{keyId}") {
        fun createRoute(keyId: String) = "institutional_setup/${Uri.encode(keyId)}"
    }
    object SocialRecoveryRestore : Screen("social_recovery_restore")
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt
git commit -m "feat: add navigation routes for social and institutional recovery screens"
```

---

### Task 5: Social Recovery Setup Screen

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/recovery/SocialRecoverySetupScreen.kt`

**Step 1: Create the screen with ViewModel**

```kotlin
package my.ssdid.wallet.feature.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class SocialSetupState {
    object Idle : SocialSetupState()
    object Creating : SocialSetupState()
    data class Success(val shares: Map<String, String>, val guardianNames: List<String>) : SocialSetupState()
    data class Error(val message: String) : SocialSetupState()
}

data class GuardianEntry(val name: String = "", val did: String = "")

@HiltViewModel
class SocialRecoverySetupViewModel @Inject constructor(
    private val socialRecoveryManager: SocialRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<SocialSetupState>(SocialSetupState.Idle)
    val state = _state.asStateFlow()

    private val _guardians = MutableStateFlow(listOf(GuardianEntry(), GuardianEntry()))
    val guardians = _guardians.asStateFlow()

    private val _threshold = MutableStateFlow(2)
    val threshold = _threshold.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun updateGuardian(index: Int, entry: GuardianEntry) {
        val list = _guardians.value.toMutableList()
        if (index < list.size) {
            list[index] = entry
            _guardians.value = list
        }
    }

    fun addGuardian() {
        _guardians.value = _guardians.value + GuardianEntry()
    }

    fun removeGuardian(index: Int) {
        if (_guardians.value.size > 2) {
            _guardians.value = _guardians.value.toMutableList().apply { removeAt(index) }
            if (_threshold.value > _guardians.value.size) {
                _threshold.value = _guardians.value.size
            }
        }
    }

    fun setThreshold(value: Int) {
        _threshold.value = value.coerceIn(2, _guardians.value.size)
    }

    fun createShares() {
        val id = _identity.value ?: return
        val entries = _guardians.value
        val valid = entries.all { it.name.isNotBlank() && it.did.isNotBlank() }
        if (!valid) {
            _state.value = SocialSetupState.Error("All guardian fields are required")
            return
        }

        viewModelScope.launch {
            _state.value = SocialSetupState.Creating
            val guardianPairs = entries.map { it.name to it.did }
            socialRecoveryManager.setupSocialRecovery(id, guardianPairs, _threshold.value)
                .onSuccess { shares ->
                    _state.value = SocialSetupState.Success(shares, entries.map { it.name })
                }
                .onFailure {
                    _state.value = SocialSetupState.Error(it.message ?: "Setup failed")
                }
        }
    }
}

@Composable
fun SocialRecoverySetupScreen(
    onBack: () -> Unit,
    viewModel: SocialRecoverySetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val guardians by viewModel.guardians.collectAsState()
    val threshold by viewModel.threshold.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Social Recovery", style = MaterialTheme.typography.titleLarge)
        }

        when (val currentState = state) {
            is SocialSetupState.Success -> {
                // Show shares for distribution
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessDim)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    "Social Recovery Configured",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Success,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${threshold}-of-${guardians.size} guardians required to recover",
                                    fontSize = 13.sp,
                                    color = Success
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningDim)
                        ) {
                            Text(
                                "Distribute each share to the corresponding guardian. Each guardian should store their share securely. Shares cannot be recovered once this screen is closed.",
                                modifier = Modifier.padding(18.dp),
                                fontSize = 13.sp,
                                color = Warning
                            )
                        }
                    }

                    val shareEntries = currentState.shares.entries.toList()
                    itemsIndexed(shareEntries) { idx, (_, shareData) ->
                        val guardianName = currentState.guardianNames.getOrElse(idx) { "Guardian ${idx + 1}" }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(guardianName, style = MaterialTheme.typography.titleMedium)
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentDim)
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text("Share ${idx + 1}", fontSize = 11.sp, color = Accent)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    shareData,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { clipboardManager.setText(AnnotatedString(shareData)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text("Copy Share", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BgTertiary)
                        ) {
                            Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
            else -> {
                // Guardian entry form
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Add trusted contacts as guardians. Your recovery secret will be split so that any $threshold of them can help you recover.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    // Threshold selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("RECOVERY THRESHOLD", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.setThreshold(threshold - 1) },
                                        enabled = threshold > 2
                                    ) { Text("\u2212", fontSize = 20.sp, color = if (threshold > 2) Accent else TextTertiary) }
                                    Text(
                                        "$threshold of ${guardians.size}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Accent
                                    )
                                    TextButton(
                                        onClick = { viewModel.setThreshold(threshold + 1) },
                                        enabled = threshold < guardians.size
                                    ) { Text("+", fontSize = 20.sp, color = if (threshold < guardians.size) Accent else TextTertiary) }
                                }
                                Text(
                                    "Minimum $threshold guardians needed to recover",
                                    fontSize = 12.sp,
                                    color = TextTertiary,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    // Guardian entries
                    itemsIndexed(guardians) { index, entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Guardian ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                    if (guardians.size > 2) {
                                        TextButton(onClick = { viewModel.removeGuardian(index) }) {
                                            Text("Remove", fontSize = 12.sp, color = Danger)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = entry.name,
                                    onValueChange = { viewModel.updateGuardian(index, entry.copy(name = it)) },
                                    placeholder = { Text("Name", color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent,
                                        unfocusedBorderColor = Border,
                                        cursorColor = Accent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = entry.did,
                                    onValueChange = { viewModel.updateGuardian(index, entry.copy(did = it)) },
                                    placeholder = { Text("did:ssdid:...", color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent,
                                        unfocusedBorderColor = Border,
                                        cursorColor = Accent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Add guardian button
                    item {
                        TextButton(
                            onClick = { viewModel.addGuardian() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Guardian", fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (currentState is SocialSetupState.Error) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DangerDim)
                            ) {
                                Text(
                                    currentState.message,
                                    modifier = Modifier.padding(18.dp),
                                    fontSize = 13.sp,
                                    color = Danger
                                )
                            }
                        }
                    }
                }

                // Footer button
                Button(
                    onClick = { viewModel.createShares() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = state !is SocialSetupState.Creating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (state is SocialSetupState.Creating) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Creating Shares...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Create Recovery Shares", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/recovery/SocialRecoverySetupScreen.kt
git commit -m "feat: add social recovery setup screen with guardian management"
```

---

### Task 6: Institutional Recovery Setup Screen

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/recovery/InstitutionalSetupScreen.kt`

**Step 1: Create the screen with ViewModel**

```kotlin
package my.ssdid.wallet.feature.recovery

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class InstitutionalSetupState {
    object Idle : InstitutionalSetupState()
    object Enrolling : InstitutionalSetupState()
    data class Success(val orgName: String) : InstitutionalSetupState()
    data class Error(val message: String) : InstitutionalSetupState()
}

@HiltViewModel
class InstitutionalSetupViewModel @Inject constructor(
    private val institutionalManager: InstitutionalRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<InstitutionalSetupState>(InstitutionalSetupState.Idle)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun enroll(orgName: String, orgDid: String, encryptedKeyBase64: String) {
        val id = _identity.value ?: return
        if (orgName.isBlank() || orgDid.isBlank() || encryptedKeyBase64.isBlank()) {
            _state.value = InstitutionalSetupState.Error("All fields are required")
            return
        }

        viewModelScope.launch {
            _state.value = InstitutionalSetupState.Enrolling
            try {
                val keyBytes = Base64.decode(encryptedKeyBase64, Base64.DEFAULT)
                institutionalManager.enrollOrganization(id, orgDid, orgName, keyBytes)
                    .onSuccess { _state.value = InstitutionalSetupState.Success(orgName) }
                    .onFailure { _state.value = InstitutionalSetupState.Error(it.message ?: "Enrollment failed") }
            } catch (e: IllegalArgumentException) {
                _state.value = InstitutionalSetupState.Error("Invalid Base64 key format")
            }
        }
    }
}

@Composable
fun InstitutionalSetupScreen(
    onBack: () -> Unit,
    viewModel: InstitutionalSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val identity by viewModel.identity.collectAsState()
    var orgName by remember { mutableStateOf("") }
    var orgDid by remember { mutableStateOf("") }
    var encryptedKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Institutional Recovery", style = MaterialTheme.typography.titleLarge)
        }

        when (val currentState = state) {
            is InstitutionalSetupState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SuccessDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2713", fontSize = 28.sp, color = Success, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Organization Enrolled",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${currentState.orgName} can now assist with account recovery.",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Enroll an organization as a recovery custodian. They will hold an encrypted copy of your recovery key and can assist with account recovery after identity verification.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    if (identity?.hasRecoveryKey != true) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = WarningDim)
                            ) {
                                Text(
                                    "A recovery key must be generated first. Go back and set up Recovery Key (Tier 1) before enrolling an organization.",
                                    modifier = Modifier.padding(18.dp),
                                    fontSize = 13.sp,
                                    color = Warning
                                )
                            }
                        }
                    }

                    item {
                        Text("ORGANIZATION NAME", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = orgName,
                            onValueChange = { orgName = it },
                            placeholder = { Text("e.g. Acme Corp", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text("ORGANIZATION DID", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = orgDid,
                            onValueChange = { orgDid = it },
                            placeholder = { Text("did:ssdid:...", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text("ENCRYPTED RECOVERY KEY (BASE64)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = encryptedKey,
                            onValueChange = { encryptedKey = it },
                            placeholder = { Text("Paste encrypted recovery key", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            minLines = 3,
                            maxLines = 5
                        )
                    }

                    if (currentState is InstitutionalSetupState.Error) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DangerDim)
                            ) {
                                Text(
                                    currentState.message,
                                    modifier = Modifier.padding(18.dp),
                                    fontSize = 13.sp,
                                    color = Danger
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.enroll(orgName, orgDid, encryptedKey) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = identity?.hasRecoveryKey == true &&
                        orgName.isNotBlank() && orgDid.isNotBlank() && encryptedKey.isNotBlank() &&
                        state !is InstitutionalSetupState.Enrolling,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (state is InstitutionalSetupState.Enrolling) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Enrolling...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Enroll Organization", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/recovery/InstitutionalSetupScreen.kt
git commit -m "feat: add institutional recovery setup screen"
```

---

### Task 7: Social Recovery Restore Screen

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/recovery/SocialRecoveryRestoreScreen.kt`

**Step 1: Create the screen with ViewModel**

```kotlin
package my.ssdid.wallet.feature.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class SocialRestoreState {
    object Idle : SocialRestoreState()
    object Restoring : SocialRestoreState()
    object Success : SocialRestoreState()
    data class Error(val message: String) : SocialRestoreState()
}

data class ShareEntry(val index: String = "", val data: String = "")

@HiltViewModel
class SocialRecoveryRestoreViewModel @Inject constructor(
    private val socialRecoveryManager: SocialRecoveryManager,
    private val ssdidClient: SsdidClient,
    private val storage: VaultStorage
) : ViewModel() {

    private val _state = MutableStateFlow<SocialRestoreState>(SocialRestoreState.Idle)
    val state = _state.asStateFlow()

    private val _shares = MutableStateFlow(listOf(ShareEntry(), ShareEntry()))
    val shares = _shares.asStateFlow()

    fun updateShare(index: Int, entry: ShareEntry) {
        val list = _shares.value.toMutableList()
        if (index < list.size) {
            list[index] = entry
            _shares.value = list
        }
    }

    fun addShare() {
        _shares.value = _shares.value + ShareEntry()
    }

    fun removeShare(index: Int) {
        if (_shares.value.size > 2) {
            _shares.value = _shares.value.toMutableList().apply { removeAt(index) }
        }
    }

    fun restore(did: String, name: String, algorithm: Algorithm) {
        if (did.isBlank() || name.isBlank()) {
            _state.value = SocialRestoreState.Error("DID and name are required")
            return
        }
        val shareEntries = _shares.value
        val valid = shareEntries.all { it.index.isNotBlank() && it.data.isNotBlank() }
        if (!valid) {
            _state.value = SocialRestoreState.Error("All share fields are required")
            return
        }

        viewModelScope.launch {
            _state.value = SocialRestoreState.Restoring
            try {
                val collectedShares = shareEntries.associate {
                    it.index.toInt() to it.data
                }
                socialRecoveryManager.recoverWithShares(did, collectedShares, name, algorithm)
                    .onSuccess { identity ->
                        try { ssdidClient.updateDidDocument(identity.keyId) } catch (_: Exception) {}
                        storage.setOnboardingCompleted()
                        _state.value = SocialRestoreState.Success
                    }
                    .onFailure { _state.value = SocialRestoreState.Error(it.message ?: "Recovery failed") }
            } catch (e: NumberFormatException) {
                _state.value = SocialRestoreState.Error("Share index must be a number")
            }
        }
    }
}

@Composable
fun SocialRecoveryRestoreScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: SocialRecoveryRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val shares by viewModel.shares.collectAsState()
    var did by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(Algorithm.KAZ_SIGN_192) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Social Recovery", style = MaterialTheme.typography.titleLarge)
        }

        when (state) {
            is SocialRestoreState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SuccessDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2713", fontSize = 28.sp, color = Success, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Identity Restored",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your identity has been recovered using guardian shares.",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Collect shares from your guardians to restore your identity. You need the minimum threshold number of shares.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    item {
                        Text("DID", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = did,
                            onValueChange = { did = it },
                            placeholder = { Text("did:ssdid:...", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text("IDENTITY NAME", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("e.g. Personal, Work", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text("SIGNATURE ALGORITHM", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Algorithm.entries.forEach { algo ->
                                val isSelected = selectedAlgorithm == algo
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) AccentDim else BgCard
                                    ),
                                    onClick = { selectedAlgorithm = algo }
                                ) {
                                    Row(Modifier.padding(14.dp)) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedAlgorithm = algo },
                                            colors = RadioButtonDefaults.colors(selectedColor = Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(algo.name.replace("_", " "), style = MaterialTheme.typography.titleMedium)
                                            Text(algo.w3cType, fontSize = 11.sp, color = TextTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("GUARDIAN SHARES", style = MaterialTheme.typography.labelMedium)
                    }

                    itemsIndexed(shares) { index, entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Share ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                    if (shares.size > 2) {
                                        TextButton(onClick = { viewModel.removeShare(index) }) {
                                            Text("Remove", fontSize = 12.sp, color = Danger)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = entry.index,
                                    onValueChange = { viewModel.updateShare(index, entry.copy(index = it)) },
                                    placeholder = { Text("Share index (number)", color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                        cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = entry.data,
                                    onValueChange = { viewModel.updateShare(index, entry.copy(data = it)) },
                                    placeholder = { Text("Paste Base64 share data", color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                        cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                                    ),
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }
                        }
                    }

                    item {
                        TextButton(
                            onClick = { viewModel.addShare() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Share", fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (state is SocialRestoreState.Error) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DangerDim)
                            ) {
                                Text(
                                    (state as SocialRestoreState.Error).message,
                                    modifier = Modifier.padding(18.dp),
                                    fontSize = 13.sp,
                                    color = Danger
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.restore(did, name, selectedAlgorithm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = did.isNotBlank() && name.isNotBlank() &&
                        shares.all { it.index.isNotBlank() && it.data.isNotBlank() } &&
                        state !is SocialRestoreState.Restoring,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (state is SocialRestoreState.Restoring) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Recovering...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Recover Identity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/recovery/SocialRecoveryRestoreScreen.kt
git commit -m "feat: add social recovery restore screen with share collection"
```

---

### Task 8: Update RecoverySetupScreen to Wire Social and Institutional Cards

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/recovery/RecoverySetupScreen.kt`

**Step 1: Update ViewModel to check social/institutional status**

Replace the existing `RecoverySetupViewModel` with:

```kotlin
@HiltViewModel
class RecoverySetupViewModel @Inject constructor(
    private val recoveryManager: RecoveryManager,
    private val socialRecoveryManager: SocialRecoveryManager,
    private val institutionalManager: InstitutionalRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<RecoverySetupState>(RecoverySetupState.Idle)
    val state = _state.asStateFlow()

    private val _hasSocialRecovery = MutableStateFlow(false)
    val hasSocialRecovery = _hasSocialRecovery.asStateFlow()

    private val _socialConfig = MutableStateFlow<SocialRecoveryConfig?>(null)
    val socialConfig = _socialConfig.asStateFlow()

    private val _hasInstitutionalRecovery = MutableStateFlow(false)
    val hasInstitutionalRecovery = _hasInstitutionalRecovery.asStateFlow()

    private val _orgConfig = MutableStateFlow<OrgRecoveryConfig?>(null)
    val orgConfig = _orgConfig.asStateFlow()

    init {
        viewModelScope.launch {
            val id = vault.getIdentity(keyId)
            _identity.value = id
            if (id != null) {
                _hasSocialRecovery.value = socialRecoveryManager.hasSocialRecovery(id.did)
                _socialConfig.value = socialRecoveryManager.getConfig(id.did)
                _hasInstitutionalRecovery.value = institutionalManager.hasOrgRecovery(id.did)
                _orgConfig.value = institutionalManager.getConfig(id.did)
            }
        }
    }

    fun generateRecoveryKey() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            _state.value = RecoverySetupState.Generating
            recoveryManager.generateRecoveryKey(id)
                .onSuccess { keyBytes ->
                    _state.value = RecoverySetupState.Success(keyBytes)
                    _identity.value = vault.getIdentity(keyId)
                }
                .onFailure { _state.value = RecoverySetupState.Error(it.message ?: "Generation failed") }
        }
    }
}
```

New imports to add:
```kotlin
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryConfig
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.OrgRecoveryConfig
```

**Step 2: Update RecoverySetupScreen composable signature and social/institutional cards**

Add `onNavigateToSocialSetup` and `onNavigateToInstitutionalSetup` callbacks:

```kotlin
@Composable
fun RecoverySetupScreen(
    onBack: () -> Unit,
    onNavigateToSocialSetup: (String) -> Unit = {},
    onNavigateToInstitutionalSetup: (String) -> Unit = {},
    viewModel: RecoverySetupViewModel = hiltViewModel()
) {
```

Collect the new state flows:
```kotlin
    val hasSocialRecovery by viewModel.hasSocialRecovery.collectAsState()
    val socialConfig by viewModel.socialConfig.collectAsState()
    val hasInstitutionalRecovery by viewModel.hasInstitutionalRecovery.collectAsState()
    val orgConfig by viewModel.orgConfig.collectAsState()
```

Replace the Social Recovery tier card (lines ~196-211) with:
```kotlin
            // Tier 2: Social Recovery
            item {
                val keyId = identity?.keyId ?: ""
                RecoveryTierCard(
                    emoji = "\uD83D\uDC65",
                    title = "Social Recovery",
                    description = if (hasSocialRecovery) {
                        val config = socialConfig
                        "${config?.threshold}-of-${config?.totalShares} guardians"
                    } else {
                        "Split recovery secret among trusted contacts"
                    },
                    badgeText = "Advanced",
                    badgeColor = Accent,
                    badgeBgColor = AccentDim,
                    isConfigured = hasSocialRecovery,
                    buttonText = "Set Up Social Recovery",
                    buttonEnabled = identity?.hasRecoveryKey == true,
                    isLoading = false,
                    onClick = { onNavigateToSocialSetup(keyId) }
                )
            }
```

Replace the Institutional tier card (lines ~213-228) with:
```kotlin
            // Tier 3: Institutional
            item {
                val keyId = identity?.keyId ?: ""
                RecoveryTierCard(
                    emoji = "\uD83C\uDFE2",
                    title = "Institutional",
                    description = if (hasInstitutionalRecovery) {
                        orgConfig?.orgName ?: "Organization enrolled"
                    } else {
                        "Organization holds recovery authority"
                    },
                    badgeText = "Enterprise",
                    badgeColor = Pqc,
                    badgeBgColor = PqcDim,
                    isConfigured = hasInstitutionalRecovery,
                    buttonText = "Enroll Organization",
                    buttonEnabled = identity?.hasRecoveryKey == true,
                    isLoading = false,
                    onClick = { onNavigateToInstitutionalSetup(keyId) }
                )
            }
```

**Step 3: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/recovery/RecoverySetupScreen.kt
git commit -m "feat: wire social and institutional recovery cards in setup screen"
```

---

### Task 9: Update RecoveryRestoreScreen with Method Selector

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/recovery/RecoveryRestoreScreen.kt`

**Step 1: Add recovery method selector**

Add `onNavigateToSocialRestore` callback to the composable:

```kotlin
@Composable
fun RecoveryRestoreScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onNavigateToSocialRestore: () -> Unit = {},
    viewModel: RecoveryRestoreViewModel = hiltViewModel()
) {
```

Insert a method selector card at the top of the `else` branch (after the info text item, before the DID field), inside the LazyColumn:

```kotlin
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            onClick = onNavigateToSocialRestore
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\uD83D\uDC65", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Social Recovery", style = MaterialTheme.typography.titleMedium)
                                    Text("Recover using guardian shares", fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("\u203A", fontSize = 20.sp, color = TextTertiary)
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(Modifier.weight(1f), color = Border)
                            Text("  or use recovery key  ", fontSize = 12.sp, color = TextTertiary)
                            HorizontalDivider(Modifier.weight(1f), color = Border)
                        }
                    }
```

Add import:
```kotlin
import androidx.compose.material3.HorizontalDivider
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/recovery/RecoveryRestoreScreen.kt
git commit -m "feat: add recovery method selector to restore screen"
```

---

### Task 10: Register New Screens in NavGraph

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add imports and composable entries**

Add imports:
```kotlin
import my.ssdid.wallet.feature.recovery.SocialRecoverySetupScreen
import my.ssdid.wallet.feature.recovery.InstitutionalSetupScreen
import my.ssdid.wallet.feature.recovery.SocialRecoveryRestoreScreen
```

Update existing `RecoverySetup` composable to pass navigation callbacks:
```kotlin
        composable(Screen.RecoverySetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            RecoverySetupScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSocialSetup = { keyId ->
                    navController.navigate(Screen.SocialRecoverySetup.createRoute(keyId))
                },
                onNavigateToInstitutionalSetup = { keyId ->
                    navController.navigate(Screen.InstitutionalSetup.createRoute(keyId))
                }
            )
        }
```

Update existing `RecoveryRestore` composable:
```kotlin
        composable(Screen.RecoveryRestore.route) {
            RecoveryRestoreScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.RecoveryRestore.route) { inclusive = true }
                    }
                },
                onNavigateToSocialRestore = {
                    navController.navigate(Screen.SocialRecoveryRestore.route)
                }
            )
        }
```

Add three new composable entries before the closing `}` of `NavHost`:
```kotlin
        composable(Screen.SocialRecoverySetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            SocialRecoverySetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.InstitutionalSetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            InstitutionalSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SocialRecoveryRestore.route) {
            SocialRecoveryRestoreScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.SocialRecoveryRestore.route) { inclusive = true }
                    }
                }
            )
        }
```

**Step 2: Compile to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt
git commit -m "feat: register social and institutional recovery screens in nav graph"
```
