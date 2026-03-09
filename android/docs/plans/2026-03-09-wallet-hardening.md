# SSDID Wallet Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix ECDSA registry integration, harden biometric checks, refactor layer violations, and add missing test coverage across vault, recovery, and ViewModel layers.

**Architecture:** Clean architecture with domain/platform separation. Domain layer must not reference Android APIs directly. Tests use JUnit 4 + Mockk + Truth + Robolectric. All crypto uses BouncyCastle. Registry is an Elixir/Phoenix app at `https://registry.ssdid.my` using ExCcrypto for verification.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit 2, kotlinx-serialization, BouncyCastle, Robolectric

---

## Task 1: Investigate and fix ECDSA P-256/P-384 registry 401

The registry uses ExCcrypto (Erlang `:crypto`) for ECDSA verification. The wallet uses BouncyCastle `SHA256withECDSA` / `SHA384withECDSA` which **double-hashes** the signing payload (the payload is already `SHA3-256(options) || SHA3-256(doc)`, then `SHA256withECDSA` hashes it again with SHA-256 before ECDSA signing). The registry's ExCcrypto may or may not apply the same hash. This is the most likely root cause.

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/domain/crypto/ClassicalProvider.kt:218-227`
- Test: `app/src/test/java/my/ssdid/wallet/domain/crypto/ClassicalProviderTest.kt`
- Test: `app/src/test/java/my/ssdid/wallet/integration/RegistryIntegrationTest.kt`

**Step 1: Add a local ECDSA round-trip verification test**

Add a test that generates an ECDSA P-256 keypair, signs data, and verifies — confirming our local crypto works in isolation.

```kotlin
@Test
fun `ECDSA P-256 sign and verify round trip`() {
    val kp = provider.generateKeyPair(Algorithm.ECDSA_P256)
    val data = "test payload".toByteArray()
    val sig = provider.sign(Algorithm.ECDSA_P256, kp.privateKey, data)
    assertThat(provider.verify(Algorithm.ECDSA_P256, kp.publicKey, sig, data)).isTrue()
}

@Test
fun `ECDSA P-384 sign and verify round trip`() {
    val kp = provider.generateKeyPair(Algorithm.ECDSA_P384)
    val data = "test payload".toByteArray()
    val sig = provider.sign(Algorithm.ECDSA_P384, kp.privateKey, data)
    assertThat(provider.verify(Algorithm.ECDSA_P384, kp.publicKey, sig, data)).isTrue()
}
```

**Step 2: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ClassicalProviderTest"`
Expected: PASS (our local round trip works)

**Step 3: Add `signRaw` method to ClassicalProvider for ECDSA**

The registry's ExCcrypto `AsymkeySign` module likely uses `NONEwithECDSA` (raw ECDSA, no hash). Since our signing payload is already hashed (SHA3-256), we should sign with `NONEwithECDSA` instead of `SHA256withECDSA`.

Add a new method in `ClassicalProvider.kt`:

```kotlin
private fun signEcdsaRaw(curveName: String, privateKey: ByteArray, data: ByteArray): ByteArray {
    val pkcs8 = wrapEcPrivateKey(privateKey, curveName)
    val keySpec = PKCS8EncodedKeySpec(pkcs8)
    val kf = KeyFactory.getInstance("EC", "BC")
    val privKey = kf.generatePrivate(keySpec)
    val sig = Signature.getInstance("NONEwithECDSA", "BC")
    sig.initSign(privKey)
    sig.update(data)
    return sig.sign()
}
```

Then update the `sign` method dispatch:

```kotlin
Algorithm.ECDSA_P256 -> signEcdsaRaw("secp256r1", privateKey, data)
Algorithm.ECDSA_P384 -> signEcdsaRaw("secp384r1", privateKey, data)
```

And update `verify` similarly to use `NONEwithECDSA`:

```kotlin
private fun verifyEcdsaRaw(curveName: String, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
    val x509 = wrapEcPublicKey(publicKey, curveName)
    val keySpec = X509EncodedKeySpec(x509)
    val kf = KeyFactory.getInstance("EC", "BC")
    val pubKey = kf.generatePublic(keySpec)
    val sig = Signature.getInstance("NONEwithECDSA", "BC")
    sig.initVerify(pubKey)
    sig.update(data)
    return sig.verify(signature)
}
```

**Step 4: Run local tests to verify round trip still works**

Run: `./gradlew :app:testDebugUnitTest --tests "*.ClassicalProviderTest"`
Expected: PASS

**Step 5: Run integration tests against registry**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RegistryIntegrationTest.register and resolve DID with ECDSA*"`
Expected: PASS (if the double-hash was the root cause)

**Step 6: If NONEwithECDSA doesn't fix it, investigate further**

Alternative root causes to check:
- DER signature format mismatch (Erlang vs BouncyCastle)
- Public key encoding: raw EC point vs X.509 SubjectPublicKeyInfo
- Canonical JSON differences between wallet and registry

If `NONEwithECDSA` causes the 64-byte payload to exceed the max input size for the curve (P-256 can sign up to ~32 bytes raw), then the registry must be using `SHA256withECDSA` too, and the issue is elsewhere. In that case, revert to `SHA256withECDSA` and investigate canonical JSON output byte-by-byte.

**Step 7: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/crypto/ClassicalProvider.kt \
        app/src/test/java/my/ssdid/wallet/domain/crypto/ClassicalProviderTest.kt
git commit -m "fix(crypto): investigate and fix ECDSA registry signing mismatch"
```

---

## Task 2: Extract `DeviceInfoProvider` interface (Ar-F2)

`DeviceManager.kt:82` directly uses `android.os.Build.MODEL` in the domain layer. Extract behind an interface.

**Files:**
- Create: `app/src/main/java/my/ssdid/wallet/domain/device/DeviceInfoProvider.kt`
- Create: `app/src/main/java/my/ssdid/wallet/platform/device/AndroidDeviceInfoProvider.kt`
- Modify: `app/src/main/java/my/ssdid/wallet/domain/device/DeviceManager.kt:1-4,15-19,82,99`
- Modify: `app/src/main/java/my/ssdid/wallet/di/AppModule.kt` (bind provider)

**Step 1: Create the domain interface**

```kotlin
// app/src/main/java/my/ssdid/wallet/domain/device/DeviceInfoProvider.kt
package my.ssdid.wallet.domain.device

interface DeviceInfoProvider {
    val deviceName: String
    val platform: String
}
```

**Step 2: Create the Android implementation**

```kotlin
// app/src/main/java/my/ssdid/wallet/platform/device/AndroidDeviceInfoProvider.kt
package my.ssdid.wallet.platform.device

import android.os.Build
import my.ssdid.wallet.domain.device.DeviceInfoProvider
import javax.inject.Inject

class AndroidDeviceInfoProvider @Inject constructor() : DeviceInfoProvider {
    override val deviceName: String get() = Build.MODEL ?: "Android Device"
    override val platform: String get() = "android"
}
```

**Step 3: Update DeviceManager to use the interface**

Remove `import android.os.Build` from `DeviceManager.kt`. Add `DeviceInfoProvider` as a constructor parameter:

```kotlin
class DeviceManager(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val ssdidClient: dagger.Lazy<SsdidClient>,
    private val deviceInfo: DeviceInfoProvider
)
```

Replace `Build.MODEL ?: "This Device"` on line 82 with `deviceInfo.deviceName`.
Replace `"android"` on lines 49 and 100 with `deviceInfo.platform`.

**Step 4: Bind in Hilt module**

Add to `AppModule.kt`:

```kotlin
@Binds
abstract fun bindDeviceInfoProvider(impl: AndroidDeviceInfoProvider): DeviceInfoProvider
```

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (DeviceManager tests should still pass; they use mockk)

**Step 6: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/domain/device/DeviceInfoProvider.kt \
        app/src/main/java/my/ssdid/wallet/platform/device/AndroidDeviceInfoProvider.kt \
        app/src/main/java/my/ssdid/wallet/domain/device/DeviceManager.kt \
        app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "refactor(device): extract DeviceInfoProvider interface from Build.MODEL usage"
```

