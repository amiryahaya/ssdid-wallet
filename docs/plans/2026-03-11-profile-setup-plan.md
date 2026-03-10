# Profile Setup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a profile setup screen to the onboarding flow so users enter name/email/phone before creating an identity, stored as a self-issued credential and shared during Sign In with SSDID.

**Architecture:** Global profile stored as a `VerifiableCredential` with fixed ID `urn:ssdid:profile`. A `ProfileManager` handles CRUD. The same form composable is reused for onboarding (Continue) and settings edit (Save). The consent flow reads claims from the profile instead of per-identity credentials.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx-serialization, MockK, Truth, Robolectric

---

### Task 1: ProfileManager — Domain Logic

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/profile/ProfileManager.kt`
- Test: `app/src/test/java/my/ssdid/wallet/domain/profile/ProfileManagerTest.kt`

**Step 1: Write the failing tests**

```kotlin
package my.ssdid.wallet.domain.profile

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class ProfileManagerTest {

    private lateinit var vault: Vault
    private lateinit var manager: ProfileManager

    @Before
    fun setup() {
        vault = mockk()
        manager = ProfileManager(vault)
    }

    @Test
    fun `saveProfile creates credential with correct structure`() = runTest {
        coEvery { vault.storeCredential(any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)
        coEvery { vault.listCredentials() } returns emptyList()

        val result = manager.saveProfile("Alice", "alice@example.com", "+60123456789")

        assertThat(result.isSuccess).isTrue()
        coVerify {
            vault.storeCredential(match { vc ->
                vc.id == "urn:ssdid:profile"
                    && vc.issuer == "did:ssdid:self"
                    && vc.type.contains("ProfileCredential")
                    && vc.credentialSubject.id == "did:ssdid:self"
                    && vc.credentialSubject.claims["name"] == "Alice"
                    && vc.credentialSubject.claims["email"] == "alice@example.com"
                    && vc.credentialSubject.claims["phone"] == "+60123456789"
                    && vc.proof.type == "SelfIssued2024"
            })
        }
    }

    @Test
    fun `saveProfile without phone omits phone claim`() = runTest {
        coEvery { vault.storeCredential(any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)
        coEvery { vault.listCredentials() } returns emptyList()

        manager.saveProfile("Alice", "alice@example.com", "")

        coVerify {
            vault.storeCredential(match { vc ->
                !vc.credentialSubject.claims.containsKey("phone")
                    && vc.credentialSubject.claims["name"] == "Alice"
                    && vc.credentialSubject.claims["email"] == "alice@example.com"
            })
        }
    }

    @Test
    fun `saveProfile deletes existing profile before saving`() = runTest {
        val existing = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:self", claims = mapOf("name" to "Old")),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(existing)
        coEvery { vault.deleteCredential("urn:ssdid:profile") } returns Result.success(Unit)
        coEvery { vault.storeCredential(any()) } returns Result.success(Unit)

        manager.saveProfile("New Name", "new@example.com", "")

        coVerifyOrder {
            vault.deleteCredential("urn:ssdid:profile")
            vault.storeCredential(any())
        }
    }

    @Test
    fun `getProfile returns profile credential`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(profile)

        val result = manager.getProfile()
        assertThat(result).isNotNull()
        assertThat(result!!.credentialSubject.claims["name"]).isEqualTo("Alice")
    }

    @Test
    fun `getProfile returns null when no profile exists`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()
        assertThat(manager.getProfile()).isNull()
    }

    @Test
    fun `getProfileClaims returns claims map`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(profile)

        val claims = manager.getProfileClaims()
        assertThat(claims).containsEntry("name", "Alice")
        assertThat(claims).containsEntry("email", "alice@example.com")
    }

    @Test
    fun `getProfileClaims returns empty map when no profile`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()
        assertThat(manager.getProfileClaims()).isEmpty()
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.profile.ProfileManagerTest"`
Expected: FAIL — `ProfileManager` class does not exist

