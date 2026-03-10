# Sign In with SSDID Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable cross-platform "Sign in with SSDID" with selective claim disclosure, algorithm filtering, MFA proof, and mutual authentication.

**Architecture:** Extends the existing `authenticate` deep link/QR action. New DTOs + ServerApi endpoints for challenge/verify. New ConsentScreen with claim selection UI. DeepLinkHandler and QrScanner parse new fields. CreateIdentityScreen accepts algorithm filter. Native apps use callback URLs, web apps use server-side polling.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit 2, kotlinx-serialization, Coroutines/Flow

**Design Doc:** `docs/plans/2026-03-10-sign-in-with-ssdid-design.md`

---

## Task 1: Auth DTOs

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/transport/dto/AuthDtos.kt`
- Test: `app/src/test/java/my/ssdid/wallet/domain/transport/dto/AuthDtosTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.transport.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class AuthDtosTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `ClaimRequest serialization round trip`() {
        val claim = ClaimRequest(key = "email", required = true)
        val encoded = json.encodeToString(claim)
        val decoded = json.decodeFromString<ClaimRequest>(encoded)
        assertThat(decoded).isEqualTo(claim)
    }

    @Test
    fun `AuthChallengeResponse deserialization`() {
        val raw = """{"challenge":"abc123","server_name":"MyApp","server_did":"did:ssdid:s1","server_key_id":"did:ssdid:s1#key-1"}"""
        val resp = json.decodeFromString<AuthChallengeResponse>(raw)
        assertThat(resp.challenge).isEqualTo("abc123")
        assertThat(resp.server_name).isEqualTo("MyApp")
        assertThat(resp.server_did).isEqualTo("did:ssdid:s1")
    }

    @Test
    fun `AuthVerifyRequest serialization includes all fields`() {
        val req = AuthVerifyRequest(
            did = "did:ssdid:user1",
            key_id = "did:ssdid:user1#key-1",
            signed_challenge = "uSig123",
            shared_claims = mapOf("name" to "Amir", "email" to "a@b.com"),
            amr = listOf("hwk", "bio"),
            session_id = "sess-1"
        )
        val encoded = json.encodeToString(req)
        assertThat(encoded).contains("\"amr\":[\"hwk\",\"bio\"]")
        assertThat(encoded).contains("\"session_id\":\"sess-1\"")
    }

    @Test
    fun `AuthVerifyRequest session_id omitted when null`() {
        val req = AuthVerifyRequest(
            did = "did:ssdid:user1",
            key_id = "did:ssdid:user1#key-1",
            signed_challenge = "uSig123",
            shared_claims = emptyMap(),
            amr = listOf("hwk")
        )
        val encoded = json.encodeToString(req)
        assertThat(encoded).doesNotContain("session_id")
    }

    @Test
    fun `AuthVerifyResponse deserialization`() {
        val raw = """{"session_token":"tok-1","server_did":"did:ssdid:s1","server_key_id":"did:ssdid:s1#key-1","server_signature":"uSig"}"""
        val resp = json.decodeFromString<AuthVerifyResponse>(raw)
        assertThat(resp.session_token).isEqualTo("tok-1")
        assertThat(resp.server_signature).isEqualTo("uSig")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.AuthDtosTest" 2>&1 | tail -5`
Expected: FAIL — classes not found

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClaimRequest(
    val key: String,
    val required: Boolean = false
)

@Serializable
data class AuthChallengeResponse(
    val challenge: String,
    val server_name: String,
    val server_did: String,
    val server_key_id: String
)

@Serializable
data class AuthVerifyRequest(
    val did: String,
    val key_id: String,
    val signed_challenge: String,
    val shared_claims: Map<String, String>,
    val amr: List<String>,
    val session_id: String? = null
)

@Serializable
data class AuthVerifyResponse(
    val session_token: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.AuthDtosTest" 2>&1 | tail -5`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/transport/dto/AuthDtos.kt app/src/test/java/my/ssdid/wallet/domain/transport/dto/AuthDtosTest.kt
git commit -m "feat: add auth DTOs for Sign In with SSDID"
```

---

## Task 2: Claim Validator

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/auth/ClaimValidator.kt`
- Test: `app/src/test/java/my/ssdid/wallet/domain/auth/ClaimValidatorTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClaimValidatorTest {

    @Test
    fun `valid name passes`() {
        assertThat(ClaimValidator.validate("name", "Amir Rudin")).isNull()
    }

    @Test
    fun `empty name fails`() {
        assertThat(ClaimValidator.validate("name", "")).isNotNull()
    }

    @Test
    fun `name over 100 chars fails`() {
        assertThat(ClaimValidator.validate("name", "A".repeat(101))).isNotNull()
    }

    @Test
    fun `valid email passes`() {
        assertThat(ClaimValidator.validate("email", "amir@example.com")).isNull()
    }

    @Test
    fun `invalid email fails`() {
        assertThat(ClaimValidator.validate("email", "not-an-email")).isNotNull()
    }

    @Test
    fun `valid phone passes`() {
        assertThat(ClaimValidator.validate("phone", "+60123456789")).isNull()
    }

    @Test
    fun `phone without plus fails`() {
        assertThat(ClaimValidator.validate("phone", "60123456789")).isNotNull()
    }

    @Test
    fun `unknown claim key always passes`() {
        assertThat(ClaimValidator.validate("custom_field", "anything")).isNull()
    }

    @Test
    fun `isWellKnown returns true for known keys`() {
        assertThat(ClaimValidator.isWellKnown("name")).isTrue()
        assertThat(ClaimValidator.isWellKnown("email")).isTrue()
        assertThat(ClaimValidator.isWellKnown("phone")).isTrue()
    }

    @Test
    fun `isWellKnown returns false for unknown keys`() {
        assertThat(ClaimValidator.isWellKnown("address")).isFalse()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ClaimValidatorTest" 2>&1 | tail -5`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.auth

object ClaimValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^\\+[1-9]\\d{6,14}$")

    private val WELL_KNOWN_KEYS = setOf("name", "email", "phone")

    fun isWellKnown(key: String): Boolean = key in WELL_KNOWN_KEYS

    /**
     * Returns null if valid, or an error message if invalid.
     */
    fun validate(key: String, value: String): String? {
        return when (key) {
            "name" -> when {
                value.isBlank() -> "Name must not be empty"
                value.length > 100 -> "Name must be 100 characters or less"
                else -> null
            }
            "email" -> if (EMAIL_REGEX.matches(value)) null else "Invalid email format"
            "phone" -> if (PHONE_REGEX.matches(value)) null else "Phone must be E.164 format (e.g. +60123456789)"
            else -> null // Unknown claims pass through
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ClaimValidatorTest" 2>&1 | tail -5`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/auth/ClaimValidator.kt app/src/test/java/my/ssdid/wallet/domain/auth/ClaimValidatorTest.kt
git commit -m "feat: add claim validator for well-known claim keys"
```

---

## Task 3: ServerApi Auth Endpoints

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/domain/transport/ServerApi.kt`

**Step 1: Add new endpoints to ServerApi**

Add these two methods to the `ServerApi` interface (after line 20):

```kotlin
    @GET("api/auth/challenge")
    suspend fun getAuthChallenge(): AuthChallengeResponse

    @POST("api/auth/verify")
    suspend fun verifyAuth(@Body request: AuthVerifyRequest): AuthVerifyResponse
```

Add the import at the top of the file:

```kotlin
import my.ssdid.wallet.domain.transport.dto.AuthChallengeResponse
import my.ssdid.wallet.domain.transport.dto.AuthVerifyRequest
import my.ssdid.wallet.domain.transport.dto.AuthVerifyResponse
```

**Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/transport/ServerApi.kt
git commit -m "feat: add auth challenge and verify endpoints to ServerApi"
```

---

## Task 4: QR Payload + Deep Link Parsing

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/platform/scan/QrScanner.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`
- Test: `app/src/test/java/my/ssdid/wallet/platform/scan/QrScannerTest.kt` (modify existing or create)
- Test: `app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt` (modify existing or create)

**Step 1: Update QrPayload data class** (QrScanner.kt lines 8-16)

Add three new fields to `QrPayload`:

```kotlin
@Serializable
data class QrPayload(
    @SerialName("server_url") val serverUrl: String = "",
    @SerialName("server_did") val serverDid: String = "",
    val action: String,
    @SerialName("session_token") val sessionToken: String = "",
    @SerialName("issuer_url") val issuerUrl: String = "",
    @SerialName("offer_id") val offerId: String = "",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("callback_url") val callbackUrl: String = "",
    @SerialName("requested_claims") val requestedClaims: List<my.ssdid.wallet.domain.transport.dto.ClaimRequest> = emptyList(),
    @SerialName("accepted_algorithms") val acceptedAlgorithms: List<String> = emptyList()
)
```

**Step 2: Update DeepLinkAction** (DeepLinkHandler.kt lines 7-27)

Add three new fields:

```kotlin
data class DeepLinkAction(
    val action: String,
    val serverUrl: String,
    val serverDid: String = "",
    val sessionToken: String = "",
    val issuerUrl: String = "",
    val offerId: String = "",
    val callbackUrl: String = "",
    val sessionId: String = "",
    val requestedClaims: List<my.ssdid.wallet.domain.transport.dto.ClaimRequest> = emptyList(),
    val acceptedAlgorithms: List<String> = emptyList()
)
```

**Step 3: Update DeepLinkHandler.parse()** to extract new fields

In `DeepLinkHandler.parse()`, after extracting `callbackUrl`, add:

```kotlin
val sessionId = uri.getQueryParameter("session_id") ?: ""
val requestedClaimsJson = uri.getQueryParameter("requested_claims") ?: ""
val requestedClaims = if (requestedClaimsJson.isNotEmpty()) {
    try {
        kotlinx.serialization.json.Json.decodeFromString<List<my.ssdid.wallet.domain.transport.dto.ClaimRequest>>(requestedClaimsJson)
    } catch (_: Exception) { emptyList() }
} else emptyList()
val acceptedAlgorithmsJson = uri.getQueryParameter("accepted_algorithms") ?: ""
val acceptedAlgorithms = if (acceptedAlgorithmsJson.isNotEmpty()) {
    try {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(acceptedAlgorithmsJson)
    } catch (_: Exception) { emptyList() }
} else emptyList()
```

Include these in the returned `DeepLinkAction`.

**Step 4: Update DeepLinkAction.toNavRoute()** for authenticate action

When `action == "authenticate"`, encode the new fields into the navigation route. Update `Screen.AuthFlow.createRoute()` in the next task to accept them.

**Step 5: Write test for QR payload parsing**

```kotlin
package my.ssdid.wallet.platform.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrScannerAuthTest {

    @Test
    fun `parsePayload with requested_claims and accepted_algorithms`() {
        val raw = """{"action":"authenticate","server_url":"https://app.example.com","session_id":"abc-123","requested_claims":[{"key":"name","required":true},{"key":"phone","required":false}],"accepted_algorithms":["ED25519","KAZ_SIGN_192"]}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.action).isEqualTo("authenticate")
        assertThat(payload.sessionId).isEqualTo("abc-123")
        assertThat(payload.requestedClaims).hasSize(2)
        assertThat(payload.requestedClaims[0].key).isEqualTo("name")
        assertThat(payload.requestedClaims[0].required).isTrue()
        assertThat(payload.acceptedAlgorithms).containsExactly("ED25519", "KAZ_SIGN_192")
    }

    @Test
    fun `parsePayload without optional fields still works`() {
        val raw = """{"action":"authenticate","server_url":"https://app.example.com"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.requestedClaims).isEmpty()
        assertThat(payload.acceptedAlgorithms).isEmpty()
        assertThat(payload.sessionId).isEmpty()
    }
}
```

**Step 6: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.QrScannerAuthTest" 2>&1 | tail -5`
Expected: PASS

**Step 7: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/scan/QrScanner.kt app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt app/src/test/java/my/ssdid/wallet/platform/scan/QrScannerAuthTest.kt
git commit -m "feat: parse requested_claims, session_id, accepted_algorithms in QR and deep links"
```

---

## Task 5: Navigation — Screen + NavGraph

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add Screen.Consent to Screen.kt**

After `Screen.AuthFlow` (around line 21), add:

```kotlin
    object Consent : Screen("consent?serverUrl={serverUrl}&callbackUrl={callbackUrl}&sessionId={sessionId}&requestedClaims={requestedClaims}&acceptedAlgorithms={acceptedAlgorithms}") {
        fun createRoute(
            serverUrl: String,
            callbackUrl: String = "",
            sessionId: String = "",
            requestedClaims: String = "",
            acceptedAlgorithms: String = ""
        ): String = "consent?serverUrl=${Uri.encode(serverUrl)}&callbackUrl=${Uri.encode(callbackUrl)}&sessionId=${Uri.encode(sessionId)}&requestedClaims=${Uri.encode(requestedClaims)}&acceptedAlgorithms=${Uri.encode(acceptedAlgorithms)}"
    }
```

**Step 2: Add composable route in NavGraph.kt**

After the AuthFlow composable block, add:

```kotlin
        composable(
            route = Screen.Consent.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("callbackUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("requestedClaims") { type = NavType.StringType; defaultValue = "" },
                navArgument("acceptedAlgorithms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            ConsentScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.WalletHome.route) { inclusive = true }
                    }
                },
                onCreateIdentity = { acceptedAlgos ->
                    navController.navigate(Screen.CreateIdentity.createRoute(acceptedAlgos))
                }
            )
        }
```

Add import: `import my.ssdid.wallet.feature.auth.ConsentScreen`

**Step 3: Update ScanQrScreen routing**

In `ScanQrScreen.kt`, where `"authenticate"` action is handled (the `when` block), update to navigate to Consent instead of AuthFlow when `requestedClaims` is present:

```kotlin
"authenticate" -> {
    if (payload.requestedClaims.isNotEmpty()) {
        val claimsJson = Json.encodeToString(payload.requestedClaims)
        val algosJson = Json.encodeToString(payload.acceptedAlgorithms)
        navController.navigate(
            Screen.Consent.createRoute(
                serverUrl = payload.serverUrl,
                callbackUrl = payload.callbackUrl,
                sessionId = payload.sessionId,
                requestedClaims = claimsJson,
                acceptedAlgorithms = algosJson
            )
        )
    } else {
        navController.navigate(Screen.AuthFlow.createRoute(payload.serverUrl))
    }
}
```

**Step 4: Update CreateIdentityScreen route to accept algorithm filter**

In `Screen.kt`, update `CreateIdentity`:

```kotlin
    object CreateIdentity : Screen("create_identity?acceptedAlgorithms={acceptedAlgorithms}") {
        fun createRoute(acceptedAlgorithms: String = ""): String =
            "create_identity?acceptedAlgorithms=${Uri.encode(acceptedAlgorithms)}"
    }
```

Update the NavGraph composable for CreateIdentity to pass the argument:

```kotlin
        composable(
            route = Screen.CreateIdentity.route,
            arguments = listOf(
                navArgument("acceptedAlgorithms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            CreateIdentityScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.WalletHome.route) { inclusive = true }
                    }
                }
            )
        }
```

**Step 5: Verify it compiles** (ConsentScreen will be a stub at this point)

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (may need a placeholder ConsentScreen composable)

**Step 6: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt app/src/main/java/my/ssdid/wallet/feature/scan/ScanQrScreen.kt
git commit -m "feat: add Consent screen navigation route and QR routing logic"
```

---

## Task 6: CreateIdentityScreen Algorithm Filter

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/identity/CreateIdentityScreen.kt`

**Step 1: Read acceptedAlgorithms from SavedStateHandle in ViewModel**

Update `CreateIdentityViewModel` to accept `SavedStateHandle` and parse the algorithm filter:

```kotlin
@HiltViewModel
class CreateIdentityViewModel @Inject constructor(
    private val client: SsdidClient,
    private val storage: VaultStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val acceptedAlgorithms: List<Algorithm> = run {
        val raw = savedStateHandle.get<String>("acceptedAlgorithms") ?: ""
        if (raw.isBlank()) Algorithm.entries.toList()
        else {
            val names = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(raw)
            } catch (_: Exception) { emptyList() }
            if (names.isEmpty()) Algorithm.entries.toList()
            else Algorithm.entries.filter { it.name in names }
        }
    }
    // ... rest unchanged
}
```

Add import: `import androidx.lifecycle.SavedStateHandle`

**Step 2: Filter algorithm list in the UI**

In `CreateIdentityScreen`, replace `Algorithm.entries.forEach` (line 111) with:

```kotlin
viewModel.acceptedAlgorithms.forEach { algo ->
```

And set default selection to first accepted:

```kotlin
var selectedAlgo by remember { mutableStateOf(viewModel.acceptedAlgorithms.first()) }
```

**Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/identity/CreateIdentityScreen.kt
git commit -m "feat: filter algorithms in CreateIdentityScreen from accepted_algorithms param"
```

---

## Task 7: ConsentScreen ViewModel

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt`
- Test: `app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.ServerApi
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.AuthChallengeResponse
import my.ssdid.wallet.domain.transport.dto.AuthVerifyResponse
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {

    private lateinit var vault: Vault
    private lateinit var httpClient: SsdidHttpClient
    private lateinit var serverApi: ServerApi
    private lateinit var verifier: Verifier
    private lateinit var biometricAuth: BiometricAuthenticator

    private val testIdentity = Identity(
        name = "Personal",
        did = "did:ssdid:user1",
        keyId = "did:ssdid:user1#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPubKey",
        createdAt = "2026-03-10T00:00:00Z"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        vault = mockk()
        httpClient = mockk()
        serverApi = mockk()
        verifier = mockk()
        biometricAuth = mockk()
        every { httpClient.serverApi(any()) } returns serverApi
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        serverUrl: String = "https://app.example.com",
        requestedClaims: String = """[{"key":"name","required":true},{"key":"phone","required":false}]""",
        acceptedAlgorithms: String = "",
        callbackUrl: String = "",
        sessionId: String = ""
    ): ConsentViewModel {
        val handle = SavedStateHandle(mapOf(
            "serverUrl" to serverUrl,
            "requestedClaims" to requestedClaims,
            "acceptedAlgorithms" to acceptedAlgorithms,
            "callbackUrl" to callbackUrl,
            "sessionId" to sessionId
        ))
        return ConsentViewModel(vault, httpClient, verifier, biometricAuth, handle)
    }

    @Test
    fun `parses requested claims from saved state`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.requestedClaims).hasSize(2)
        assertThat(vm.requestedClaims[0].key).isEqualTo("name")
        assertThat(vm.requestedClaims[0].required).isTrue()
    }

    @Test
    fun `filters identities by accepted algorithms`() = runTest {
        val pqcIdentity = testIdentity.copy(
            name = "PQC", did = "did:ssdid:user2",
            keyId = "did:ssdid:user2#key-1", algorithm = Algorithm.KAZ_SIGN_192
        )
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        val vm = createViewModel(acceptedAlgorithms = """["KAZ_SIGN_192"]""")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(1)
        assertThat(vm.identities.value[0].algorithm).isEqualTo(Algorithm.KAZ_SIGN_192)
    }

    @Test
    fun `no algorithm filter shows all identities`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        val vm = createViewModel(acceptedAlgorithms = "")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(1)
    }

    @Test
    fun `toggleClaim toggles optional claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        val vm = createViewModel()
        advanceUntilIdle()
        // phone (index 1) is optional, starts checked
        assertThat(vm.selectedClaims.value).containsKey("phone")
        vm.toggleClaim("phone")
        assertThat(vm.selectedClaims.value).doesNotContainKey("phone")
    }

    @Test
    fun `toggleClaim does not toggle required claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleClaim("name") // required — should not toggle
        assertThat(vm.selectedClaims.value).containsKey("name")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ConsentViewModelTest" 2>&1 | tail -5`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.feature.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.AuthVerifyRequest
import my.ssdid.wallet.domain.transport.dto.ClaimRequest
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import javax.inject.Inject

sealed class ConsentState {
    object Loading : ConsentState()
    object Ready : ConsentState()
    object Submitting : ConsentState()
    data class Success(val sessionToken: String = "") : ConsentState()
    data class Error(val message: String) : ConsentState()
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val verifier: Verifier,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val callbackUrl: String = savedStateHandle["callbackUrl"] ?: ""
    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val hasCallback: Boolean get() = callbackUrl.isNotEmpty()
    val isWebFlow: Boolean get() = sessionId.isNotEmpty()

    val requestedClaims: List<ClaimRequest> = run {
        val raw = savedStateHandle.get<String>("requestedClaims") ?: ""
        if (raw.isBlank()) emptyList()
        else try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private val acceptedAlgorithmNames: List<String> = run {
        val raw = savedStateHandle.get<String>("acceptedAlgorithms") ?: ""
        if (raw.isBlank()) emptyList()
        else try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private val _state = MutableStateFlow<ConsentState>(ConsentState.Loading)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    private val _selectedClaims = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val selectedClaims = _selectedClaims.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName = _serverName.asStateFlow()

    private var challenge: String = ""

    init {
        viewModelScope.launch {
            val allIdentities = vault.listIdentities()
            val filtered = if (acceptedAlgorithmNames.isEmpty()) allIdentities
                else allIdentities.filter { it.algorithm.name in acceptedAlgorithmNames }
            _identities.value = filtered
            if (filtered.isNotEmpty()) _selectedIdentity.value = filtered.first()

            // Initialize all claims as selected
            _selectedClaims.value = requestedClaims.associate { it.key to true }

            _state.value = ConsentState.Ready
        }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
    }

    fun toggleClaim(key: String) {
        val claim = requestedClaims.find { it.key == key } ?: return
        if (claim.required) return // Cannot toggle required claims
        val current = _selectedClaims.value.toMutableMap()
        current[key] = !(current[key] ?: true)
        if (current[key] == false) current.remove(key) else current[key] = true
        _selectedClaims.value = current
    }

    fun fetchChallenge() {
        viewModelScope.launch {
            _state.value = ConsentState.Submitting
            try {
                val serverApi = httpClient.serverApi(serverUrl)
                val resp = serverApi.getAuthChallenge()
                challenge = resp.challenge
                _serverName.value = resp.server_name
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.value = ConsentState.Error(e.message ?: "Failed to connect to service")
            }
        }
    }

    suspend fun requireBiometric(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return when (biometricAuth.authenticate(activity)) {
            is BiometricResult.Success -> true
            else -> false
        }
    }

    fun approve(biometricUsed: Boolean) {
        val identity = _selectedIdentity.value ?: return
        viewModelScope.launch {
            _state.value = ConsentState.Submitting
            try {
                val serverApi = httpClient.serverApi(serverUrl)

                // Fetch challenge if not already fetched
                if (challenge.isEmpty()) {
                    val challengeResp = serverApi.getAuthChallenge()
                    challenge = challengeResp.challenge
                    _serverName.value = challengeResp.server_name
                }

                // Sign the challenge
                val signatureBytes = vault.sign(identity.keyId, challenge.toByteArray()).getOrThrow()
                val signedChallenge = Multibase.encode(signatureBytes)

                // Build shared claims from selected
                val sharedClaims = mutableMapOf<String, String>()
                val credential = vault.getCredentialForDid(identity.did)
                val claims = credential?.credentialSubject?.claims ?: emptyMap()
                for ((key, selected) in _selectedClaims.value) {
                    if (selected && claims.containsKey(key)) {
                        sharedClaims[key] = claims[key]!!
                    }
                }

                // Build AMR
                val amr = mutableListOf("hwk")
                if (biometricUsed) amr.add("bio")

                // Verify with service
                val resp = serverApi.verifyAuth(
                    AuthVerifyRequest(
                        did = identity.did,
                        key_id = identity.keyId,
                        signed_challenge = signedChallenge,
                        shared_claims = sharedClaims,
                        amr = amr,
                        session_id = sessionId.ifEmpty { null }
                    )
                )

                // Mutual auth — verify server signature
                val verified = verifier.verifyChallengeResponse(
                    resp.server_did, resp.server_key_id,
                    resp.session_token, resp.server_signature
                ).getOrThrow()
                if (!verified) throw SecurityException("Service authentication failed")

                _state.value = ConsentState.Success(resp.session_token)
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e)
                _state.value = ConsentState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun buildCallbackUri(sessionToken: String): Uri? {
        if (callbackUrl.isEmpty()) return null
        return Uri.parse(callbackUrl).buildUpon()
            .appendQueryParameter("session_token", sessionToken)
            .appendQueryParameter("did", _selectedIdentity.value?.did ?: "")
            .build()
    }

    fun buildDeclineCallbackUri(): Uri? {
        if (callbackUrl.isEmpty()) return null
        return Uri.parse(callbackUrl).buildUpon()
            .appendQueryParameter("error", "user_declined")
            .build()
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ConsentViewModelTest" 2>&1 | tail -5`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt
git commit -m "feat: add ConsentViewModel with claim selection and auth flow"
```

---

## Task 8: ConsentScreen UI

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/feature/auth/ConsentScreen.kt`

**Step 1: Write the ConsentScreen composable**

```kotlin
package my.ssdid.wallet.feature.auth

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import my.ssdid.wallet.ui.theme.*

@Composable
fun ConsentScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCreateIdentity: (String) -> Unit = {},
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val selectedIdentity by viewModel.selectedIdentity.collectAsState()
    val selectedClaims by viewModel.selectedClaims.collectAsState()
    val serverName by viewModel.serverName.collectAsState()

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Handle success — callback or complete
    LaunchedEffect(state) {
        if (state is ConsentState.Success) {
            val token = (state as ConsentState.Success).sessionToken
            if (viewModel.hasCallback) {
                val uri = viewModel.buildCallbackUri(token)
                if (uri != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
            onComplete()
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // Send decline callback if native flow
                if (viewModel.hasCallback) {
                    val uri = viewModel.buildDeclineCallbackUri()
                    if (uri != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }
                onBack()
            }) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(4.dp))
            Text("Sign In Request", style = MaterialTheme.typography.titleLarge)
        }

        // Error display
        if (state is ConsentState.Error) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DangerDim)
            ) {
                Text(
                    (state as ConsentState.Error).message,
                    modifier = Modifier.padding(12.dp),
                    color = Danger, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Service info card
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            serverName.ifEmpty { "Service" },
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(viewModel.serverUrl, fontSize = 12.sp, color = TextTertiary,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "wants to verify your identity and access the following:",
                            fontSize = 13.sp, color = TextSecondary
                        )
                    }
                }
            }

            // Identity selector
            item {
                Spacer(Modifier.height(8.dp))
                Text("IDENTITY", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (identities.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "No matching identity found",
                                style = MaterialTheme.typography.titleMedium, color = Danger
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This service requires a specific algorithm. Create a new identity to proceed.",
                                fontSize = 12.sp, color = TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onCreateIdentity("") },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Create New Identity")
                            }
                        }
                    }
                }
            } else {
                items(identities) { identity ->
                    val isSelected = selectedIdentity?.keyId == identity.keyId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentDim else BgCard
                        ),
                        onClick = { viewModel.selectIdentity(identity) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectIdentity(identity) },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(identity.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    identity.did.take(20) + "..." + identity.did.takeLast(8),
                                    fontSize = 11.sp, color = TextTertiary, fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    identity.algorithm.name.replace("_", " "),
                                    fontSize = 11.sp, color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }

            // Requested claims
            if (viewModel.requestedClaims.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("REQUESTED INFORMATION", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }

                items(viewModel.requestedClaims) { claim ->
                    val isChecked = selectedClaims[claim.key] == true
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleClaim(claim.key) },
                                enabled = !claim.required,
                                colors = CheckboxDefaults.colors(checkedColor = Accent)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    claim.key.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(if (claim.required) DangerDim else AccentDim)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (claim.required) "Required" else "Optional",
                                    fontSize = 10.sp,
                                    color = if (claim.required) Danger else Accent
                                )
                            }
                        }
                    }
                }
            }

            // MFA info
            item {
                Spacer(Modifier.height(8.dp))
                Text("AUTHENTICATION", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Biometric + Hardware Key", fontSize = 13.sp, color = TextPrimary)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Action buttons
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (activity != null) {
                        viewModel.viewModelScope.launch {
                            val bioUsed = viewModel.requireBiometric(activity)
                            if (bioUsed) viewModel.approve(biometricUsed = true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = identities.isNotEmpty() && state !is ConsentState.Submitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (state is ConsentState.Submitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Approve", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = {
                    if (viewModel.hasCallback) {
                        val uri = viewModel.buildDeclineCallbackUri()
                        if (uri != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", fontSize = 15.sp, color = TextPrimary)
            }
        }
    }
}
```

Note: This needs `import kotlinx.coroutines.launch` and `import androidx.lifecycle.viewModelScope` — or better, move the biometric + approve call into the ViewModel as a single `fun approveWithBiometric(activity)` method.

**Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/auth/ConsentScreen.kt
git commit -m "feat: add ConsentScreen UI with claim selection and identity picker"
```

---

## Task 9: Integration — Wire Deep Links to Consent

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt`

**Step 1: Update DeepLinkAction.toNavRoute() for authenticate**

In `DeepLinkAction.toNavRoute()`, when `action == "authenticate"` and `requestedClaims` is not empty, route to `Screen.Consent` instead of `Screen.AuthFlow`:

```kotlin
"authenticate" -> {
    if (requestedClaims.isNotEmpty()) {
        val claimsJson = kotlinx.serialization.json.Json.encodeToString(requestedClaims)
        val algosJson = if (acceptedAlgorithms.isNotEmpty())
            kotlinx.serialization.json.Json.encodeToString(acceptedAlgorithms) else ""
        Screen.Consent.createRoute(serverUrl, callbackUrl, sessionId, claimsJson, algosJson)
    } else {
        Screen.AuthFlow.createRoute(serverUrl, callbackUrl)
    }
}
```

**Step 2: Verify it compiles and existing tests pass**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 3: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt app/src/main/java/my/ssdid/wallet/feature/auth/AuthFlowScreen.kt
git commit -m "feat: route authenticate deep links with claims to ConsentScreen"
```

---

## Task 10: End-to-End Verification

**Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 2: Run lint**

Run: `./gradlew lint 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Manual test checklist**

On a device or emulator, test these scenarios:

- [ ] Scan QR with `requested_claims` — opens ConsentScreen
- [ ] Scan QR without `requested_claims` — opens existing AuthFlowScreen
- [ ] Deep link `ssdid://authenticate?server_url=...&requested_claims=[...]` — opens ConsentScreen
- [ ] Identity selector shows only matching algorithms when `accepted_algorithms` specified
- [ ] Required claims cannot be unchecked
- [ ] Optional claims can be toggled
- [ ] Approve triggers biometric then submits
- [ ] Decline sends callback with `error=user_declined` (native flow)
- [ ] No identities matching algorithm — shows "Create New Identity" button
- [ ] Error from server displayed in error card

**Step 4: Commit all remaining changes**

```bash
git add -A
git commit -m "feat: complete Sign In with SSDID implementation"
```
