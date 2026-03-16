# Identity-Scoped Profiles Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move profile data (name, email) from a global ProfileManager into per-identity scope so each identity has its own name/email.

**Architecture:** Add `profileName: String?`, `email: String?`, and `emailVerified: Bool` fields to the `Identity` model on both platforms. The existing `name` field stays as the identity label (e.g., "Work"). The new `profileName` holds the user's real name (e.g., "Amir Yahaya"). All consumers of `ProfileManager.getProfileClaims()` will read from the selected identity's profile fields instead. ProfileManager and its global `urn:ssdid:profile` VC are removed. A one-time migration copies existing profile data to the user's first identity.

**Tech Stack:** Kotlin (Android, kotlinx-serialization, Hilt, Compose), Swift (iOS, Codable, SwiftUI)

**Key design decision — name vs profileName:**
- `Identity.name` = identity label (e.g., "Work", "Personal") — used in the wallet UI
- `Identity.profileName` = user's display name (e.g., "Amir Yahaya") — shared as the `name` claim with services
- `Identity.email` = user's email for this identity — shared as the `email` claim with services

---

## Chunk 1: Data Model + Storage (Android)

### Task 1: Add profile fields to Android Identity model

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/model/IdentityProfileTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// File: android/app/src/test/java/my/ssdid/wallet/domain/model/IdentityProfileTest.kt
package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class IdentityProfileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `identity serializes with profileName, email and emailVerified fields`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir Yahaya",
            email = "amir@acme.com",
            emailVerified = true
        )
        val encoded = json.encodeToString(identity)
        assertThat(encoded).contains("\"profileName\":\"Amir Yahaya\"")
        assertThat(encoded).contains("\"email\":\"amir@acme.com\"")
        assertThat(encoded).contains("\"emailVerified\":true")

        val decoded = json.decodeFromString<Identity>(encoded)
        assertThat(decoded.profileName).isEqualTo("Amir Yahaya")
        assertThat(decoded.email).isEqualTo("amir@acme.com")
        assertThat(decoded.emailVerified).isTrue()
    }

    @Test
    fun `identity without profile fields deserializes with null defaults`() {
        val jsonStr = """{"name":"Work","did":"did:ssdid:abc","keyId":"did:ssdid:abc#key-1","algorithm":"ED25519","publicKeyMultibase":"z6Mk...","createdAt":"2026-03-16T00:00:00Z"}"""
        val decoded = json.decodeFromString<Identity>(jsonStr)
        assertThat(decoded.profileName).isNull()
        assertThat(decoded.email).isNull()
        assertThat(decoded.emailVerified).isFalse()
    }

    @Test
    fun `identityClaimsMap returns profile name as name claim`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir Yahaya",
            email = "amir@acme.com"
        )
        val claims = identity.claimsMap()
        assertThat(claims["name"]).isEqualTo("Amir Yahaya")
        assertThat(claims["email"]).isEqualTo("amir@acme.com")
    }

    @Test
    fun `identityClaimsMap omits null fields`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z"
        )
        val claims = identity.claimsMap()
        assertThat(claims).doesNotContainKey("name")
        assertThat(claims).doesNotContainKey("email")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.model.IdentityProfileTest" --info`
Expected: FAIL — `profileName`, `email`, `emailVerified`, and `claimsMap()` don't exist on Identity.

- [ ] **Step 3: Add profile fields and claimsMap to Identity**

```kotlin
// File: android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt
package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val name: String,
    val did: String,
    val keyId: String,
    val algorithm: Algorithm,
    val publicKeyMultibase: String,
    val createdAt: String,
    val isActive: Boolean = true,
    val recoveryKeyId: String? = null,
    val hasRecoveryKey: Boolean = false,
    val preRotatedKeyId: String? = null,
    val profileName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false
) {
    /** Returns a claims map suitable for sharing with services (e.g., shared_claims). */
    fun claimsMap(): Map<String, String> = buildMap {
        profileName?.let { put("name", it) }
        email?.let { put("email", it) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.model.IdentityProfileTest" --info`
Expected: PASS

- [ ] **Step 5: Run all existing tests to verify no regressions**

Run: `cd android && ./gradlew :app:testDebugUnitTest --info`
Expected: All tests PASS. Existing Identity usages still compile because new fields have defaults.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt android/app/src/test/java/my/ssdid/wallet/domain/model/IdentityProfileTest.kt
git commit -m "feat(android): add profileName, email, emailVerified fields and claimsMap to Identity"
```

---

### Task 2: Add sharedClaims to Android RegisterVerifyRequest

The Android `RegisterVerifyRequest` in `ServerDtos.kt` is missing the `sharedClaims` and `inviteToken` fields that the iOS version already has. These are needed for DriveLoginViewModel and InviteAcceptViewModel.

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/ServerDtos.kt:21-25`

- [ ] **Step 1: Add sharedClaims and inviteToken fields**

```kotlin
@Serializable
data class RegisterVerifyRequest(
    val did: String,
    val key_id: String,
    val signed_challenge: String,
    val invite_token: String? = null,
    val shared_claims: Map<String, String>? = null
)
```