**Step 3: Implement ProfileManager**

```kotlin
package my.ssdid.wallet.domain.profile

import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.vault.Vault
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ProfileManager @Inject constructor(private val vault: Vault) {

    companion object {
        const val PROFILE_ID = "urn:ssdid:profile"
        const val SELF_ISSUER = "did:ssdid:self"
    }

    suspend fun saveProfile(name: String, email: String, phone: String): Result<Unit> = runCatching {
        // Delete existing profile if present
        val existing = vault.listCredentials().find { it.id == PROFILE_ID }
        if (existing != null) vault.deleteCredential(PROFILE_ID)

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val claims = mutableMapOf("name" to name, "email" to email)
        if (phone.isNotBlank()) claims["phone"] = phone

        val credential = VerifiableCredential(
            id = PROFILE_ID,
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = SELF_ISSUER,
            issuanceDate = now,
            credentialSubject = CredentialSubject(id = SELF_ISSUER, claims = claims),
            proof = Proof(
                type = "SelfIssued2024",
                created = now,
                verificationMethod = SELF_ISSUER,
                proofPurpose = "selfAssertion",
                proofValue = ""
            )
        )
        vault.storeCredential(credential).getOrThrow()
    }

    suspend fun getProfile(): VerifiableCredential? {
        return vault.listCredentials().find { it.id == PROFILE_ID }
    }

    suspend fun getProfileClaims(): Map<String, String> {
        return getProfile()?.credentialSubject?.claims ?: emptyMap()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.profile.ProfileManagerTest"`
Expected: PASS (7 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/profile/ProfileManager.kt \
       app/src/test/java/my/ssdid/wallet/domain/profile/ProfileManagerTest.kt
git commit -m "feat: add ProfileManager for self-issued profile credential"
```

---

### Task 2: Navigation Routes

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`

**Step 1: Add ProfileSetup and ProfileEdit routes**

Add after the `BiometricSetup` object in `Screen.kt`:

```kotlin
object ProfileSetup : Screen("profile_setup")
object ProfileEdit : Screen("profile_edit")
```

**Step 2: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt
git commit -m "feat: add ProfileSetup and ProfileEdit navigation routes"
```

---

### Task 3: ProfileSetupScreen — UI + ViewModel

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/profile/ProfileSetupScreen.kt`
- Test: `app/src/test/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModelTest.kt`

**Step 1: Write the failing ViewModel tests**

```kotlin
package my.ssdid.wallet.feature.profile

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.profile.ProfileManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileSetupViewModelTest {

    private lateinit var profileManager: ProfileManager
    private lateinit var vm: ProfileSetupViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        profileManager = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileSetupViewModel {
        return ProfileSetupViewModel(profileManager)
    }

    @Test
    fun `initial state has empty fields and no errors`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEmpty()
        assertThat(vm.email.value).isEmpty()
        assertThat(vm.phone.value).isEmpty()
        assertThat(vm.nameError.value).isNull()
        assertThat(vm.emailError.value).isNull()
    }

    @Test
    fun `loads existing profile in edit mode`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com", "phone" to "+60123456789")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { profileManager.getProfile() } returns profile
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEqualTo("Alice")
        assertThat(vm.email.value).isEqualTo("alice@example.com")
        assertThat(vm.phone.value).isEqualTo("+60123456789")
    }

    @Test
    fun `isValid is false when name empty`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is false when email invalid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("not-an-email")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is true when name and email valid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isTrue()
    }

    @Test
    fun `isValid is false when phone provided but invalid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.updatePhone("12345")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is true when phone provided and valid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.updatePhone("+60123456789")
        assertThat(vm.isValid.value).isTrue()
    }

    @Test
    fun `save calls profileManager and sets saved state`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        coEvery { profileManager.saveProfile(any(), any(), any()) } returns Result.success(Unit)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.saved.value).isTrue()
        coVerify { profileManager.saveProfile("Alice", "alice@example.com", "") }
    }

    @Test
    fun `save sets error on failure`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        coEvery { profileManager.saveProfile(any(), any(), any()) } returns Result.failure(RuntimeException("Storage error"))
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.saved.value).isFalse()
        assertThat(vm.error.value).isNotNull()
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.feature.profile.ProfileSetupViewModelTest"`
Expected: FAIL — class does not exist