---

## Task 3: Move `KeystoreManager` interface to domain layer (Ar-F3)

`KeystoreManager` is an interface used by `VaultImpl` (domain layer) but lives in `platform/keystore/`. The interface belongs in domain; only the implementation belongs in platform.

**Files:**
- Move: `app/src/main/java/my/ssdid/wallet/platform/keystore/KeystoreManager.kt` → `app/src/main/java/my/ssdid/wallet/domain/vault/KeystoreManager.kt`
- Modify: All files that import `my.ssdid.wallet.platform.keystore.KeystoreManager`

**Step 1: Move the interface file**

```bash
mv app/src/main/java/my/ssdid/wallet/platform/keystore/KeystoreManager.kt \
   app/src/main/java/my/ssdid/wallet/domain/vault/KeystoreManager.kt
```

**Step 2: Update the package declaration**

Change `package my.ssdid.wallet.platform.keystore` to `package my.ssdid.wallet.domain.vault` in `KeystoreManager.kt`.

**Step 3: Update all imports**

Find and replace `my.ssdid.wallet.platform.keystore.KeystoreManager` with `my.ssdid.wallet.domain.vault.KeystoreManager` in:
- `VaultImpl.kt`
- `RecoveryManager.kt`
- `KeystoreManagerImpl.kt` (platform implementation)
- `AppModule.kt` or `StorageModule.kt` (DI binding)
- All test files that reference it (VaultTest, RecoveryManagerTest, KeyRotationManagerTest, etc.)

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add -A  # All moved/updated files
git commit -m "refactor(arch): move KeystoreManager interface from platform to domain layer"
```

---

## Task 4: Move `ActivityRepositoryImpl` to platform/storage (Ar-F1)

`ActivityRepositoryImpl` uses `android.content.Context` and DataStore — platform concerns. The interface `ActivityRepository` stays in domain; the implementation moves to platform.

**Files:**
- Move: `app/src/main/java/my/ssdid/wallet/domain/history/ActivityRepositoryImpl.kt` → `app/src/main/java/my/ssdid/wallet/platform/storage/ActivityRepositoryImpl.kt`
- Modify: DI module that binds the implementation

**Step 1: Move the file**

```bash
mkdir -p app/src/main/java/my/ssdid/wallet/platform/storage
mv app/src/main/java/my/ssdid/wallet/domain/history/ActivityRepositoryImpl.kt \
   app/src/main/java/my/ssdid/wallet/platform/storage/ActivityRepositoryImpl.kt
```

**Step 2: Update the package declaration**

Change `package my.ssdid.wallet.domain.history` to `package my.ssdid.wallet.platform.storage`.

Add import for the interface: `import my.ssdid.wallet.domain.history.ActivityRepository`.
Add import for the model: `import my.ssdid.wallet.domain.model.ActivityRecord`.

**Step 3: Update DI binding import**

In the Hilt module that binds `ActivityRepositoryImpl`, update the import to `my.ssdid.wallet.platform.storage.ActivityRepositoryImpl`.

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor(arch): move ActivityRepositoryImpl from domain to platform/storage"
```

---

## Task 5: Add `createProof` unit tests to VaultTest (T-D10)

`VaultImpl.createProof` is untested. It's the critical W3C Data Integrity proof builder.

**Files:**
- Modify: `app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt`

**Step 1: Write the failing test for createProof basic behavior**

```kotlin
@Test
fun `createProof generates valid W3C Data Integrity proof`() = runTest {
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    val doc = vault.buildDidDocument(identity.keyId).getOrThrow()
    val docJson = Json.encodeToString(doc)
    val docObject = Json.parseToJsonElement(docJson).jsonObject

    val proof = vault.createProof(identity.keyId, docObject, "assertionMethod").getOrThrow()

    assertThat(proof.type).isEqualTo("Ed25519Signature2020")
    assertThat(proof.verificationMethod).isEqualTo(identity.keyId)
    assertThat(proof.proofPurpose).isEqualTo("assertionMethod")
    assertThat(proof.proofValue).startsWith("u") // multibase base64url
    assertThat(proof.created).isNotEmpty()
    assertThat(proof.challenge).isNull()
}
```