- [ ] **Step 2: Verify compilation**

Run: `cd android && ./gradlew compileDebugKotlin --info`
Expected: PASS (new fields have defaults so existing callers compile)

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/ServerDtos.kt
git commit -m "feat(android): add sharedClaims and inviteToken to RegisterVerifyRequest"
```

---

### Task 3: Add Vault methods for updating identity profile (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultUpdateProfileTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// File: android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultUpdateProfileTest.kt
package my.ssdid.wallet.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import org.junit.Before
import org.junit.Test

class VaultUpdateProfileTest {

    private lateinit var storage: VaultStorage
    private lateinit var vault: VaultImpl

    @Before
    fun setup() {
        storage = mockk(relaxed = true)
        val classicalProvider = mockk<my.ssdid.wallet.domain.crypto.CryptoProvider>()
        val pqcProvider = mockk<my.ssdid.wallet.domain.crypto.CryptoProvider>()
        val keystoreManager = mockk<KeystoreManager>()
        vault = VaultImpl(classicalProvider, pqcProvider, keystoreManager, storage)
    }

    @Test
    fun `updateIdentityProfile updates profileName and email`() = runTest {
        val identity = Identity(
            name = "Work", did = "did:ssdid:abc", keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z"
        )
        coEvery { storage.getIdentity("did:ssdid:abc#key-1") } returns identity
        coEvery { storage.getEncryptedPrivateKey("did:ssdid:abc#key-1") } returns ByteArray(32)

        val result = vault.updateIdentityProfile(
            "did:ssdid:abc#key-1",
            profileName = "Amir Yahaya",
            email = "amir@acme.com"
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            storage.saveIdentity(match {
                it.profileName == "Amir Yahaya" && it.email == "amir@acme.com" && it.keyId == "did:ssdid:abc#key-1"
            }, any())
        }
    }

    @Test
    fun `updateIdentityProfile preserves existing fields when nulls passed`() = runTest {
        val identity = Identity(
            name = "Work", did = "did:ssdid:abc", keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir", email = "amir@acme.com"
        )
        coEvery { storage.getIdentity("did:ssdid:abc#key-1") } returns identity
        coEvery { storage.getEncryptedPrivateKey("did:ssdid:abc#key-1") } returns ByteArray(32)

        val result = vault.updateIdentityProfile("did:ssdid:abc#key-1", email = "new@acme.com")

        assertThat(result.isSuccess).isTrue()
        coVerify {
            storage.saveIdentity(match {
                it.profileName == "Amir" && it.email == "new@acme.com"
            }, any())
        }
    }

    @Test
    fun `updateIdentityProfile fails for nonexistent identity`() = runTest {
        coEvery { storage.getIdentity("missing") } returns null

        val result = vault.updateIdentityProfile("missing", email = "x@y.com")

        assertThat(result.isFailure).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.vault.VaultUpdateProfileTest" --info`
Expected: FAIL — `updateIdentityProfile` doesn't exist.

- [ ] **Step 3: Add updateIdentityProfile to Vault interface and VaultImpl**

In `Vault.kt`, add after the `deleteIdentity` method:

```kotlin
suspend fun updateIdentityProfile(
    keyId: String,
    profileName: String? = null,
    email: String? = null,
    emailVerified: Boolean? = null
): Result<Unit>
```

In `VaultImpl.kt`, add the implementation:

```kotlin
override suspend fun updateIdentityProfile(
    keyId: String,
    profileName: String?,
    email: String?,
    emailVerified: Boolean?
): Result<Unit> = runCatching {
    val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
    val encryptedKey = storage.getEncryptedPrivateKey(keyId)
        ?: throw IllegalStateException("Private key not found for: $keyId")
    val updated = identity.copy(
        profileName = profileName ?: identity.profileName,
        email = email ?: identity.email,
        emailVerified = emailVerified ?: identity.emailVerified
    )
    storage.saveIdentity(updated, encryptedKey)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.vault.VaultUpdateProfileTest" --info`
Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --info`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultUpdateProfileTest.kt
git commit -m "feat(android): add updateIdentityProfile to Vault for per-identity profile"
```

---

