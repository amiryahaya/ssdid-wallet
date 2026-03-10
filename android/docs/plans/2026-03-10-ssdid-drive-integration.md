# SSDID Drive Deep Link Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate SSDID Wallet with SSDID Drive via deep links for auth callback and cloud backup/restore.

**Architecture:** Two features using app-to-app deep links — no API client code in the wallet. Auth callback extends the existing `authenticate` deep link with a `callback_url` parameter; after successful auth, the wallet launches the callback URL with a session token. Cloud backup reuses the existing `BackupManager` encrypted export and hands off files via `ACTION_SEND` intent. Restore accepts incoming share intents from ssdid-drive.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation Component, Android Intents, FileProvider, JUnit 4, Mockk, Truth, Robolectric

---

### Task 1: Extend DeepLinkHandler to Parse `callback_url` Parameter

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt:7-61`
- Test: `app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt`

**Step 1: Write the failing tests**

Add these tests to `DeepLinkHandlerTest.kt`:

```kotlin
@Test
fun `parse authenticate with callback_url returns callbackUrl`() {
    val uri = mockUri(
        scheme = "ssdid",
        host = "authenticate",
        queryParams = mapOf(
            "server_url" to "https://demo.ssdid.my",
            "callback_url" to "ssdiddrive://auth/callback"
        )
    )
    val result = DeepLinkHandler.parse(uri)

    assertThat(result).isNotNull()
    assertThat(result!!.action).isEqualTo("authenticate")
    assertThat(result.callbackUrl).isEqualTo("ssdiddrive://auth/callback")
}

@Test
fun `parse authenticate without callback_url has empty callbackUrl`() {
    val uri = mockUri(
        scheme = "ssdid",
        host = "authenticate",
        queryParams = mapOf("server_url" to "https://demo.ssdid.my")
    )
    val result = DeepLinkHandler.parse(uri)

    assertThat(result).isNotNull()
    assertThat(result!!.callbackUrl).isEmpty()
}

@Test
fun `parse authenticate rejects non-ssdiddrive callback_url scheme`() {
    val uri = mockUri(
        scheme = "ssdid",
        host = "authenticate",
        queryParams = mapOf(
            "server_url" to "https://demo.ssdid.my",
            "callback_url" to "https://evil.com/steal"
        )
    )
    val result = DeepLinkHandler.parse(uri)

    assertThat(result).isNotNull()
    assertThat(result!!.callbackUrl).isEmpty()
}

@Test
fun `parse authenticate rejects javascript callback_url scheme`() {
    val uri = mockUri(
        scheme = "ssdid",
        host = "authenticate",
        queryParams = mapOf(
            "server_url" to "https://demo.ssdid.my",
            "callback_url" to "javascript:alert(1)"
        )
    )
    val result = DeepLinkHandler.parse(uri)

    assertThat(result).isNotNull()
    assertThat(result!!.callbackUrl).isEmpty()
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" -x lint`
Expected: FAIL — `callbackUrl` property does not exist on `DeepLinkAction`

**Step 3: Implement the changes**

In `DeepLinkHandler.kt`, add `callbackUrl` field to `DeepLinkAction`:

```kotlin
data class DeepLinkAction(
    val action: String,
    val serverUrl: String,
    val serverDid: String = "",
    val sessionToken: String = "",
    val issuerUrl: String = "",
    val offerId: String = "",
    val callbackUrl: String = ""
)
```

Update `toNavRoute()` for `authenticate` to pass `callbackUrl`:

```kotlin
fun toNavRoute(): String? = when (action) {
    "register" -> Screen.Registration.createRoute(serverUrl, serverDid)
    "authenticate" -> Screen.AuthFlow.createRoute(serverUrl, callbackUrl)
    "sign" -> Screen.TxSigning.createRoute(serverUrl, sessionToken)
    "credential-offer" -> Screen.CredentialOffer.createRoute(issuerUrl, offerId)
    else -> null
}
```

In `parse()`, after extracting `serverUrl` (line 53-60), add callback_url parsing with scheme validation:

```kotlin
val serverUrl = uri.getQueryParameter("server_url") ?: return null
if (!UrlValidator.isValidServerUrl(serverUrl)) return null

val rawCallbackUrl = uri.getQueryParameter("callback_url") ?: ""
val callbackUrl = if (rawCallbackUrl.startsWith("ssdiddrive://")) rawCallbackUrl else ""

return DeepLinkAction(
    action = action,
    serverUrl = serverUrl,
    serverDid = uri.getQueryParameter("server_did") ?: "",
    sessionToken = uri.getQueryParameter("session_token") ?: "",
    callbackUrl = callbackUrl
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" -x lint`
Expected: ALL PASS (existing + new tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt \
       app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt
git commit -m "feat: add callback_url support to DeepLinkHandler for ssdid-drive auth"
```

---

### Task 2: Update Screen.kt and NavGraph.kt to Pass callbackUrl Through AuthFlow

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt:18-20`
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt:120-132`

**Step 1: Write the failing test**

Add to `DeepLinkHandlerTest.kt`:

```kotlin
@Test
fun `toNavRoute for authenticate with callbackUrl includes callbackUrl param`() {
    val deepLink = DeepLinkAction(
        action = "authenticate",
        serverUrl = "https://demo.ssdid.my",
        callbackUrl = "ssdiddrive://auth/callback"
    )
    val route = deepLink.toNavRoute()

    assertThat(route).isNotNull()
    assertThat(route).contains("auth_flow")
    assertThat(route).contains("callbackUrl")
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest.toNavRoute for authenticate with callbackUrl includes callbackUrl param" -x lint`
Expected: FAIL — route does not contain `callbackUrl`

**Step 3: Implement the changes**

Update `Screen.AuthFlow` in `Screen.kt`:

```kotlin
object AuthFlow : Screen("auth_flow?serverUrl={serverUrl}&callbackUrl={callbackUrl}") {
    fun createRoute(serverUrl: String, callbackUrl: String = "") =
        "auth_flow?serverUrl=${Uri.encode(serverUrl)}&callbackUrl=${Uri.encode(callbackUrl)}"
}
```

Update `NavGraph.kt` AuthFlow composable (line 120-132):

```kotlin
composable(
    Screen.AuthFlow.route,
    arguments = listOf(
        navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
        navArgument("callbackUrl") { type = NavType.StringType; defaultValue = "" }
    )
) {
    AuthFlowScreen(
        onBack = { navController.popBackStack() },
        onComplete = {
            navController.popBackStack(Screen.WalletHome.route, inclusive = false)
        }
    )
}
```

Also update `ScanQrScreen` onScanned (NavGraph.kt line 99) to pass callbackUrl:

```kotlin
"authenticate" -> navController.navigate(Screen.AuthFlow.createRoute(payload.serverUrl, payload.callbackUrl))
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" -x lint`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt \
       app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt \
       app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt
git commit -m "feat: wire callbackUrl through AuthFlow navigation route"
```

---

### Task 3: Modify AuthFlowViewModel to Launch Callback URL After Auth Success

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt:44-89`
- Create: `app/src/test/java/my/ssdid/wallet/feature/auth/AuthFlowViewModelTest.kt`

**Step 1: Write the failing tests**

Create `app/src/test/java/my/ssdid/wallet/feature/auth/AuthFlowViewModelTest.kt`:

```kotlin
package my.ssdid.wallet.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var client: SsdidClient
    private lateinit var vault: Vault
    private lateinit var biometricAuth: BiometricAuthenticator

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)
        coEvery { vault.listCredentials() } returns emptyList()
    }

    @Test
    fun `callbackUrl is extracted from SavedStateHandle`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        assertThat(vm.callbackUrl).isEqualTo("ssdiddrive://auth/callback")
    }

    @Test
    fun `callbackUrl defaults to empty when not provided`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my"
            ))
        )
        assertThat(vm.callbackUrl).isEmpty()
    }

    @Test
    fun `hasCallback is true when callbackUrl is non-empty`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        assertThat(vm.hasCallback).isTrue()
    }

    @Test
    fun `hasCallback is false when callbackUrl is empty`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my"
            ))
        )
        assertThat(vm.hasCallback).isFalse()
    }

    @Test
    fun `buildCallbackUri appends session_token to callbackUrl`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        val uri = vm.buildCallbackUri("mytoken123")

        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("ssdiddrive://auth/callback?session_token=mytoken123")
    }

    @Test
    fun `buildCallbackUri returns null when no callbackUrl`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my"
            ))
        )
        val uri = vm.buildCallbackUri("mytoken123")
        assertThat(uri).isNull()
    }

    @Test
    fun `auth success sets AuthState to Success with sessionToken`() = runTest {
        coEvery { client.authenticate(any(), any()) } returns Result.success("tok_abc")

        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        advanceUntilIdle()

        // Need a credential selected to authenticate
        val cred = mockk<my.ssdid.wallet.domain.model.VerifiableCredential>(relaxed = true)
        coEvery { vault.listCredentials() } returns listOf(cred)

        val vm2 = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://demo.ssdid.my",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        advanceUntilIdle()
        vm2.selectCredential(cred)
        vm2.authenticate()
        advanceUntilIdle()

        assertThat(vm2.state.value).isInstanceOf(AuthState.Success::class.java)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*.AuthFlowViewModelTest" -x lint`
Expected: FAIL — `callbackUrl`, `hasCallback`, `buildCallbackUri` don't exist

**Step 3: Implement the changes**

In `AuthFlowScreen.kt`, update `AuthFlowViewModel`:

```kotlin
@HiltViewModel
class AuthFlowViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val callbackUrl: String = savedStateHandle["callbackUrl"] ?: ""
    val hasCallback: Boolean get() = callbackUrl.isNotEmpty()

    // ... existing state, credentials, selectedCredential ...

    fun buildCallbackUri(sessionToken: String): android.net.Uri? {
        if (callbackUrl.isEmpty()) return null
        return android.net.Uri.parse(callbackUrl)
            .buildUpon()
            .appendQueryParameter("session_token", sessionToken)
            .build()
    }

    // ... rest unchanged ...
}
```

Update `AuthState.Success` to carry the session token:

```kotlin
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val sessionToken: String = "") : AuthState()
    data class Error(val message: String) : AuthState()
}
```

Update `authenticate()` to capture the session token:

```kotlin
fun authenticate() {
    val credential = _selectedCredential.value ?: return
    viewModelScope.launch {
        _state.value = AuthState.Loading
        client.authenticate(credential, serverUrl)
            .onSuccess { token -> _state.value = AuthState.Success(sessionToken = token ?: "") }
            .onFailure { _state.value = AuthState.Error(it.message ?: "Authentication failed") }
    }
}
```

**Important:** Check what `client.authenticate()` returns. If it returns `Result<String>` (session token), use it directly. If it returns `Result<Unit>`, we need to check how the session token is obtained. Look at `SsdidClient.authenticate()` to verify.

In `AuthFlowScreen` composable, add callback launch in the Success branch. Add an `import android.content.Intent` and:

```kotlin
is AuthState.Success -> {
    val authSuccess = state as AuthState.Success
    val callbackUri = viewModel.buildCallbackUri(authSuccess.sessionToken)

    // ... existing success UI ...

    Button(
        onClick = {
            if (callbackUri != null) {
                val intent = Intent(Intent.ACTION_VIEW, callbackUri)
                context.startActivity(intent)
            }
            onComplete()
        },
        // ... existing button config ...
    ) {
        Text(
            if (callbackUri != null) "Return to SSDID Drive" else "Done",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.AuthFlowViewModelTest" -x lint`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt \
       app/src/test/java/my/ssdid/wallet/feature/auth/AuthFlowViewModelTest.kt
git commit -m "feat: launch callback URL after successful auth for ssdid-drive"
```

---

### Task 4: Set Up FileProvider for Secure Inter-App File Sharing

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Create FileProvider paths config**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="backups" path="backups/" />
</paths>
```

**Step 2: Add FileProvider to AndroidManifest.xml**

Add inside `<application>` tag, after the `<activity>` block:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin -x lint`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml \
       app/src/main/AndroidManifest.xml
git commit -m "feat: add FileProvider for secure backup file sharing"
```

---

### Task 5: Add "Backup to Cloud" Button on BackupScreen

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt:130-490`
- Create: `app/src/test/java/my/ssdid/wallet/feature/backup/BackupViewModelTest.kt`

**Step 1: Write the failing tests**

Create `app/src/test/java/my/ssdid/wallet/feature/backup/BackupViewModelTest.kt`:

```kotlin
package my.ssdid.wallet.feature.backup

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.backup.BackupManager
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var backupManager: BackupManager
    private lateinit var biometricAuth: BiometricAuthenticator
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setup() {
        backupManager = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)
        viewModel = BackupViewModel(backupManager, biometricAuth)
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }

    @Test
    fun `createBackup success sets Success state with bytes`() = runTest {
        val testBytes = byteArrayOf(1, 2, 3)
        coEvery { backupManager.createBackup(any()) } returns Result.success(testBytes)

        viewModel.createBackup("password123")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(BackupState.Success::class.java)
        assertThat(viewModel.lastBackupBytes).isEqualTo(testBytes)
    }

    @Test
    fun `createBackup failure sets Error state`() = runTest {
        coEvery { backupManager.createBackup(any()) } returns Result.failure(RuntimeException("fail"))

        viewModel.createBackup("password123")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(BackupState.Error::class.java)
        assertThat((viewModel.state.value as BackupState.Error).message).isEqualTo("fail")
    }

    @Test
    fun `onBackupSaved clears lastBackupBytes`() = runTest {
        val testBytes = byteArrayOf(1, 2, 3)
        coEvery { backupManager.createBackup(any()) } returns Result.success(testBytes)
        viewModel.createBackup("password123")
        advanceUntilIdle()

        viewModel.onBackupSaved()

        assertThat(viewModel.lastBackupBytes).isNull()
    }

    @Test
    fun `restoreBackup with loaded bytes sets RestoreSuccess`() = runTest {
        val testBytes = byteArrayOf(4, 5, 6)
        viewModel.onBackupFileLoaded(testBytes)
        coEvery { backupManager.restoreBackup(testBytes, "pass") } returns Result.success(3)

        viewModel.restoreBackup("pass")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(BackupState.RestoreSuccess::class.java)
        assertThat((viewModel.state.value as BackupState.RestoreSuccess).count).isEqualTo(3)
    }

    @Test
    fun `restoreBackup without loaded bytes does nothing`() = runTest {
        viewModel.restoreBackup("pass")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }

    @Test
    fun `resetState returns to Idle`() = runTest {
        coEvery { backupManager.createBackup(any()) } returns Result.failure(RuntimeException("fail"))
        viewModel.createBackup("password123")
        advanceUntilIdle()

        viewModel.resetState()

        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }
}
```

**Step 2: Run tests to verify they pass** (these test existing behavior)

Run: `./gradlew :app:testDebugUnitTest --tests "*.BackupViewModelTest" -x lint`
Expected: ALL PASS — these test existing ViewModel behavior

**Step 3: Add "Backup to Cloud" button in BackupScreen composable**

In `BackupScreen.kt`, add import:

```kotlin
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
```

After the existing "Save to File" button inside the Success card (after line 334), add a second button:

```kotlin
Spacer(Modifier.height(8.dp))
Button(
    onClick = {
        val bytes = viewModel.lastBackupBytes ?: return@Button
        val cacheDir = File(context.cacheDir, "backups")
        cacheDir.mkdirs()
        val date = java.time.LocalDate.now().toString()
        val backupFile = File(cacheDir, "ssdid-backup-$date.enc")
        backupFile.writeBytes(bytes)
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            setPackage("my.ssdid.drive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(sendIntent)
    },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Accent)
) {
    Text("Backup to Cloud", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
```

**Step 4: Run all tests to verify nothing broke**

Run: `./gradlew :app:testDebugUnitTest -x lint`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt \
       app/src/test/java/my/ssdid/wallet/feature/backup/BackupViewModelTest.kt
git commit -m "feat: add Backup to Cloud button using ACTION_SEND to ssdid-drive"
```

---

### Task 6: Handle Incoming Share Intents for Backup Restore

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/my/ssdid/wallet/MainActivity.kt:26-90`
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt:199-201`

**Step 1: Add ACTION_SEND intent filter to AndroidManifest.xml**

Add a new intent filter to the `<activity>` tag in `AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/octet-stream" />
</intent-filter>
```

**Step 2: Handle incoming share intent in MainActivity.kt**

Add handling for `ACTION_SEND` in `MainActivity.kt`. Update `onNewIntent()` and the `handleDeepLink` logic:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    when {
        intent.data != null -> pendingDeepLinks.tryEmit(intent)
        intent.action == Intent.ACTION_SEND -> pendingDeepLinks.tryEmit(intent)
    }
}
```

Update `handleDeepLink` to also handle share intents. Rename it to `handleIntent` and add ACTION_SEND handling:

```kotlin
private fun handleIntent(intent: Intent, navController: NavHostController) {
    when (intent.action) {
        Intent.ACTION_SEND -> handleShareIntent(intent, navController)
        else -> handleDeepLink(intent, navController)
    }
}

private fun handleShareIntent(intent: Intent, navController: NavHostController) {
    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return
    // Read bytes from shared URI
    val bytes = try {
        contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read shared backup file", e)
        return
    }
    // Navigate to backup screen — the ViewModel will receive the bytes
    navController.navigate(Screen.BackupExport.route)
    // Post the bytes to a shared flow that BackupViewModel can collect
    pendingBackupBytes.tryEmit(bytes)
}
```

Add a shared flow for backup bytes:

```kotlin
private val pendingBackupBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
```

**Alternative simpler approach:** Instead of a shared flow, navigate directly to BackupScreen and use the existing `onBackupFileLoaded()` pattern. The BackupScreen already handles `loadedFileBytes`. We can encode the file URI as a nav argument:

Update `Screen.kt` — add an optional `restoreUri` param to `BackupExport`:

```kotlin
object BackupExport : Screen("backup_export?restoreUri={restoreUri}") {
    fun createRoute(restoreUri: String = "") =
        "backup_export?restoreUri=${Uri.encode(restoreUri)}"
}
```

Update `NavGraph.kt` for BackupExport:

```kotlin
composable(
    Screen.BackupExport.route,
    arguments = listOf(
        navArgument("restoreUri") { type = NavType.StringType; defaultValue = "" }
    )
) {
    BackupScreen(onBack = { navController.popBackStack() })
}
```

Update `BackupViewModel` to accept `SavedStateHandle` and auto-load the file:

```kotlin
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val restoreUri: String = savedStateHandle["restoreUri"] ?: ""
    // ... existing code ...
}
```

In `BackupScreen`, auto-load the file from restoreUri if present:

```kotlin
LaunchedEffect(viewModel.restoreUri) {
    if (viewModel.restoreUri.isNotEmpty()) {
        val uri = android.net.Uri.parse(viewModel.restoreUri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) {
            viewModel.onBackupFileLoaded(bytes)
        }
    }
}
```

In `MainActivity.handleShareIntent()`:

```kotlin
private fun handleShareIntent(intent: Intent, navController: NavHostController) {
    @Suppress("DEPRECATION")
    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return
    navController.navigate(Screen.BackupExport.createRoute(uri.toString()))
    intent.action = null
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin -x lint`
Expected: BUILD SUCCESSFUL

**Step 4: Run all tests**

Run: `./gradlew :app:testDebugUnitTest -x lint`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
       app/src/main/java/my/ssdid/wallet/MainActivity.kt \
       app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt \
       app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt \
       app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt
git commit -m "feat: handle incoming share intents for backup restore from ssdid-drive"
```

---

### Task 7: Add String Resources for New UI Text

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Add string resources**

Add to `strings.xml`:

```xml
<string name="backup_to_cloud">Backup to Cloud</string>
<string name="return_to_drive">Return to SSDID Drive</string>
```

**Step 2: Replace hardcoded strings in BackupScreen and AuthFlowScreen**

Replace `"Backup to Cloud"` with `stringResource(R.string.backup_to_cloud)` and `"Return to SSDID Drive"` with `stringResource(R.string.return_to_drive)`.

**Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin -x lint`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
       app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt \
       app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt
git commit -m "chore: add string resources for ssdid-drive integration UI"
```

---

### Task 8: Run Full Test Suite and Final Verification

**Step 1: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest -x lint`
Expected: ALL PASS

**Step 2: Run lint**

Run: `./gradlew lint`
Expected: No new errors

**Step 3: Verify all deep link tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" -x lint`
Expected: ALL PASS (original 13 + 5 new = 18 tests)

**Step 4: Commit any remaining fixes if needed**

```bash
git add -A
git commit -m "fix: address any lint/test issues from ssdid-drive integration"
```