Requires adding imports:
```kotlin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
```

**Step 2: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.VaultTest.createProof generates valid*"`
Expected: PASS (implementation exists)

**Step 3: Write test for createProof with challenge**

```kotlin
@Test
fun `createProof includes challenge when provided`() = runTest {
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    val doc = vault.buildDidDocument(identity.keyId).getOrThrow()
    val docJson = Json.encodeToString(doc)
    val docObject = Json.parseToJsonElement(docJson).jsonObject

    val proof = vault.createProof(identity.keyId, docObject, "capabilityInvocation", "test-challenge-123").getOrThrow()

    assertThat(proof.proofPurpose).isEqualTo("capabilityInvocation")
    assertThat(proof.challenge).isEqualTo("test-challenge-123")
}
```

**Step 4: Write test verifying proof signature is cryptographically valid**

```kotlin
@Test
fun `createProof signature verifies with public key`() = runTest {
    val classical = ClassicalProvider()
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    val doc = vault.buildDidDocument(identity.keyId).getOrThrow()
    val docJson = Json.encodeToString(doc)
    val docObject = Json.parseToJsonElement(docJson).jsonObject

    val proof = vault.createProof(identity.keyId, docObject, "assertionMethod").getOrThrow()

    // Reconstruct the signing payload
    val proofOptions = buildMap<String, kotlinx.serialization.json.JsonElement> {
        put("type", kotlinx.serialization.json.JsonPrimitive(proof.type))
        put("created", kotlinx.serialization.json.JsonPrimitive(proof.created))
        put("verificationMethod", kotlinx.serialization.json.JsonPrimitive(proof.verificationMethod))
        put("proofPurpose", kotlinx.serialization.json.JsonPrimitive(proof.proofPurpose))
    }
    val sha3 = java.security.MessageDigest.getInstance("SHA3-256")
    val optionsHash = sha3.digest(VaultImpl.canonicalJson(kotlinx.serialization.json.JsonObject(proofOptions)).toByteArray())
    sha3.reset()
    val docHash = sha3.digest(VaultImpl.canonicalJson(docObject).toByteArray())
    val payload = optionsHash + docHash

    val signature = Multibase.decode(proof.proofValue)
    val publicKey = Multibase.decode(identity.publicKeyMultibase)
    assertThat(classical.verify(Algorithm.ED25519, publicKey, signature, payload)).isTrue()
}
```

**Step 5: Run all VaultTest tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.VaultTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt
git commit -m "test(vault): add createProof unit tests with cryptographic verification"
```

---

## Task 6: Strengthen VaultTest sign test with crypto verification (T-D4)

Current `sign produces valid signature` test only checks `isNotEmpty()`. It should verify the signature cryptographically.

**Files:**
- Modify: `app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt`

**Step 1: Update the existing sign test**

Replace the existing test at line 49-55:

```kotlin
@Test
fun `sign produces valid signature`() = runTest {
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    val message = "Hello".toByteArray()
    val signature = vault.sign(identity.keyId, message).getOrThrow()

    // Verify signature cryptographically
    val publicKey = Multibase.decode(identity.publicKeyMultibase)
    val classical = ClassicalProvider()
    assertThat(classical.verify(Algorithm.ED25519, publicKey, signature, message)).isTrue()
}
```

**Step 2: Add ECDSA P-256 sign verification test**

```kotlin
@Test
fun `sign with ECDSA P-256 produces verifiable signature`() = runTest {
    val identity = vault.createIdentity("Test-P256", Algorithm.ECDSA_P256).getOrThrow()
    val message = "test data".toByteArray()
    val signature = vault.sign(identity.keyId, message).getOrThrow()

    val publicKey = Multibase.decode(identity.publicKeyMultibase)
    val classical = ClassicalProvider()
    assertThat(classical.verify(Algorithm.ECDSA_P256, publicKey, signature, message)).isTrue()
}
```

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.VaultTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt
git commit -m "test(vault): strengthen sign tests with cryptographic verification"
```

---

## Task 7: Add recovery key verification tests (T-D1)

`RecoveryManagerTest` only tests key generation. It should test `restoreWithRecoveryKey` and verify the stored public key is used for validation.

**Files:**
- Modify: `app/src/test/java/my/ssdid/wallet/domain/recovery/RecoveryManagerTest.kt`

**Step 1: Write test for successful restore**

```kotlin
@Test
fun `restoreWithRecoveryKey creates new identity with same DID`() = runTest {
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    val recoveryKey = recoveryManager.generateRecoveryKey(identity).getOrThrow()
    val recoveryKeyBase64 = java.util.Base64.getEncoder().encodeToString(recoveryKey)

    val restored = recoveryManager.restoreWithRecoveryKey(
        did = identity.did,
        recoveryKeyBase64 = recoveryKeyBase64,
        algorithm = Algorithm.ED25519,
        name = "Restored"
    ).getOrThrow()

    assertThat(restored.did).isEqualTo(identity.did)
    assertThat(restored.name).isEqualTo("Restored")
    assertThat(restored.keyId).isNotEqualTo(identity.keyId) // New key
    assertThat(restored.publicKeyMultibase).isNotEqualTo(identity.publicKeyMultibase)
}
```

**Step 2: Write test for invalid recovery key rejection**

```kotlin
@Test
fun `restoreWithRecoveryKey fails with wrong key`() = runTest {
    val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
    recoveryManager.generateRecoveryKey(identity)

    // Generate a different key and try to restore with it
    val wrongKey = ClassicalProvider().generateKeyPair(Algorithm.ED25519).privateKey
    val wrongKeyBase64 = java.util.Base64.getEncoder().encodeToString(wrongKey)

    val result = recoveryManager.restoreWithRecoveryKey(
        did = identity.did,
        recoveryKeyBase64 = wrongKeyBase64,
        algorithm = Algorithm.ED25519,
        name = "Restored"
    )

    assertThat(result.isFailure).isTrue()
}
```

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.RecoveryManagerTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/test/java/my/ssdid/wallet/domain/recovery/RecoveryManagerTest.kt
git commit -m "test(recovery): add restore and invalid key rejection tests"
```

---

## Task 8: Replace relaxed vault/verifier mocks in SsdidClientTest (T-D2)

`SsdidClientTest` uses `mockk(relaxed = true)` for vault and verifier. Relaxed mocks hide bugs by returning default values silently. Replace with strict mocks and explicit stubs.

**Files:**
- Modify: `app/src/test/java/my/ssdid/wallet/domain/SsdidClientTest.kt`

**Step 1: Change mock declarations**

In `setup()`, replace:
```kotlin
vault = mockk(relaxed = true)
verifier = mockk(relaxed = true)
```
with:
```kotlin
vault = mockk()
verifier = mockk()
```

Keep `httpClient`, `registryApi`, `serverApi`, and `activityRepo` relaxed — they're transport/side-effect objects where relaxed is acceptable.

**Step 2: Add explicit stubs to each test**

For each test that now fails, add the minimal `coEvery` stubs needed. The tests already have most stubs; the relaxed mock was only providing defaults for unstubbed calls. Run tests after each fix to find what's missing.

Common stubs that may need adding:
- `coEvery { vault.getIdentity(any()) } returns testIdentity` (for deactivate tests)
- `coEvery { activityRepo.addActivity(any()) } returns Unit` (for activity logging)

**Step 3: Run tests iteratively**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SsdidClientTest"`
Expected: PASS after adding all required stubs

**Step 4: Commit**

```bash
git add app/src/test/java/my/ssdid/wallet/domain/SsdidClientTest.kt
git commit -m "test(client): replace relaxed vault/verifier mocks with strict mocks"
```