### Task 4: Migrate global profile to first identity (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileMigration.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/profile/ProfileMigrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// File: android/app/src/test/java/my/ssdid/wallet/domain/profile/ProfileMigrationTest.kt
package my.ssdid.wallet.domain.profile

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class ProfileMigrationTest {

    private lateinit var vault: Vault
    private lateinit var migration: ProfileMigration

    @Before
    fun setup() {
        vault = mockk(relaxed = true)
        migration = ProfileMigration(vault)
    }

    private fun profileVc(name: String = "Amir", email: String = "amir@acme.com") = VerifiableCredential(
        id = "urn:ssdid:profile",
        type = listOf("VerifiableCredential", "ProfileCredential"),
        issuer = "did:ssdid:self",
        issuanceDate = "2026-03-16T00:00:00Z",
        credentialSubject = CredentialSubject(
            id = "did:ssdid:self",
            claims = mapOf("name" to name, "email" to email)
        ),
        proof = Proof(type = "SelfIssued2024", created = "2026-03-16T00:00:00Z",
            verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
    )

    private fun identity(keyId: String = "did:ssdid:abc#key-1", email: String? = null, profileName: String? = null) = Identity(
        name = "Work", did = "did:ssdid:abc", keyId = keyId,
        algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
        createdAt = "2026-03-16T00:00:00Z", profileName = profileName, email = email
    )

    @Test
    fun `migrate copies name and email to first identity and deletes profile VC`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(identity())
        coEvery { vault.updateIdentityProfile(any(), profileName = any(), email = any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        coVerify { vault.updateIdentityProfile("did:ssdid:abc#key-1", profileName = "Amir", email = "amir@acme.com") }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }

    @Test
    fun `migrate does nothing when no profile VC exists`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()

        migration.migrateIfNeeded()

        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify(exactly = 0) { vault.deleteCredential(any()) }
    }

    @Test
    fun `migrate skips identity that already has email but still deletes profile VC`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(identity(email = "amir@acme.com", profileName = "Amir"))
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }

    @Test
    fun `migrate preserves profile VC when no identities exist`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns emptyList()

        migration.migrateIfNeeded()

        // Profile VC preserved — no identity to migrate to, user's data should not be lost
        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify(exactly = 0) { vault.deleteCredential(any()) }
    }

    @Test
    fun `migrate with multiple identities only updates first without email`() = runTest {
        val id1 = identity(keyId = "did:ssdid:abc#key-1", email = "existing@acme.com", profileName = "Existing")
        val id2 = identity(keyId = "did:ssdid:def#key-1")

        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(id1, id2)
        coEvery { vault.updateIdentityProfile(any(), profileName = any(), email = any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        // Should update id2 (first without email), not id1
        coVerify { vault.updateIdentityProfile("did:ssdid:def#key-1", profileName = "Amir", email = "amir@acme.com") }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.profile.ProfileMigrationTest" --info`
Expected: FAIL — `ProfileMigration` doesn't exist.

- [ ] **Step 3: Implement ProfileMigration**

```kotlin
// File: android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileMigration.kt
package my.ssdid.wallet.domain.profile

import my.ssdid.wallet.domain.vault.Vault
import javax.inject.Inject

class ProfileMigration @Inject constructor(private val vault: Vault) {

    companion object {
        private const val LEGACY_PROFILE_ID = "urn:ssdid:profile"
    }

    suspend fun migrateIfNeeded() {
        val profileVc = vault.listCredentials().find { it.id == LEGACY_PROFILE_ID }
            ?: return

        val profileName = profileVc.credentialSubject.claims["name"]
        val email = profileVc.credentialSubject.claims["email"]
        val identities = vault.listIdentities()

        // If no identities, preserve the profile VC so data isn't lost
        if (identities.isEmpty()) return

        // Copy profile to the first identity that doesn't already have email
        val target = identities.firstOrNull { it.email.isNullOrBlank() }
        if (target != null && (!email.isNullOrBlank() || !profileName.isNullOrBlank())) {
            vault.updateIdentityProfile(
                target.keyId,
                profileName = profileName,
                email = email
            )
        }

        // Delete the legacy profile VC
        vault.deleteCredential(LEGACY_PROFILE_ID)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.profile.ProfileMigrationTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileMigration.kt android/app/src/test/java/my/ssdid/wallet/domain/profile/ProfileMigrationTest.kt
git commit -m "feat(android): add ProfileMigration to move global profile to first identity"
```

---

## Chunk 2: Update Consumers (Android)

### Task 5: Update ConsentViewModel to use identity claims (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt`
- Modify: `android/app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt`

- [ ] **Step 1: Remove ProfileManager and use identity.claimsMap()**

In `ConsentViewModel.kt`:

1. Remove `private val profileManager: ProfileManager` from the constructor parameter list.
2. Remove the `import my.ssdid.wallet.domain.profile.ProfileManager` line.

3. Replace the `hasAllRequiredClaims` StateFlow (lines 94-112) — change `profileManager.getProfileClaims()` to `identity.claimsMap()`:

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
                val claims = identity.claimsMap()
                emit(requiredKeys.all { !claims[it].isNullOrBlank() })
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

4. In `approve()` (lines 178-193), replace profile claims with identity claims:

```kotlin
// Build shared claims from selected identity
val sharedClaims = mutableMapOf<String, String>()
val claims = identity.claimsMap()

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

- [ ] **Step 2: Update ConsentViewModelTest**

In `ConsentViewModelTest.kt`: remove all `profileManager` mocking. Instead, create test identities with `profileName` and `email` set:

```kotlin
val testIdentity = Identity(
    name = "Work", did = "did:ssdid:abc", keyId = "did:ssdid:abc#key-1",
    algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
    createdAt = "2026-03-16T00:00:00Z",
    profileName = "Alice", email = "alice@example.com"
)
```

- [ ] **Step 3: Verify compilation and run tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.ConsentViewModelTest" --info`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt android/app/src/test/java/my/ssdid/wallet/feature/auth/ConsentViewModelTest.kt
git commit -m "refactor(android): ConsentViewModel reads claims from identity.claimsMap()"
```

---

### Task 6: Update DriveLoginViewModel to use identity claims (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/auth/DriveLoginViewModel.kt`

- [ ] **Step 1: Remove ProfileManager and use identity.claimsMap()**

In `DriveLoginViewModel.kt`:

1. Remove `private val profileManager: ProfileManager` from constructor.
2. Remove the `ProfileManager` import.
3. Update `registerWithDrive()` to pass identity claims as shared claims:

```kotlin
private suspend fun registerWithDrive(
    identity: Identity,
    driveApi: DriveApi
): VerifiableCredential {
    // Step 1: Register — send our DID
    val startResp = driveApi.register(
        RegisterStartRequest(identity.did, identity.keyId)
    )

    // Step 2: Verify server's signature (best-effort)
    if (startResp.server_did.isNotBlank() && startResp.server_signature.isNotBlank()) {
        val serverVerified = verifier.verifyChallengeResponse(
            startResp.server_did, startResp.server_key_id,
            startResp.challenge, startResp.server_signature
        ).getOrNull() ?: false
        if (!serverVerified) {
            android.util.Log.w("DriveLogin", "Server signature verification failed — proceeding with registration")
        }
    }

    // Step 3: Sign the challenge
    val signatureBytes = vault.sign(identity.keyId, startResp.challenge.toByteArray()).getOrThrow()
    val signedChallenge = Multibase.encode(signatureBytes)

    // Step 4: Build shared claims from identity profile
    val sharedClaims = identity.claimsMap().ifEmpty { null }

    // Step 5: Complete registration — receive VC
    val verifyResp = driveApi.registerVerify(
        RegisterVerifyRequest(identity.did, identity.keyId, signedChallenge, shared_claims = sharedClaims)
    )

    // Step 6: Store the credential
    val vc = verifyResp.credential
    vault.storeCredential(vc).getOrThrow()
    return vc
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd android && ./gradlew compileDebugKotlin --info`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/auth/DriveLoginViewModel.kt
git commit -m "refactor(android): DriveLoginViewModel uses identity.claimsMap() for shared claims"
```

---

### Task 7: Update InviteAcceptViewModel to use identity email (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/invite/InviteAcceptViewModel.kt`

- [ ] **Step 1: Remove ProfileManager and use selected identity's email**

In `InviteAcceptViewModel.kt`:

1. Remove `private val profileManager: ProfileManager` from constructor.
2. Remove `ProfileManager` import.
3. Add a selected identity flow:

```kotlin
private val _selectedIdentity = MutableStateFlow<Identity?>(null)
val selectedIdentity = _selectedIdentity.asStateFlow()
```

4. Import Identity: `import my.ssdid.wallet.domain.model.Identity`

5. In `loadInvitation()`, replace lines 71-72:

```kotlin
// OLD:
// val claims = profileManager.getProfileClaims()
// val walletEmail = claims["email"] ?: ""

// NEW:
val identities = vault.listIdentities()
val firstIdentity = identities.firstOrNull()
_selectedIdentity.value = firstIdentity
val walletEmail = firstIdentity?.email ?: ""
```

6. In `accept()`, use selected identity's email:

```kotlin
// In the AcceptWithWalletRequest construction, replace:
// email = _state.value.walletEmail
// With:
email = _selectedIdentity.value?.email ?: _state.value.walletEmail
```

7. Add a `selectIdentity` function:

```kotlin
fun selectIdentity(identity: Identity) {
    _selectedIdentity.value = identity
    _state.update { it.copy(walletEmail = identity.email ?: "") }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd android && ./gradlew compileDebugKotlin --info`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/invite/InviteAcceptViewModel.kt
git commit -m "refactor(android): InviteAcceptViewModel uses identity email instead of ProfileManager"
```

---

### Task 8: Update ProfileSetupViewModel to be identity-scoped (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModel.kt`
- Modify: `android/app/src/test/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModelTest.kt`

- [ ] **Step 1: Rewrite ProfileSetupViewModel to use Vault + keyId**

```kotlin
package my.ssdid.wallet.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.auth.ClaimValidator
import my.ssdid.wallet.domain.vault.Vault
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _nameError = MutableStateFlow<String?>(null)
    val nameError = _nameError.asStateFlow()

    private val _emailError = MutableStateFlow<String?>(null)
    val emailError = _emailError.asStateFlow()

    private val _isValid = MutableStateFlow(false)
    val isValid = _isValid.asStateFlow()

    private var originalEmail = ""

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _emailChanged = MutableStateFlow(false)
    val emailChanged = _emailChanged.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val identity = vault.getIdentity(keyId)
                if (identity != null) {
                    _name.value = identity.profileName ?: ""
                    _email.value = identity.email ?: ""
                    originalEmail = identity.email ?: ""
                }
            } catch (e: Exception) {
                _error.value = "Failed to load profile"
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

    private fun revalidate() {
        val nameOk = _name.value.isNotBlank() && _nameError.value == null
        val emailOk = _email.value.isNotBlank() && _emailError.value == null
        _isValid.value = nameOk && emailOk
    }

    fun save() {
        if (_saving.value || _saved.value) return
        _saving.value = true
        viewModelScope.launch {
            _error.value = null
            val result = vault.updateIdentityProfile(
                keyId,
                profileName = _name.value,
                email = _email.value
            )
            if (result.isSuccess) {
                _emailChanged.value = _email.value.trim().lowercase() != originalEmail.trim().lowercase()
                _saved.value = true
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save profile"
            }
            _saving.value = false
        }
    }
}
```

- [ ] **Step 2: Update navigation route to pass keyId**

In `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`, update:

```kotlin
object ProfileSetup : Screen("profile_setup?keyId={keyId}")
```

Update `NavGraph.kt` accordingly to accept the `keyId` argument.

- [ ] **Step 3: Update ProfileSetupViewModelTest**

Rewrite the test to mock `Vault` instead of `ProfileManager`. Use `SavedStateHandle(mapOf("keyId" to "did:ssdid:abc#key-1"))`.

- [ ] **Step 4: Verify compilation and run tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --info`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModel.kt android/app/src/main/java/my/ssdid/wallet/ui/navigation/ android/app/src/test/java/my/ssdid/wallet/feature/profile/ProfileSetupViewModelTest.kt
git commit -m "refactor(android): ProfileSetupViewModel is now identity-scoped via keyId"
```

---

### Task 9: Wire migration into app startup + remove ProfileManager (Android)

**Files:**
- Modify: App startup code (Application class or MainViewModel)
- Delete: `android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileManager.kt`
- Delete: `android/app/src/test/java/my/ssdid/wallet/domain/profile/ProfileManagerTest.kt`

- [ ] **Step 1: Wire ProfileMigration into app startup**

In the appropriate startup location (e.g., `MainViewModel.init` or `Application.onCreate`), inject `ProfileMigration` and call:

```kotlin
viewModelScope.launch {
    profileMigration.migrateIfNeeded()
}
```

- [ ] **Step 2: Delete ProfileManager and its test**

```bash
rm android/app/src/main/java/my/ssdid/wallet/domain/profile/ProfileManager.kt
rm android/app/src/test/java/my/ssdid/wallet/domain/profile/ProfileManagerTest.kt
```

Remove any Hilt provider for ProfileManager in AppModule if it exists.

- [ ] **Step 3: Verify no remaining references**

Run: `grep -r "ProfileManager" android/app/src/ --include="*.kt" | grep -v ProfileMigration`
Expected: No results.

- [ ] **Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --info`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(android): wire migration at startup, remove deprecated ProfileManager"
```

---

## Chunk 3: iOS Implementation

### Task 10: Add profile fields to iOS Identity model

**Files:**
- Modify: `ios/SsdidWallet/Domain/Model/Identity.swift`

- [ ] **Step 1: Add profileName, email and emailVerified fields**

```swift
struct Identity: Codable, Identifiable {
    let name: String
    let did: String
    let keyId: String
    let algorithm: Algorithm
    let publicKeyMultibase: String
    let createdAt: String
    var isActive: Bool = true
    var recoveryKeyId: String? = nil
    var hasRecoveryKey: Bool = false
    var preRotatedKeyId: String? = nil
    var profileName: String? = nil
    var email: String? = nil
    var emailVerified: Bool = false

    var id: String { keyId }

    /// Returns a claims map suitable for sharing with services.
    func claimsMap() -> [String: String] {
        var claims: [String: String] = [:]
        if let profileName = profileName { claims["name"] = profileName }
        if let email = email { claims["email"] = email }
        return claims
    }
}
```

- [ ] **Step 2: Verify the project builds**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWallet/Domain/Model/Identity.swift
git commit -m "feat(ios): add profileName, email, emailVerified and claimsMap to Identity"
```

---

### Task 11: Add updateIdentityProfile to iOS Vault

**Files:**
- Modify: `ios/SsdidWallet/Domain/Vault/Vault.swift`
- Modify: `ios/SsdidWallet/Domain/Vault/VaultImpl.swift`

- [ ] **Step 1: Add to Vault protocol**

In `Vault.swift`, add after `deleteIdentity`:

```swift
/// Updates profile fields (profileName, email, emailVerified) on an existing identity.
func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws
```

- [ ] **Step 2: Implement in VaultImpl**

In `VaultImpl.swift`, add:

```swift
func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {
    guard var identity = await storage.getIdentity(keyId: keyId) else {
        throw VaultError.identityNotFound(keyId)
    }
    guard let encryptedKey = await storage.getEncryptedPrivateKey(keyId: keyId) else {
        throw VaultError.privateKeyNotFound(keyId)
    }
    if let profileName = profileName { identity.profileName = profileName }
    if let email = email { identity.email = email }
    if let emailVerified = emailVerified { identity.emailVerified = emailVerified }
    try await storage.saveIdentity(identity, encryptedPrivateKey: encryptedKey)
}
```

- [ ] **Step 3: Verify build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWallet/Domain/Vault/Vault.swift ios/SsdidWallet/Domain/Vault/VaultImpl.swift
git commit -m "feat(ios): add updateIdentityProfile to Vault"
```

---

### Task 12: Update iOS DriveLoginScreen to use identity claims

**Files:**
- Modify: `ios/SsdidWallet/Feature/Auth/DriveLoginScreen.swift`

- [ ] **Step 1: Remove ProfileManager usage in registerWithDrive**

In `DriveLoginScreen.swift`, update `registerWithDrive()` (lines 381-410).

Replace lines 394-396:
```swift
// OLD:
// let profileManager = ProfileManager(vault: services.vault)
// let profileClaims = await profileManager.getProfileClaims()
// let claims: [String: String]? = profileClaims.isEmpty ? nil : profileClaims

// NEW: Use identity's own profile claims
let identityClaims = identity.claimsMap()
let sharedClaims: [String: String]? = identityClaims.isEmpty ? nil : identityClaims
```

Update the `registerVerify` call to use `sharedClaims` variable.

- [ ] **Step 2: Verify build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWallet/Feature/Auth/DriveLoginScreen.swift
git commit -m "refactor(ios): DriveLoginScreen uses identity.claimsMap() for shared claims"
```

---

### Task 13: Update iOS InviteAcceptViewModel to use identity email

**Files:**
- Modify: `ios/SsdidWallet/Feature/Invite/InviteAcceptViewModel.swift`

- [ ] **Step 1: Replace ProfileManager with identity email**

In `InviteAcceptViewModel.swift`:

1. In `loadData()` (lines 59-62), replace:
```swift
// OLD:
// let profileManager = ProfileManager(vault: services.vault)
// let claims = await profileManager.getProfileClaims()
// walletEmail = claims["email"] ?? ""

// NEW:
walletEmail = selectedIdentity?.email ?? ""
```

2. Update `walletEmail` when identity is selected — add after setting `selectedIdentity` in `loadData()`:
```swift
if identities.count == 1 {
    selectedIdentity = identities.first
}
walletEmail = selectedIdentity?.email ?? ""
```

3. In `registerWithDrive()` (lines 201-202), replace:
```swift
// OLD:
// let profileManager = ProfileManager(vault: services.vault)
// let profileClaims = await profileManager.getProfileClaims()

// NEW:
let profileClaims = identity.claimsMap()
```

- [ ] **Step 2: Verify build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWallet/Feature/Invite/InviteAcceptViewModel.swift
git commit -m "refactor(ios): InviteAcceptViewModel uses identity.claimsMap()"
```

---

### Task 14: Update iOS ProfileSetupScreen to be identity-scoped

**Files:**
- Modify: `ios/SsdidWallet/Feature/Profile/ProfileSetupScreen.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/ContentView.swift`

- [ ] **Step 1: Update Route enum**

In `AppRouter.swift`, update the route cases:
```swift
case profileSetup(keyId: String? = nil)
case profileEdit(keyId: String)
```

Remove the old `case profileSetup` and `case profileEdit`.

- [ ] **Step 2: Update ContentView destinations**

In `ContentView.swift`, update the navigation destinations:
```swift
case .profileSetup(let keyId):
    ProfileSetupScreen(isEditing: false, keyId: keyId)
case .profileEdit(let keyId):
    ProfileSetupScreen(isEditing: true, keyId: keyId)
```

- [ ] **Step 3: Add keyId parameter to ProfileSetupScreen and use Vault**

```swift
struct ProfileSetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let isEditing: Bool
    let keyId: String?

    @State private var name = ""
    @State private var email = ""
    @State private var originalEmail = ""
    @State private var nameError: String?
    @State private var emailError: String?
    @State private var saving = false
    @State private var loaded = false
    @State private var identityKeyId = ""
    // ... (keep existing validation logic)
```

Update the `.task` block:
```swift
.task {
    guard !loaded else { return }
    loaded = true
    let targetKeyId: String
    if let keyId = keyId {
        targetKeyId = keyId
    } else {
        guard let first = await services.vault.listIdentities().first else { return }
        targetKeyId = first.keyId
    }
    identityKeyId = targetKeyId
    if let identity = await services.vault.getIdentity(keyId: targetKeyId) {
        name = identity.profileName ?? ""
        email = identity.email ?? ""
        originalEmail = identity.email ?? ""
    }
}
```

Update `saveAndContinue()`:
```swift
private func saveAndContinue() {
    saving = true
    let trimmedName = name.trimmingCharacters(in: .whitespaces)
    let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
    let emailChanged = trimmedEmail.lowercased() != originalEmail.trimmingCharacters(in: .whitespaces).lowercased()

    Task {
        try? await services.vault.updateIdentityProfile(
            keyId: identityKeyId,
            profileName: trimmedName,
            email: trimmedEmail,
            emailVerified: nil
        )
        saving = false

        if emailChanged {
            router.push(.emailVerification(email: trimmedEmail, isEditing: true))
        } else {
            router.pop()
        }
    }
}
```

- [ ] **Step 4: Verify build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 5: Commit**

```bash
git add ios/SsdidWallet/Feature/Profile/ProfileSetupScreen.swift ios/SsdidWallet/UI/Navigation/AppRouter.swift ios/SsdidWallet/UI/Navigation/ContentView.swift
git commit -m "refactor(ios): ProfileSetupScreen is now identity-scoped via keyId"
```

---

### Task 15: Add migration and remove ProfileManager (iOS)

**Files:**
- Create: `ios/SsdidWallet/Domain/Profile/ProfileMigration.swift`
- Delete: `ios/SsdidWallet/Domain/Profile/ProfileManager.swift`

- [ ] **Step 1: Create ProfileMigration**

```swift
// File: ios/SsdidWallet/Domain/Profile/ProfileMigration.swift
import Foundation

/// One-time migration: copies global profile VC data to the first identity.
enum ProfileMigration {

    private static let legacyProfileId = "urn:ssdid:profile"

    static func migrateIfNeeded(vault: Vault) async {
        let credentials = await vault.listCredentials()
        guard let profileVc = credentials.first(where: { $0.id == legacyProfileId }) else {
            return
        }

        let profileName = profileVc.credentialSubject.claims["name"]
        let email = profileVc.credentialSubject.claims["email"]
        let identities = await vault.listIdentities()

        // If no identities, preserve the profile VC so data isn't lost
        guard !identities.isEmpty else { return }

        // Copy profile to the first identity that doesn't already have email
        if let target = identities.first(where: { ($0.email ?? "").isEmpty }) {
            let hasData = !(email ?? "").isEmpty || !(profileName ?? "").isEmpty
            if hasData {
                try? await vault.updateIdentityProfile(
                    keyId: target.keyId,
                    profileName: profileName,
                    email: email,
                    emailVerified: nil
                )
            }
        }

        // Delete the legacy profile VC
        try? await vault.deleteCredential(credentialId: legacyProfileId)
    }
}
```

- [ ] **Step 2: Wire migration into app startup**

In `ServiceContainer.swift` or the app's startup sequence, add after vault is created:

```swift
Task {
    await ProfileMigration.migrateIfNeeded(vault: vault)
}
```

- [ ] **Step 3: Delete ProfileManager.swift**

```bash
rm ios/SsdidWallet/Domain/Profile/ProfileManager.swift
```

- [ ] **Step 4: Verify no remaining ProfileManager references**

Run: `grep -r "ProfileManager" ios/SsdidWallet/ --include="*.swift" | grep -v ProfileMigration`
Expected: No results.

- [ ] **Step 5: Verify build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 6: Commit**

```bash
git add ios/SsdidWallet/Domain/Profile/ProfileMigration.swift
git rm ios/SsdidWallet/Domain/Profile/ProfileManager.swift
git add -A
git commit -m "feat(ios): add ProfileMigration, remove deprecated ProfileManager"
```

---

## Chunk 4: UI + Final Integration

### Task 16: Add email/profileName input to CreateIdentityScreen (both platforms)

**Files:**
- Modify: `ios/SsdidWallet/Feature/Identity/CreateIdentityScreen.swift`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/CreateIdentityScreen.kt`

- [ ] **Step 1: iOS — Add profileName and email fields to CreateIdentityScreen**

In `CreateIdentityScreen.swift`:

Add state properties:
```swift
@State private var profileName = ""
@State private var email = ""
```

Add fields after the identity name TextField (around line 101):
```swift
Spacer().frame(height: 12)

Text("YOUR NAME")
    .font(.ssdidCaption)
    .foregroundStyle(Color.textTertiary)

TextField("", text: $profileName, prompt: Text("Your full name").foregroundStyle(Color.textTertiary))
    .textFieldStyle(.plain)
    .font(.ssdidBody)
    .foregroundStyle(Color.textPrimary)
    .padding(14)
    .background(Color.bgCard)
    .cornerRadius(12)
    .overlay(
        RoundedRectangle(cornerRadius: 12)
            .stroke(Color.ssdidBorder, lineWidth: 1)
    )

Spacer().frame(height: 12)

Text("EMAIL")
    .font(.ssdidCaption)
    .foregroundStyle(Color.textTertiary)

TextField("", text: $email, prompt: Text("your@email.com").foregroundStyle(Color.textTertiary))
    .textFieldStyle(.plain)
    .font(.ssdidBody)
    .foregroundStyle(Color.textPrimary)
    .keyboardType(.emailAddress)
    .textInputAutocapitalization(.never)
    .autocorrectionDisabled()
    .padding(14)
    .background(Color.bgCard)
    .cornerRadius(12)
    .overlay(
        RoundedRectangle(cornerRadius: 12)
            .stroke(Color.ssdidBorder, lineWidth: 1)
    )
```

In `createIdentity()`, after successful identity creation, save profile:
```swift
let identity = try await services.ssdidClient.initIdentity(
    name: trimmedName,
    algorithm: selectedAlgorithm
)
// Set profile on the newly created identity
let trimmedProfileName = profileName.trimmingCharacters(in: .whitespaces)
let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
if !trimmedProfileName.isEmpty || !trimmedEmail.isEmpty {
    try? await services.vault.updateIdentityProfile(
        keyId: identity.keyId,
        profileName: trimmedProfileName.isEmpty ? nil : trimmedProfileName,
        email: trimmedEmail.isEmpty ? nil : trimmedEmail,
        emailVerified: nil
    )
}
```

- [ ] **Step 2: Android — Add profileName and email fields to CreateIdentityScreen**

In `CreateIdentityScreen.kt`, add state variables:
```kotlin
var profileName by remember { mutableStateOf("") }
var email by remember { mutableStateOf("") }
```

Add OutlinedTextField components for profileName ("YOUR NAME") and email ("EMAIL") after the identity name field in the LazyColumn.

Update `CreateIdentityViewModel` to inject `Vault` (instead of just `VaultStorage`) and accept profileName/email:

```kotlin
@HiltViewModel
class CreateIdentityViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    private val storage: VaultStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // ... existing code ...

    fun createIdentity(name: String, algorithm: Algorithm, profileName: String?, email: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null
            client.initIdentity(name, algorithm)
                .onSuccess { identity ->
                    if (!profileName.isNullOrBlank() || !email.isNullOrBlank()) {
                        vault.updateIdentityProfile(
                            identity.keyId,
                            profileName = profileName?.takeIf { it.isNotBlank() },
                            email = email?.takeIf { it.isNotBlank() }
                        )
                    }
                    storage.setOnboardingCompleted()
                    onSuccess()
                }
                .onFailure {
                    io.sentry.Sentry.captureException(it)
                    _error.value = it.message ?: "Failed to create identity"
                }
            _isCreating.value = false
        }
    }
}
```

Update the button click handler to pass the new fields:
```kotlin
viewModel.createIdentity(name, selectedAlgo, profileName.ifBlank { null }, email.ifBlank { null }) { ... }
```

- [ ] **Step 3: Verify both platforms build**

Android: `cd android && ./gradlew compileDebugKotlin`
iOS: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWallet/Feature/Identity/CreateIdentityScreen.swift android/app/src/main/java/my/ssdid/wallet/feature/identity/CreateIdentityScreen.kt
git commit -m "feat: add profileName and email fields to identity creation screen"
```

---

### Task 17: Show per-identity email on WalletHomeScreen and IdentityDetailScreen

**Files:**
- Modify: `ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift`
- Modify: `ios/SsdidWallet/Feature/Identity/IdentityDetailScreen.swift`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/IdentityDetailScreen.kt`

- [ ] **Step 1: Update WalletHomeScreen to show identity email**

On both platforms, wherever identities are listed, show `identity.email` below the identity name/DID if present.

iOS example (in the identity row):
```swift
if let email = identity.email {
    Text(email)
        .font(.system(size: 12))
        .foregroundStyle(Color.textTertiary)
}
```

Android example (in the identity card):
```kotlin
identity.email?.let { email ->
    Text(email, fontSize = 12.sp, color = TextTertiary)
}
```

- [ ] **Step 2: Update IdentityDetailScreen to show/edit profile**

On both platforms, add a "Profile" section on the identity detail screen that shows `profileName` and `email`. Include an "Edit Profile" button that navigates to `ProfileSetupScreen(keyId: identity.keyId)`.

iOS:
```swift
// Profile section
if identity.profileName != nil || identity.email != nil {
    VStack(alignment: .leading, spacing: 4) {
        Text("PROFILE")
            .font(.ssdidCaption)
            .foregroundStyle(Color.textTertiary)

        if let profileName = identity.profileName {
            HStack {
                Text("Name")
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                Text(profileName)
                    .foregroundStyle(Color.textPrimary)
            }
        }
        if let email = identity.email {
            HStack {
                Text("Email")
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                Text(email)
                    .foregroundStyle(Color.textPrimary)
            }
        }
    }
    .ssdidCard()
}

Button {
    router.push(.profileEdit(keyId: identity.keyId))
} label: {
    Text(identity.email != nil ? "Edit Profile" : "Set Up Profile")
}
.buttonStyle(.ssdidSecondary)
```

- [ ] **Step 3: Verify both platforms build**

Android: `cd android && ./gradlew compileDebugKotlin`
iOS: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: show per-identity email on home and detail screens"
```

---

### Task 18: Final verification

- [ ] **Step 1: Run full Android test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest --info`
Expected: All PASS

- [ ] **Step 2: Run Android lint**

Run: `cd android && ./gradlew lint`
Expected: No new errors

- [ ] **Step 3: Verify iOS build**

Run: `cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

- [ ] **Step 4: Verify no ProfileManager references remain**

```bash
grep -r "ProfileManager" android/app/src/ --include="*.kt" | grep -v ProfileMigration
grep -r "ProfileManager" ios/SsdidWallet/ --include="*.swift" | grep -v ProfileMigration
```
Expected: No results for either.

- [ ] **Step 5: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: final cleanup for identity-scoped profiles"
```
