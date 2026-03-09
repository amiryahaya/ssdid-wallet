# Credential Revocation Checking (Bitstring Status List) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Validate credential revocation status using W3C Bitstring Status List before credential use, with caching for offline support.

**Architecture:** New `RevocationManager` fetches and caches Bitstring Status List VCs from issuers. It parses the GZIP-compressed bitstring and checks the bit at the credential's `statusListIndex`. The check is wired into `SsdidClient.authenticate()` and the `CredentialDetailScreen` UI. A new `StatusListApi` Retrofit interface fetches status list credentials by URL.

**Tech Stack:** Kotlin, kotlinx-serialization, Retrofit 2, OkHttp, java.util.zip.GZIPInputStream, JUnit 4 + Mockk + Truth

---

### Task 1: StatusList Model & Bitstring Parser

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/revocation/StatusListCredential.kt`
- Create: `app/src/main/java/my/ssdid/wallet/domain/revocation/BitstringParser.kt`
- Test: `app/src/test/java/my/ssdid/wallet/domain/revocation/BitstringParserTest.kt`

**Step 1: Write failing tests for BitstringParser**

```kotlin
package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class BitstringParserTest {

    private fun makeEncodedList(bitCount: Int, revokedIndices: Set<Int>): String {
        val byteCount = (bitCount + 7) / 8
        val bytes = ByteArray(byteCount)
        for (idx in revokedIndices) {
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8) // MSB first
            bytes[bytePos] = (bytes[bytePos].toInt() or (1 shl bitPos)).toByte()
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    @Test
    fun `isRevoked returns true for revoked index`() {
        val encoded = makeEncodedList(128, setOf(42))
        assertThat(BitstringParser.isRevoked(encoded, 42)).isTrue()
    }

    @Test
    fun `isRevoked returns false for non-revoked index`() {
        val encoded = makeEncodedList(128, setOf(42))
        assertThat(BitstringParser.isRevoked(encoded, 43)).isFalse()
    }

    @Test
    fun `isRevoked handles multiple revoked indices`() {
        val encoded = makeEncodedList(256, setOf(0, 7, 8, 127, 255))
        assertThat(BitstringParser.isRevoked(encoded, 0)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 7)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 8)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 127)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 255)).isTrue()
        assertThat(BitstringParser.isRevoked(encoded, 1)).isFalse()
        assertThat(BitstringParser.isRevoked(encoded, 128)).isFalse()
    }

    @Test
    fun `isRevoked handles all-zeros bitstring`() {
        val encoded = makeEncodedList(128, emptySet())
        assertThat(BitstringParser.isRevoked(encoded, 0)).isFalse()
        assertThat(BitstringParser.isRevoked(encoded, 64)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `isRevoked throws for negative index`() {
        val encoded = makeEncodedList(128, emptySet())
        BitstringParser.isRevoked(encoded, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `isRevoked throws for index beyond bitstring`() {
        val encoded = makeEncodedList(8, emptySet()) // only 1 byte = 8 bits
        BitstringParser.isRevoked(encoded, 8)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.BitstringParserTest"`
Expected: FAIL (class not found)

**Step 3: Implement BitstringParser and StatusListCredential**

```kotlin
// StatusListCredential.kt
package my.ssdid.wallet.domain.revocation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.Proof

@Serializable
data class StatusListCredential(
    @SerialName("@context") val context: List<String> = emptyList(),
    val id: String? = null,
    val type: List<String>,
    val issuer: String,
    val credentialSubject: StatusListSubject,
    val proof: Proof? = null
)

@Serializable
data class StatusListSubject(
    val type: String,
    val statusPurpose: String,
    val encodedList: String
)
```

```kotlin
// BitstringParser.kt
package my.ssdid.wallet.domain.revocation

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

object BitstringParser {
    fun isRevoked(encodedList: String, index: Int): Boolean {
        require(index >= 0) { "Status list index must be non-negative: $index" }
        val compressed = Base64.getUrlDecoder().decode(encodedList)
        val bitstring = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val bytePos = index / 8
        require(bytePos < bitstring.size) { "Index $index out of range (bitstring has ${bitstring.size * 8} bits)" }
        val bitPos = 7 - (index % 8) // MSB first per W3C spec
        return (bitstring[bytePos].toInt() ushr bitPos) and 1 == 1
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.BitstringParserTest"`
Expected: PASS (6 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/revocation/ app/src/test/java/my/ssdid/wallet/domain/revocation/
git commit -m "feat(revocation): add BitstringParser and StatusListCredential model"
```

---

### Task 2: RevocationManager with HTTP Fetching

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/revocation/RevocationManager.kt`
- Test: `app/src/test/java/my/ssdid/wallet/domain/revocation/RevocationManagerTest.kt`

**Step 1: Write failing tests for RevocationManager**

```kotlin
package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class RevocationManagerTest {

    private lateinit var fetcher: StatusListFetcher
    private lateinit var manager: RevocationManager

    private fun makeEncodedList(revokedIndices: Set<Int>): String {
        val bytes = ByteArray(128) // 1024 bits
        for (idx in revokedIndices) {
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8)
            bytes[bytePos] = (bytes[bytePos].toInt() or (1 shl bitPos)).toByte()
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    private fun makeStatusListCredential(revokedIndices: Set<Int>): StatusListCredential {
        return StatusListCredential(
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList",
                statusPurpose = "revocation",
                encodedList = makeEncodedList(revokedIndices)
            )
        )
    }

    @Before
    fun setup() {
        fetcher = mockk()
        manager = RevocationManager(fetcher)
    }

    @Test
    fun `checkRevocation returns VALID for credential without status`() = runTest {
        val vc = VerifiableCredential(
            id = "urn:uuid:1", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC")
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.VALID)
    }

    @Test
    fun `checkRevocation returns REVOKED when bit is set`() = runTest {
        val statusListCred = makeStatusListCredential(setOf(42))
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val vc = VerifiableCredential(
            id = "urn:uuid:2", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#42",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "42",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = Proof(type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC")
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.REVOKED)
    }

    @Test
    fun `checkRevocation returns VALID when bit is not set`() = runTest {
        val statusListCred = makeStatusListCredential(setOf(42))
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val vc = VerifiableCredential(
            id = "urn:uuid:3", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#10",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "10",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = Proof(type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC")
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.VALID)
    }

    @Test
    fun `checkRevocation returns UNKNOWN when fetch fails`() = runTest {
        coEvery { fetcher.fetch(any()) } returns Result.failure(RuntimeException("network error"))

        val vc = VerifiableCredential(
            id = "urn:uuid:4", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#5",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "5",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = Proof(type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC")
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.UNKNOWN)
    }

    @Test
    fun `checkRevocation caches status list and reuses it`() = runTest {
        val statusListCred = makeStatusListCredential(emptySet())
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val status = CredentialStatus(
            id = "https://registry.example/status/1#5",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = "5",
            statusListCredential = "https://registry.example/status/1"
        )
        val proof = Proof(type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC")

        val vc1 = VerifiableCredential(id = "urn:uuid:a", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = status, proof = proof)
        val vc2 = VerifiableCredential(id = "urn:uuid:b", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = status, proof = proof)

        manager.checkRevocation(vc1)
        manager.checkRevocation(vc2)

        coVerify(exactly = 1) { fetcher.fetch(any()) }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RevocationManagerTest"`
Expected: FAIL (classes not found)

**Step 3: Implement RevocationManager**

```kotlin
package my.ssdid.wallet.domain.revocation

import my.ssdid.wallet.domain.model.VerifiableCredential
import java.util.concurrent.ConcurrentHashMap

enum class RevocationStatus { VALID, REVOKED, UNKNOWN }

fun interface StatusListFetcher {
    suspend fun fetch(url: String): Result<StatusListCredential>
}

class RevocationManager(private val fetcher: StatusListFetcher) {

    private val cache = ConcurrentHashMap<String, StatusListCredential>()

    suspend fun checkRevocation(credential: VerifiableCredential): RevocationStatus {
        val status = credential.credentialStatus ?: return RevocationStatus.VALID

        val listUrl = status.statusListCredential
        val index = status.statusListIndex.toIntOrNull()
            ?: return RevocationStatus.UNKNOWN

        val statusListCred = cache[listUrl] ?: run {
            val result = fetcher.fetch(listUrl)
            if (result.isFailure) return RevocationStatus.UNKNOWN
            result.getOrThrow().also { cache[listUrl] = it }
        }

        return try {
            if (BitstringParser.isRevoked(statusListCred.credentialSubject.encodedList, index))
                RevocationStatus.REVOKED
            else
                RevocationStatus.VALID
        } catch (_: Exception) {
            RevocationStatus.UNKNOWN
        }
    }

    fun invalidateCache() {
        cache.clear()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RevocationManagerTest"`
Expected: PASS (5 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/revocation/RevocationManager.kt app/src/test/java/my/ssdid/wallet/domain/revocation/RevocationManagerTest.kt
git commit -m "feat(revocation): add RevocationManager with caching and status list fetching"
```

---

### Task 3: HTTP StatusListFetcher & DI Wiring

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/revocation/HttpStatusListFetcher.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/di/AppModule.kt:37-144`
- Test: `app/src/test/java/my/ssdid/wallet/domain/revocation/HttpStatusListFetcherTest.kt`

**Step 1: Write failing test**

```kotlin
package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpStatusListFetcherTest {

    @Test
    fun `fetcher rejects non-https URLs`() {
        val fetcher = HttpStatusListFetcher(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        )
        val result = kotlinx.coroutines.test.runTest {
            fetcher.fetch("http://insecure.example/status/1")
        }
        // runTest returns Unit, need to use runBlocking pattern
    }
}
```

Actually, simpler approach — test URL validation directly:

```kotlin
package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class HttpStatusListFetcherTest {

    private val fetcher = HttpStatusListFetcher(Json { ignoreUnknownKeys = true })

    @Test
    fun `fetch rejects non-https URL`() = runTest {
        val result = fetcher.fetch("http://insecure.example/status/1")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTPS")
    }

    @Test
    fun `fetch rejects empty URL`() = runTest {
        val result = fetcher.fetch("")
        assertThat(result.isFailure).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.HttpStatusListFetcherTest"`
Expected: FAIL (class not found)

**Step 3: Implement HttpStatusListFetcher and wire DI**

```kotlin
// HttpStatusListFetcher.kt
package my.ssdid.wallet.domain.revocation

import kotlinx.serialization.json.Json
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HttpStatusListFetcher(private val json: Json) : StatusListFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String): Result<StatusListCredential> = runCatching {
        require(url.isNotEmpty()) { "Status list URL must not be empty" }
        val parsed = URL(url)
        require(parsed.protocol == "https") { "Status list URL must use HTTPS: $url" }

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        require(response.isSuccessful) { "Failed to fetch status list: HTTP ${response.code}" }
        val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
        json.decodeFromString<StatusListCredential>(body)
    }
}
```

Then add to `AppModule.kt` — add RevocationManager provider:

```kotlin
// Add after provideDeviceManager in AppModule.kt:

@Provides
@Singleton
fun provideRevocationManager(): RevocationManager {
    val json = Json { ignoreUnknownKeys = true }
    return RevocationManager(HttpStatusListFetcher(json))
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.HttpStatusListFetcherTest"`
Expected: PASS (2 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/revocation/HttpStatusListFetcher.kt app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git add app/src/test/java/my/ssdid/wallet/domain/revocation/HttpStatusListFetcherTest.kt
git commit -m "feat(revocation): add HttpStatusListFetcher and DI wiring"
```

---

### Task 4: Wire Revocation Check into SsdidClient.authenticate()

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt:23-27,147-163`
- Test: `app/src/test/java/my/ssdid/wallet/domain/SsdidClientTest.kt`

**Step 1: Write failing test**

Add to `SsdidClientTest.kt`:

```kotlin
// Add revocationManager mock in fields:
private lateinit var revocationManager: RevocationManager

// In setup():
revocationManager = mockk(relaxed = true)
client = SsdidClient(vault, verifier, httpClient, activityRepo, revocationManager)

// New test:
@Test
fun `authenticate fails when credential is revoked`() = runTest {
    coEvery { revocationManager.checkRevocation(testVc) } returns RevocationStatus.REVOKED

    val result = client.authenticate(testVc, "https://server.example.com")

    assertThat(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    assertThat(result.exceptionOrNull()?.message).contains("revoked")
}

@Test
fun `authenticate succeeds when credential is valid`() = runTest {
    coEvery { revocationManager.checkRevocation(testVc) } returns RevocationStatus.VALID
    // ... rest of existing successful auth test setup
    val authResp = AuthenticateResponse(
        session_token = "session-abc",
        server_did = "did:ssdid:server",
        server_key_id = "did:ssdid:server#key-1",
        server_signature = "uSessionSig"
    )
    coEvery { serverApi.authenticate(any()) } returns authResp
    coEvery { verifier.verifyChallengeResponse("did:ssdid:server", "did:ssdid:server#key-1", "session-abc", "uSessionSig") } returns Result.success(true)

    val result = client.authenticate(testVc, "https://server.example.com")
    assertThat(result.isSuccess).isTrue()
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SsdidClientTest"`
Expected: FAIL (constructor mismatch — SsdidClient doesn't take revocationManager yet)

**Step 3: Add revocation check to SsdidClient**

Modify `SsdidClient` constructor to accept `RevocationManager`:

```kotlin
class SsdidClient(
    private val vault: Vault,
    private val verifier: Verifier,
    private val httpClient: SsdidHttpClient,
    private val activityRepo: ActivityRepository,
    private val revocationManager: RevocationManager
)
```

Add revocation check at start of `authenticate()`:

```kotlin
suspend fun authenticate(credential: VerifiableCredential, serverUrl: String): Result<VerifiableCredential> = runCatching {
    // Check revocation status before presenting credential
    val revocationStatus = revocationManager.checkRevocation(credential)
    if (revocationStatus == RevocationStatus.REVOKED) {
        throw SecurityException("Credential has been revoked")
    }

    // ... existing auth flow unchanged
}
```

Update `AppModule.kt` to pass `RevocationManager` to `SsdidClient`:

```kotlin
@Provides
@Singleton
fun provideSsdidClient(
    vault: Vault,
    verifier: Verifier,
    httpClient: SsdidHttpClient,
    activityRepo: ActivityRepository,
    revocationManager: RevocationManager
): SsdidClient = SsdidClient(vault, verifier, httpClient, activityRepo, revocationManager)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SsdidClientTest"`
Expected: PASS (all existing + 2 new tests)

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git add app/src/test/java/my/ssdid/wallet/domain/SsdidClientTest.kt
git commit -m "feat(revocation): check credential revocation status before authentication"
```

---

### Task 5: Show Revocation Status in CredentialDetailScreen

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/credentials/CredentialDetailScreen.kt:32-57,120-135`

**Step 1: Add revocation state to CredentialDetailViewModel**

Add to the ViewModel class:

```kotlin
@HiltViewModel
class CredentialDetailViewModel @Inject constructor(
    private val vault: Vault,
    private val revocationManager: RevocationManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // ... existing fields ...

    private val _revocationStatus = MutableStateFlow<RevocationStatus?>(null)
    val revocationStatus = _revocationStatus.asStateFlow()

    init {
        viewModelScope.launch {
            val vc = vault.listCredentials().find { it.id == credentialId }
            _credential.value = vc
            if (vc != null) {
                _revocationStatus.value = revocationManager.checkRevocation(vc)
            }
        }
    }
    // ... rest unchanged
}
```

**Step 2: Update the status badge in CredentialDetailScreen**

Replace the expiration-only badge (lines 120-135) with a combined status check:

```kotlin
val revocationStatus by viewModel.revocationStatus.collectAsState()

// Replace the isExpired-only badge with:
val isExpired = vc.expirationDate?.let {
    try { Instant.now().isAfter(Instant.parse(it)) } catch (_: Exception) { false }
} ?: false
val isRevoked = revocationStatus == RevocationStatus.REVOKED
val statusUnknown = revocationStatus == RevocationStatus.UNKNOWN

val (statusText, statusColor, statusBg) = when {
    isRevoked -> Triple("Revoked", Danger, DangerDim)
    isExpired -> Triple("Expired", Danger, DangerDim)
    statusUnknown -> Triple("Status Unknown", Warning, WarningDim)
    else -> Triple("Valid", Success, SuccessDim)
}

Box(
    Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(statusBg)
        .padding(horizontal = 10.dp, vertical = 3.dp)
) {
    Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
}
```

**Step 3: Add Warning color to theme if missing**

Check `app/src/main/java/my/ssdid/wallet/ui/theme/Color.kt` — add if needed:

```kotlin
val Warning = Color(0xFFF59E0B)
val WarningDim = Color(0x1AF59E0B)
```

**Step 4: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/credentials/CredentialDetailScreen.kt
git add app/src/main/java/my/ssdid/wallet/ui/theme/Color.kt
git commit -m "feat(revocation): display revocation status in credential detail screen"
```

---

### Task 6: Final Integration Test & Cleanup

**Step 1: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS

**Step 2: Run lint**

Run: `./gradlew lint`
Expected: No new errors

**Step 3: Verify compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Final commit if needed, then push**

```bash
git push -u origin feat/phase2-production-hardening
```