---

## Task 9: Replace relaxed biometric mock and add gate tests (T-D3)

`TxSigningViewModelTest` uses `mockk(relaxed = true)` for `BiometricAuthenticator`. Add explicit biometric gating tests.

**Files:**
- Modify: `app/src/test/java/my/ssdid/wallet/feature/transaction/TxSigningViewModelTest.kt`

**Step 1: Make biometric mock strict**

Replace `biometricAuth = mockk(relaxed = true)` with `biometricAuth = mockk()`.

Add default stub in `setup()`:
```kotlin
coEvery { biometricAuth.authenticate(any(), any(), any()) } returns BiometricResult.Success
```

**Step 2: Add biometric rejection test**

```kotlin
@Test
fun `signTransaction fails when biometric is cancelled`() = runTest {
    coEvery { biometricAuth.authenticate(any(), any(), any()) } returns BiometricResult.Cancelled
    coEvery { vault.listIdentities() } returns listOf(testIdentity)

    viewModel.signTransaction()

    val state = viewModel.state.value
    assertThat(state).isInstanceOf(TxState.Failed::class.java)
}
```

Note: This test depends on whether `TxSigningViewModel.signTransaction()` actually calls `biometricAuth`. If it doesn't yet, this test reveals a missing biometric gate — which is B-D10. In that case, add the biometric check to the ViewModel before the signing call.

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TxSigningViewModelTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/test/java/my/ssdid/wallet/feature/transaction/TxSigningViewModelTest.kt
git commit -m "test(tx): replace relaxed biometric mock, add biometric gate tests"
```

---

## Task 10: Add biometric gate to TxSigningViewModel (B-D10)

If `TxSigningViewModel.signTransaction()` doesn't check biometric before signing, add the gate.

**Files:**
- Modify: `app/src/main/java/my/ssdid/wallet/feature/transaction/TxSigningViewModel.kt`
- Test: `app/src/test/java/my/ssdid/wallet/feature/transaction/TxSigningViewModelTest.kt`

**Step 1: Read TxSigningViewModel to check current biometric usage**

Check if `signTransaction()` already calls `biometricAuth.authenticate()`. If not:

**Step 2: Add biometric check before signing**

In `signTransaction()`, before calling `client.signTransaction(...)`:

```kotlin
// Biometric gate — require user authentication before signing
val bioResult = biometricAuth.authenticate(/* activity */)
if (bioResult != BiometricResult.Success) {
    _state.value = TxState.Failed("Biometric authentication required")
    return
}
```

Note: If `biometricAuth.authenticate` requires a `FragmentActivity` that the ViewModel doesn't have, the biometric check may need to be triggered from the Composable and passed as a callback. Check the existing pattern in other ViewModels before deciding the approach.

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TxSigningViewModelTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/feature/transaction/TxSigningViewModel.kt \
        app/src/test/java/my/ssdid/wallet/feature/transaction/TxSigningViewModelTest.kt
git commit -m "feat(tx): add biometric gate before transaction signing"
```

---

## Execution Order

Tasks are ordered by dependency:

1. **Task 1** (ECDSA fix) — independent, highest-priority bug fix
2. **Task 2** (DeviceInfoProvider) — independent refactor
3. **Task 3** (KeystoreManager move) — independent, but affects many imports
4. **Task 4** (ActivityRepositoryImpl move) — independent, small blast radius
5. **Task 5** (createProof tests) — depends on VaultTest existing structure
6. **Task 6** (sign test strengthening) — same file as Task 5, do after
7. **Task 7** (recovery tests) — independent
8. **Task 8** (SsdidClientTest mocks) — independent
9. **Task 9** (biometric mock) — do before Task 10
10. **Task 10** (biometric gate) — depends on Task 9 tests

Tasks 1-4 are independent and can be done in any order.
Tasks 5-6 should be sequential (same file).
Tasks 7-8 are independent.
Tasks 9-10 are sequential.