**Step 3: Implement ProfileSetupViewModel and ProfileSetupScreen**

Create `app/src/main/java/my/ssdid/wallet/feature/profile/ProfileSetupScreen.kt`:

```kotlin
package my.ssdid.wallet.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.auth.ClaimValidator
import my.ssdid.wallet.domain.profile.ProfileManager
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()

    private val _nameError = MutableStateFlow<String?>(null)
    val nameError = _nameError.asStateFlow()

    private val _emailError = MutableStateFlow<String?>(null)
    val emailError = _emailError.asStateFlow()

    private val _phoneError = MutableStateFlow<String?>(null)
    val phoneError = _phoneError.asStateFlow()

    private val _isValid = MutableStateFlow(false)
    val isValid = _isValid.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = profileManager.getProfile()
            if (existing != null) {
                val claims = existing.credentialSubject.claims
                _name.value = claims["name"] ?: ""
                _email.value = claims["email"] ?: ""
                _phone.value = claims["phone"] ?: ""
            }
            _loading.value = false
        }
    }

    fun updateName(value: String) {
        _name.value = value
        _nameError.value = ClaimValidator.validate("name", value)
        revalidate()
    }

    fun updateEmail(value: String) {
        _email.value = value
        _emailError.value = ClaimValidator.validate("email", value)
        revalidate()
    }

    fun updatePhone(value: String) {
        _phone.value = value
        _phoneError.value = if (value.isBlank()) null else ClaimValidator.validate("phone", value)
        revalidate()
    }

    private fun revalidate() {
        val nameOk = _name.value.isNotBlank() && _nameError.value == null
        val emailOk = _email.value.isNotBlank() && _emailError.value == null
        val phoneOk = _phone.value.isBlank() || _phoneError.value == null
        _isValid.value = nameOk && emailOk && phoneOk
    }

    fun save() {
        viewModelScope.launch {
            _error.value = null
            val result = profileManager.saveProfile(_name.value, _email.value, _phone.value)
            if (result.isSuccess) {
                _saved.value = true
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save profile"
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    buttonText: String = "Continue",
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsState()
    val email by viewModel.email.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val nameError by viewModel.nameError.collectAsState()
    val emailError by viewModel.emailError.collectAsState()
    val phoneError by viewModel.phoneError.collectAsState()
    val isValid by viewModel.isValid.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(saved) {
        if (saved) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("\u2190", color = TextPrimary, fontSize = 20.sp)
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                if (onBack != null) "Edit Profile" else "Set Up Your Profile",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (!loading) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "This information can be shared when you sign in to services using your SSDID wallet.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Name field
                Text("NAME *", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = Danger) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                Spacer(Modifier.height(12.dp))

                // Email field
                Text("EMAIL *", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { viewModel.updateEmail(it) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = Danger) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                Spacer(Modifier.height(12.dp))

                // Phone field
                Text("PHONE", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { viewModel.updatePhone(it) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = phoneError != null,
                    supportingText = phoneError?.let { { Text(it, color = Danger) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(Modifier.height(8.dp))
                Text("* Required", fontSize = 12.sp, color = TextTertiary)

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, fontSize = 13.sp, color = Danger)
                }
            }

            // Footer
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (onBack == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You can edit this later in Settings.",
                        fontSize = 12.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.feature.profile.ProfileSetupViewModelTest"`
Expected: PASS (9 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/profile/ProfileSetupScreen.kt \
       app/src/test/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModelTest.kt
git commit -m "feat: add ProfileSetupScreen with form validation and ViewModel"
```

---

### Task 4: Wire Navigation — Onboarding Flow

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add ProfileSetup route and update Onboarding flow**

In `NavGraph.kt`, add the import:

```kotlin
import my.ssdid.wallet.feature.profile.ProfileSetupScreen
```

Add the ProfileSetup composable route (after the Onboarding composable):

```kotlin
composable(Screen.ProfileSetup.route) {
    ProfileSetupScreen(
        onComplete = {
            navController.navigate(Screen.CreateIdentity.createRoute()) {
                popUpTo(Screen.ProfileSetup.route) { inclusive = true }
            }
        }
    )
}
```

Change the Onboarding `onComplete` callback from navigating to `Screen.CreateIdentity` to `Screen.ProfileSetup`:

```kotlin
composable(Screen.Onboarding.route) {
    OnboardingScreen(
        onComplete = {
            navController.navigate(Screen.ProfileSetup.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }
        },
        onRestore = {
            navController.navigate(Screen.RecoveryRestore.route)
        }
    )
}
```

**Step 2: Add ProfileEdit route for Settings**

```kotlin
composable(Screen.ProfileEdit.route) {
    ProfileSetupScreen(
        onComplete = { navController.popBackStack() },
        onBack = { navController.popBackStack() },
        buttonText = "Save"
    )
}
```

**Step 3: Compile and run existing tests**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt
git commit -m "feat: wire ProfileSetup into onboarding flow and add ProfileEdit route"
```

---

### Task 5: Settings Screen — Profile Menu Item

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt`

**Step 1: Add `onProfile` callback and Profile menu item**

Add `onProfile` parameter to `SettingsScreen`:

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBackupExport: () -> Unit = {},
    onProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
```

Add a new section at the top of the `LazyColumn`, before the SECURITY section:

```kotlin
item { Text("ACCOUNT", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
item { SettingsItem("Profile", "Name, email, phone", onClick = onProfile) }
item { Spacer(Modifier.height(16.dp)) }
```

**Step 2: Update NavGraph to pass `onProfile`**

In `NavGraph.kt`, update the Settings composable:

```kotlin
composable(Screen.Settings.route) {
    SettingsScreen(
        onBack = { navController.popBackStack() },
        onBackupExport = { navController.navigate(Screen.BackupExport.route) },
        onProfile = { navController.navigate(Screen.ProfileEdit.route) }
    )
}
```

**Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt \
       app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt
git commit -m "feat: add Profile menu item to Settings screen"
```

---

### Task 6: Update ConsentViewModel to Use Global Profile

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt`
- Modify: `app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt`

**Step 1: Update ConsentViewModel**

Add `ProfileManager` to constructor:

```kotlin
@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val verifier: Verifier,
    private val biometricAuth: BiometricAuthenticator,
    private val profileManager: ProfileManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

Add import:

```kotlin
import my.ssdid.wallet.domain.profile.ProfileManager
```

Replace `hasAllRequiredClaims` flow — change `vault.getCredentialForDid(identity.did)` to `profileManager.getProfileClaims()`:

```kotlin
@Suppress("OPT_IN_USAGE")
val hasAllRequiredClaims: StateFlow<Boolean> = _selectedIdentity
    .flatMapLatest { identity ->
        if (identity == null) flowOf(false)
        else flow {
            val requiredKeys = requestedClaims.filter { it.required }.map { it.key }
            if (requiredKeys.isEmpty()) {
                emit(true)
            } else {
                try {
                    val claims = profileManager.getProfileClaims()
                    emit(requiredKeys.all { !claims[it].isNullOrBlank() })
                } catch (_: Exception) {
                    emit(false)
                }
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

In `approve()`, replace the claims section (lines 171-187):

```kotlin
                // Build shared claims from global profile
                val sharedClaims = mutableMapOf<String, String>()
                val claims = profileManager.getProfileClaims()

                val missingRequired = requestedClaims
                    .filter { it.required }
                    .filter { claims[it.key].isNullOrBlank() }
                if (missingRequired.isNotEmpty()) {
                    val missing = missingRequired.joinToString(", ") { it.key }
                    throw IllegalStateException("Missing required claims: $missing")
                }

                for (key in _selectedClaims.value) {
                    val value = claims[key]
                    if (value != null) sharedClaims[key] = value
                }
```

**Step 2: Update ConsentViewModelTest**

Add `ProfileManager` mock to setup:

```kotlin
private lateinit var profileManager: ProfileManager
```

In `setup()`:

```kotlin
profileManager = mockk()
```

Update `createViewModel` to pass `profileManager`:

```kotlin
return ConsentViewModel(vault, httpClient, verifier, biometricAuth, profileManager, handle)
```

Update `stubApproveFlow()` — replace `vault.getCredentialForDid` stubs with `profileManager.getProfileClaims`:

```kotlin
private fun stubApproveFlow() {
    coEvery { vault.sign(any(), any()) } returns Result.success(ByteArray(64))
    coEvery { profileManager.getProfileClaims() } returns mapOf(
        "name" to "Amir Rudin", "email" to "amir@example.com", "phone" to "+60123456789"
    )
    coEvery { serverApi.verifyAuth(any()) } returns AuthVerifyResponse(
        sessionToken = "tok-123",
        serverDid = "did:ssdid:server1",
        serverKeyId = "did:ssdid:server1#key-1",
        serverSignature = "uSig"
    )
    coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(true)
}
```

Update the `hasAllRequiredClaims` tests — replace `vault.getCredentialForDid` stubs with `profileManager.getProfileClaims`:

- `hasAllRequiredClaims is true when all required claims present` → `coEvery { profileManager.getProfileClaims() } returns mapOf("name" to "Alice", "email" to "alice@example.com")`
- `hasAllRequiredClaims is false when required claim missing` → `coEvery { profileManager.getProfileClaims() } returns mapOf("phone" to "+60123456789")`
- `hasAllRequiredClaims is false when credential is null` → `coEvery { profileManager.getProfileClaims() } returns emptyMap()`

Update the `approve fails when required claim is missing` test — replace `vault.getCredentialForDid` with:

```kotlin
coEvery { profileManager.getProfileClaims() } returns mapOf("phone" to "+60123456789")
```

Remove import of `VerifiableCredential`, `CredentialSubject`, `Proof` if no longer used.

Add import:

```kotlin
import my.ssdid.wallet.domain.profile.ProfileManager
```

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.feature.auth.ConsentViewModelTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt \
       app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt
git commit -m "refactor: use global profile for consent claims instead of per-identity credentials"
```

---

### Task 7: Hilt Module — Provide ProfileManager

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

**Step 1: Check if ProfileManager needs explicit Hilt binding**

`ProfileManager` has `@Inject constructor` — Hilt can create it automatically if `Vault` is already provided. Verify by compiling:

Run: `./gradlew :app:compileDebugKotlin`

If it compiles, no module change needed. If it fails with a missing binding, add to `AppModule.kt`:

```kotlin
@Provides
@Singleton
fun provideProfileManager(vault: Vault): ProfileManager = ProfileManager(vault)
```

**Step 2: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

**Step 3: Run lint**

Run: `./gradlew lint`
Expected: BUILD SUCCESSFUL

**Step 4: Commit (if module change was needed)**

```bash
git add app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "chore: add ProfileManager to Hilt DI module"
```

---

### Task 8: Final Verification

**Step 1: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass

**Step 2: Run lint**

Run: `./gradlew lint`
Expected: BUILD SUCCESSFUL — no lint errors

**Step 3: Verify the onboarding flow compiles end-to-end**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL
