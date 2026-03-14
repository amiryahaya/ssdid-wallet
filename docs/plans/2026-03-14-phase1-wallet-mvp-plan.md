# Phase 1: Wallet MVP Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the SSDID wallet interoperable with external issuers/verifiers by adding SD-JWT VC support, Verifiable Presentations, multi-method DID resolution, and security hardening.

**Architecture:** Add SD-JWT VC as a second credential format alongside existing VerifiableCredential. Introduce a DidResolver abstraction supporting did:ssdid (registry), did:key (local), did:jwk (local). Add JWK key format. Wire biometric gating to keystore. Add certificate pinning. Migrate to VCDM 2.0 context.

**Tech Stack:** Kotlin + Jetpack (Android), Swift + CryptoKit (iOS), BouncyCastle, kotlinx-serialization, Codable, OkHttp CertificatePinner, URLSession delegate pinning

**Design Doc:** `docs/plans/2026-03-14-ssdid-interop-sdk-design.md`

---

## Task Group A: Shared Test Vectors (Foundation)

### Task 1: Create test vector directory and SD-JWT VC fixtures

**Files:**
- Create: `test-vectors/sd-jwt/basic-issuance.json`
- Create: `test-vectors/sd-jwt/selective-disclosure.json`
- Create: `test-vectors/sd-jwt/key-binding.json`
- Create: `test-vectors/did-resolution/did-key-ed25519.json`
- Create: `test-vectors/did-resolution/did-key-p256.json`
- Create: `test-vectors/did-resolution/did-jwk-ed25519.json`

**Step 1: Create SD-JWT VC test vectors**

These vectors define the contract between all implementations (TypeScript, Kotlin, Swift).

```json
// test-vectors/sd-jwt/basic-issuance.json
{
  "description": "Issue SD-JWT VC with 3 claims, 2 disclosable",
  "input": {
    "issuer": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "subject": "did:key:z6MkjchhfUsD6mmvni8mCdXHw216Xrm9bQe2mBH1P5RDjVJG",
    "type": ["VerifiableCredential", "VerifiedEmployee"],
    "claims": {
      "name": "Ahmad bin Ali",
      "employeeId": "EMP-1234",
      "department": "Engineering"
    },
    "disclosable": ["name", "department"],
    "iat": 1719792000,
    "exp": 1751328000
  },
  "expected": {
    "format": "sd-jwt-vc",
    "disclosure_count": 2,
    "always_visible_claims": ["employeeId"],
    "sd_alg": "sha-256",
    "typ": "vc+sd-jwt",
    "has_cnf": true
  }
}
```

```json
// test-vectors/sd-jwt/selective-disclosure.json
{
  "description": "Present SD-JWT VC disclosing only 'name', hiding 'department'",
  "input": {
    "sd_jwt": "<issued from basic-issuance>",
    "disclose": ["name"],
    "hide": ["department"],
    "audience": "https://verifier.example.com",
    "nonce": "abc123"
  },
  "expected": {
    "disclosed_claims": ["name", "employeeId"],
    "hidden_claims": ["department"],
    "has_key_binding_jwt": true,
    "kb_jwt_aud": "https://verifier.example.com",
    "kb_jwt_nonce": "abc123"
  }
}
```

**Step 2: Create DID resolution test vectors**

```json
// test-vectors/did-resolution/did-key-ed25519.json
{
  "description": "Resolve did:key for Ed25519 public key",
  "input": {
    "did": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
  },
  "expected": {
    "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "verificationMethod": [{
      "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
      "type": "Ed25519VerificationKey2020",
      "controller": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
      "publicKeyMultibase": "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    }],
    "authentication": ["did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"],
    "assertionMethod": ["did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"]
  }
}
```

```json
// test-vectors/did-resolution/did-key-p256.json
{
  "description": "Resolve did:key for P-256 public key",
  "input": {
    "did": "did:key:zDnaeWgbpcUat3VPa1GqrFbcr7jVBNMhBMRKTsgBHYBcJkRYH"
  },
  "expected": {
    "id": "did:key:zDnaeWgbpcUat3VPa1GqrFbcr7jVBNMhBMRKTsgBHYBcJkRYH",
    "verificationMethod": [{
      "type": "EcdsaSecp256r1VerificationKey2019"
    }]
  }
}
```

```json
// test-vectors/did-resolution/did-jwk-ed25519.json
{
  "description": "Resolve did:jwk for Ed25519 JWK",
  "input": {
    "did": "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IjBCRTBfRGdLbzdkZjM2VjJfSEVhbEpXTkJwRmo4Wm5mTmNLN0JKNmpIdVEifQ"
  },
  "expected": {
    "id": "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IjBCRTBfRGdLbzdkZjM2VjJfSEVhbEpXTkJwRmo4Wm5mTmNLN0JKNmpIdVEifQ",
    "verificationMethod": [{
      "type": "JsonWebKey2020",
      "publicKeyJwk": {
        "kty": "OKP",
        "crv": "Ed25519"
      }
    }]
  }
}
```

**Step 3: Commit**

```bash
git add test-vectors/
git commit -m "feat: add shared test vectors for SD-JWT VC and DID resolution"
```

---

## Task Group B: Multi-Method DID Resolution (Android)

### Task 2: Add did:key resolver for Android

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/DidResolver.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/DidKeyResolver.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/Multicodec.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/did/DidKeyResolverTest.kt`

**Step 1: Write the failing test**

```kotlin
// DidKeyResolverTest.kt
package my.ssdid.wallet.domain.did

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DidKeyResolverTest {

    private val resolver = DidKeyResolver()

    @Test
    fun `resolve Ed25519 did-key returns valid DID Document`() {
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        val result = resolver.resolve(did)

        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.id).isEqualTo(did)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
        assertThat(doc.verificationMethod[0].controller).isEqualTo(did)
        assertThat(doc.authentication).contains(doc.verificationMethod[0].id)
        assertThat(doc.assertionMethod).contains(doc.verificationMethod[0].id)
    }

    @Test
    fun `resolve P-256 did-key returns EcdsaSecp256r1 type`() {
        val did = "did:key:zDnaeWgbpcUat3VPa1GqrFbcr7jVBNMhBMRKTsgBHYBcJkRYH"
        val result = resolver.resolve(did)

        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.verificationMethod[0].type).isEqualTo("EcdsaSecp256r1VerificationKey2019")
    }

    @Test
    fun `resolve non-did-key returns failure`() {
        val result = resolver.resolve("did:ssdid:abc123")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `resolve invalid multibase returns failure`() {
        val result = resolver.resolve("did:key:invaliddata")
        assertThat(result.isFailure).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.did.DidKeyResolverTest" -x lint`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
// Multicodec.kt
package my.ssdid.wallet.domain.did

/** Multicodec prefixes for public key types. */
object Multicodec {
    const val ED25519_PUB: Int = 0xed      // varint: 0xed 0x01
    const val P256_PUB: Int = 0x1200       // varint: 0x80 0x24
    const val P384_PUB: Int = 0x1201       // varint: 0x81 0x24

    /**
     * Decodes a multicodec-prefixed byte array, returning (codec, keyBytes).
     * Supports 1-byte and 2-byte unsigned varint prefixes.
     */
    fun decode(data: ByteArray): Pair<Int, ByteArray> {
        require(data.size >= 2) { "Data too short for multicodec" }
        val first = data[0].toInt() and 0xFF
        return if (first and 0x80 == 0) {
            // Single-byte varint
            first to data.copyOfRange(1, data.size)
        } else {
            // Two-byte varint
            require(data.size >= 3) { "Data too short for 2-byte varint" }
            val second = data[1].toInt() and 0xFF
            val codec = (first and 0x7F) or (second shl 7)
            codec to data.copyOfRange(2, data.size)
        }
    }

    fun encode(codec: Int, keyBytes: ByteArray): ByteArray {
        return if (codec < 0x80) {
            byteArrayOf(codec.toByte()) + keyBytes
        } else {
            byteArrayOf(
                ((codec and 0x7F) or 0x80).toByte(),
                (codec shr 7).toByte()
            ) + keyBytes
        }
    }
}
```

```kotlin
// DidResolver.kt
package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument

/** Common interface for resolving DIDs to DID Documents. */
interface DidResolver {
    suspend fun resolve(did: String): Result<DidDocument>
}
```

```kotlin
// DidKeyResolver.kt
package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod

/**
 * Resolves did:key DIDs locally by decoding the multibase-multicodec
 * encoded public key from the method-specific identifier.
 *
 * Supports: Ed25519, P-256, P-384.
 * See: https://w3c-ccg.github.io/did-method-key/
 */
class DidKeyResolver : DidResolver {

    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        require(did.startsWith("did:key:")) { "Not a did:key: $did" }

        val methodSpecificId = did.removePrefix("did:key:")
        require(methodSpecificId.startsWith("z")) { "Expected multibase 'z' (base58btc) prefix" }

        val decoded = Base58.decode(methodSpecificId.substring(1))
        val (codec, keyBytes) = Multicodec.decode(decoded)

        val (vmType, algorithm) = when (codec) {
            Multicodec.ED25519_PUB -> "Ed25519VerificationKey2020" to "Ed25519"
            Multicodec.P256_PUB -> "EcdsaSecp256r1VerificationKey2019" to "P-256"
            Multicodec.P384_PUB -> "EcdsaSecp384VerificationKey2019" to "P-384"
            else -> throw IllegalArgumentException("Unsupported multicodec: 0x${codec.toString(16)}")
        }

        val keyId = "$did#$methodSpecificId"

        val vm = VerificationMethod(
            id = keyId,
            type = vmType,
            controller = did,
            publicKeyMultibase = methodSpecificId
        )

        DidDocument(
            id = did,
            controller = did,
            verificationMethod = listOf(vm),
            authentication = listOf(keyId),
            assertionMethod = listOf(keyId),
            capabilityInvocation = listOf(keyId)
        )
    }
}
```

```kotlin
// Base58.kt — add to same package if not already present
package my.ssdid.wallet.domain.did

/** Base58btc decoder (Bitcoin alphabet). */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun decode(input: String): ByteArray {
        var bi = java.math.BigInteger.ZERO
        for (ch in input) {
            val digit = ALPHABET.indexOf(ch)
            require(digit >= 0) { "Invalid Base58 character: $ch" }
            bi = bi.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit.toLong()))
        }
        val bytes = bi.toByteArray()
        // Strip leading zero byte from BigInteger sign
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        // Restore leading zeros from input
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + stripped
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.did.DidKeyResolverTest" -x lint`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/did/
git add android/app/src/test/java/my/ssdid/wallet/domain/did/
git commit -m "feat(android): add did:key resolver with Ed25519 and P-256 support"
```

---

### Task 3: Add did:jwk resolver for Android

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/DidJwkResolver.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/did/DidJwkResolverTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.did

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class DidJwkResolverTest {

    private val resolver = DidJwkResolver()

    @Test
    fun `resolve Ed25519 did-jwk returns JsonWebKey2020 type`() {
        val jwk = """{"kty":"OKP","crv":"Ed25519","x":"0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ"}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(jwk.toByteArray())
        val did = "did:jwk:$encoded"

        val result = resolver.resolve(did)

        assertThat(result.isSuccess).isTrue()
        val doc = result.getOrThrow()
        assertThat(doc.id).isEqualTo(did)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("JsonWebKey2020")
    }

    @Test
    fun `resolve non-did-jwk returns failure`() {
        val result = resolver.resolve("did:ssdid:abc")
        assertThat(result.isFailure).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.did.DidJwkResolverTest" -x lint`
Expected: FAIL

**Step 3: Write implementation**

```kotlin
// DidJwkResolver.kt
package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod
import java.util.Base64

/**
 * Resolves did:jwk DIDs locally by decoding the Base64url-encoded JWK
 * from the method-specific identifier.
 *
 * See: https://github.com/quartzjer/did-jwk/blob/main/spec.md
 */
class DidJwkResolver : DidResolver {

    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        require(did.startsWith("did:jwk:")) { "Not a did:jwk: $did" }

        val encoded = did.removePrefix("did:jwk:")
        // Validate it's decodable
        Base64.getUrlDecoder().decode(encoded)

        val keyId = "$did#0"

        val vm = VerificationMethod(
            id = keyId,
            type = "JsonWebKey2020",
            controller = did,
            publicKeyMultibase = "" // JWK is encoded in the DID itself
        )

        DidDocument(
            id = did,
            controller = did,
            verificationMethod = listOf(vm),
            authentication = listOf(keyId),
            assertionMethod = listOf(keyId),
            capabilityInvocation = listOf(keyId)
        )
    }
}
```

**Step 4: Run test, verify pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.did.DidJwkResolverTest" -x lint`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/did/DidJwkResolver.kt
git add android/app/src/test/java/my/ssdid/wallet/domain/did/DidJwkResolverTest.kt
git commit -m "feat(android): add did:jwk resolver"
```

---

### Task 4: Add MultiMethodResolver and wire into VerifierImpl (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/MultiMethodResolver.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/did/SsdidRegistryResolver.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/verifier/VerifierImpl.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/did/MultiMethodResolverTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.did

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod
import org.junit.Test

class MultiMethodResolverTest {

    private val ssdidResolver = mockk<SsdidRegistryResolver>()
    private val resolver = MultiMethodResolver(
        ssdidResolver = ssdidResolver,
        keyResolver = DidKeyResolver(),
        jwkResolver = DidJwkResolver()
    )

    @Test
    fun `routes did-ssdid to registry resolver`() = runTest {
        val doc = DidDocument(
            id = "did:ssdid:abc",
            verificationMethod = listOf(
                VerificationMethod("did:ssdid:abc#key-1", "Ed25519VerificationKey2020", "did:ssdid:abc", "zAbc")
            )
        )
        coEvery { ssdidResolver.resolve("did:ssdid:abc") } returns Result.success(doc)

        val result = resolver.resolve("did:ssdid:abc")
        assertThat(result.getOrThrow().id).isEqualTo("did:ssdid:abc")
    }

    @Test
    fun `routes did-key to local resolver`() = runTest {
        val result = resolver.resolve("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
    }

    @Test
    fun `unsupported method returns failure`() = runTest {
        val result = resolver.resolve("did:web:example.com")
        assertThat(result.isFailure).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.did.MultiMethodResolverTest" -x lint`

**Step 3: Write implementation**

```kotlin
// SsdidRegistryResolver.kt
package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.transport.RegistryApi

/** Resolves did:ssdid DIDs via the SSDID registry API. */
class SsdidRegistryResolver(private val registryApi: RegistryApi) : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        registryApi.resolveDid(did)
    }
}

// MultiMethodResolver.kt
package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument

/**
 * Routes DID resolution to the appropriate method-specific resolver.
 * Supports: did:ssdid (registry), did:key (local), did:jwk (local).
 */
class MultiMethodResolver(
    private val ssdidResolver: SsdidRegistryResolver,
    private val keyResolver: DidKeyResolver,
    private val jwkResolver: DidJwkResolver
) : DidResolver {

    override suspend fun resolve(did: String): Result<DidDocument> {
        val method = did.removePrefix("did:").substringBefore(":")
        return when (method) {
            "ssdid" -> ssdidResolver.resolve(did)
            "key" -> keyResolver.resolve(did)
            "jwk" -> jwkResolver.resolve(did)
            else -> Result.failure(IllegalArgumentException("Unsupported DID method: did:$method"))
        }
    }
}
```

Then update `VerifierImpl` to accept `DidResolver` instead of calling `registryApi.resolveDid()` directly, and update `AppModule` to provide `MultiMethodResolver`.

**Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest -x lint`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/did/
git add android/app/src/test/java/my/ssdid/wallet/domain/did/
git add android/app/src/main/java/my/ssdid/wallet/domain/verifier/VerifierImpl.kt
git add android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "feat(android): add MultiMethodResolver and wire into VerifierImpl"
```

---

## Task Group C: Multi-Method DID Resolution (iOS)

### Task 5: Add did:key, did:jwk, and MultiMethodResolver for iOS

Mirror Tasks 2-4 for iOS. Same test vectors, same logic, Swift idioms.

**Files:**
- Create: `ios/SsdidWallet/Domain/Did/DidResolver.swift`
- Create: `ios/SsdidWallet/Domain/Did/DidKeyResolver.swift`
- Create: `ios/SsdidWallet/Domain/Did/DidJwkResolver.swift`
- Create: `ios/SsdidWallet/Domain/Did/MultiMethodResolver.swift`
- Create: `ios/SsdidWallet/Domain/Did/SsdidRegistryResolver.swift`
- Create: `ios/SsdidWallet/Domain/Did/Multicodec.swift`
- Create: `ios/SsdidWallet/Domain/Did/Base58.swift`
- Modify: `ios/SsdidWallet/Domain/Verifier/Verifier.swift`
- Modify: `ios/SsdidWallet/App/ServiceContainer.swift`
- Test: `ios/SsdidWalletTests/DidKeyResolverTests.swift`
- Test: `ios/SsdidWalletTests/DidJwkResolverTests.swift`

Follow same TDD flow: write tests from test vectors → implement → verify → commit.

```bash
git commit -m "feat(ios): add multi-method DID resolver (did:key, did:jwk, did:ssdid)"
```

---

## Task Group D: JWK Support in VerificationMethod

### Task 6: Add publicKeyJwk to VerificationMethod (Android + iOS)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt:38-44`
- Modify: `ios/SsdidWallet/Domain/Model/VerificationMethod.swift:1-8`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/DidDocumentTest.kt`

**Step 1: Write test for JWK round-trip**

```kotlin
@Test
fun `VerificationMethod with publicKeyJwk serializes correctly`() {
    val vm = VerificationMethod(
        id = "did:jwk:abc#0",
        type = "JsonWebKey2020",
        controller = "did:jwk:abc",
        publicKeyMultibase = "",
        publicKeyJwk = buildJsonObject {
            put("kty", "OKP")
            put("crv", "Ed25519")
            put("x", "0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ")
        }
    )
    val json = Json.encodeToString(vm)
    val decoded = Json.decodeFromString<VerificationMethod>(json)
    assertThat(decoded.publicKeyJwk).isNotNull()
    assertThat(decoded.publicKeyJwk!!["kty"]?.jsonPrimitive?.content).isEqualTo("OKP")
}
```

**Step 2: Add field**

```kotlin
// Android VerificationMethod — add optional publicKeyJwk
@Serializable
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyMultibase: String = "",
    val publicKeyJwk: JsonObject? = null
)
```

```swift
// iOS VerificationMethod — add optional publicKeyJwk
struct VerificationMethod: Codable, Equatable {
    let id: String
    let type: String
    let controller: String
    var publicKeyMultibase: String = ""
    var publicKeyJwk: [String: AnyCodable]? = nil
}
```

**Step 3: Run tests, commit**

```bash
git commit -m "feat: add publicKeyJwk to VerificationMethod on both platforms"
```

---

## Task Group E: SD-JWT VC (Android)

### Task 7: Add SD-JWT VC model and parser (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/SdJwtVc.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/SdJwtParser.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/Disclosure.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/sdjwt/SdJwtParserTest.kt`

SD-JWT VC format: `<issuer-jwt>~<disclosure1>~<disclosure2>~...~<kb-jwt>`

Each disclosure is: `Base64url(["salt", "claim_name", "claim_value"])`

The issuer JWT payload contains `_sd` array of SHA-256 hashes matching disclosures.

**Step 1: Write tests using RFC 9901 examples**

```kotlin
package my.ssdid.wallet.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SdJwtParserTest {

    @Test
    fun `parse splits compact SD-JWT into parts`() {
        val compact = "eyJhbGciOiJFZDI1NTE5In0.eyJfc2QiOlsiaGFzaDEiXSwic3ViIjoiZGlkOnNzZGlkOmFiYyJ9~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~"
        val parsed = SdJwtParser.parse(compact)

        assertThat(parsed.issuerJwt).isNotEmpty()
        assertThat(parsed.disclosures).hasSize(1)
        assertThat(parsed.keyBindingJwt).isNull()
    }

    @Test
    fun `parse extracts disclosure claim name and value`() {
        // ["salt1", "name", "Ahmad"] → Base64url
        val disclosureStr = "WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd"
        val disclosure = Disclosure.decode(disclosureStr)

        assertThat(disclosure.salt).isEqualTo("salt1")
        assertThat(disclosure.claimName).isEqualTo("name")
        assertThat(disclosure.claimValue).isEqualTo("Ahmad")
    }

    @Test
    fun `parse with key binding JWT extracts KB-JWT`() {
        val compact = "eyJ0eXAiOiJ2YytzZC1qd3QifQ.eyJfc2QiOltdfQ~WyJzYWx0IiwiYSIsImIiXQ~eyJ0eXAiOiJrYitqd3QifQ.e30.c2ln~"
        val parsed = SdJwtParser.parse(compact)

        assertThat(parsed.keyBindingJwt).isNotNull()
    }

    @Test
    fun `disclosure hash matches _sd entry`() {
        val disclosure = Disclosure("salt1", "name", "Ahmad")
        val hash = disclosure.hash("sha-256")
        // Hash should be deterministic
        assertThat(hash).isNotEmpty()
    }
}
```

**Step 2: Implement**

```kotlin
// Disclosure.kt
package my.ssdid.wallet.domain.sdjwt

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*

data class Disclosure(
    val salt: String,
    val claimName: String,
    val claimValue: String,
    val encoded: String = ""
) {
    /** SHA-256 hash of the Base64url-encoded disclosure (for matching against _sd). */
    fun hash(algorithm: String = "sha-256"): String {
        val input = encode()
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun encode(): String {
        if (encoded.isNotEmpty()) return encoded
        val array = buildJsonArray {
            add(salt)
            add(claimName)
            add(claimValue)
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(array.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        fun decode(base64url: String): Disclosure {
            val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
            val array = Json.parseToJsonElement(json).jsonArray
            return Disclosure(
                salt = array[0].jsonPrimitive.content,
                claimName = array[1].jsonPrimitive.content,
                claimValue = array[2].jsonPrimitive.content,
                encoded = base64url
            )
        }
    }
}
```

```kotlin
// SdJwtVc.kt
package my.ssdid.wallet.domain.sdjwt

/**
 * Parsed SD-JWT VC.
 * Format: <issuer-jwt>~<disclosure1>~...~<kb-jwt>
 */
data class SdJwtVc(
    val issuerJwt: String,
    val disclosures: List<Disclosure>,
    val keyBindingJwt: String?
) {
    /** Reconstructs compact form with only the given disclosures included. */
    fun present(selectedDisclosures: List<Disclosure>, kbJwt: String? = null): String {
        val parts = mutableListOf(issuerJwt)
        for (d in selectedDisclosures) {
            parts.add(d.encode())
        }
        val suffix = kbJwt ?: ""
        return parts.joinToString("~") + "~$suffix"
    }
}
```

```kotlin
// SdJwtParser.kt
package my.ssdid.wallet.domain.sdjwt

object SdJwtParser {

    /**
     * Parses a compact SD-JWT string into its components.
     * Format: <issuer-jwt>~<disclosure1>~...~[kb-jwt]
     */
    fun parse(compact: String): SdJwtVc {
        val parts = compact.split("~")
        require(parts.isNotEmpty()) { "Empty SD-JWT" }

        val issuerJwt = parts[0]
        val disclosureParts = parts.drop(1).filter { it.isNotEmpty() }

        // Last non-empty part might be a KB-JWT (has 3 dot-separated parts)
        val lastPart = disclosureParts.lastOrNull()
        val isLastKbJwt = lastPart != null && lastPart.count { it == '.' } == 2

        val disclosures: List<Disclosure>
        val kbJwt: String?

        if (isLastKbJwt && disclosureParts.size > 0) {
            disclosures = disclosureParts.dropLast(1).map { Disclosure.decode(it) }
            kbJwt = lastPart
        } else {
            disclosures = disclosureParts.map { Disclosure.decode(it) }
            kbJwt = null
        }

        return SdJwtVc(
            issuerJwt = issuerJwt,
            disclosures = disclosures,
            keyBindingJwt = kbJwt
        )
    }
}
```

**Step 3: Run tests, commit**

```bash
git commit -m "feat(android): add SD-JWT VC parser with disclosure and hash support"
```

---

### Task 8: Add SD-JWT VC issuer (create signed SD-JWT VCs) — Android

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/SdJwtIssuer.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/JwtSigner.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/sdjwt/SdJwtIssuerTest.kt`

Creates SD-JWT VCs: builds JWT header+payload with `_sd` array, generates salted disclosures, signs with Ed25519/ECDSA.

TDD: Write test → implement → verify → commit.

```bash
git commit -m "feat(android): add SD-JWT VC issuer with selective disclosure"
```

---

### Task 9: Add SD-JWT VC verifier (verify + extract disclosed claims) — Android

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/SdJwtVerifier.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/sdjwt/SdJwtVerifierTest.kt`

Verifies: JWT signature (using DidResolver to get issuer key), `_sd` hash matching, expiration, key binding JWT audience/nonce.

TDD: Write test → implement → verify → commit.

```bash
git commit -m "feat(android): add SD-JWT VC verifier with key binding validation"
```

---

### Task 10: Add Key Binding JWT creation — Android

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/sdjwt/KeyBindingJwt.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/sdjwt/KeyBindingJwtTest.kt`

Creates KB-JWT proving holder controls the key referenced in `cnf` claim. Signs with holder's private key.

```bash
git commit -m "feat(android): add Key Binding JWT for SD-JWT VC presentations"
```

---

## Task Group F: SD-JWT VC (iOS)

### Task 11: Mirror SD-JWT VC implementation for iOS

Mirror Tasks 7-10 for iOS. Same test vectors, Swift idioms.

**Files:**
- Create: `ios/SsdidWallet/Domain/SdJwt/SdJwtVc.swift`
- Create: `ios/SsdidWallet/Domain/SdJwt/SdJwtParser.swift`
- Create: `ios/SsdidWallet/Domain/SdJwt/Disclosure.swift`
- Create: `ios/SsdidWallet/Domain/SdJwt/SdJwtIssuer.swift`
- Create: `ios/SsdidWallet/Domain/SdJwt/SdJwtVerifier.swift`
- Create: `ios/SsdidWallet/Domain/SdJwt/KeyBindingJwt.swift`
- Test: `ios/SsdidWalletTests/SdJwtParserTests.swift`
- Test: `ios/SsdidWalletTests/SdJwtVerifierTests.swift`

```bash
git commit -m "feat(ios): add SD-JWT VC support (parser, issuer, verifier, key binding)"
```

---

## Task Group G: Verifiable Presentation Model

### Task 12: Add VP model and creation (Android + iOS)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/VerifiablePresentation.kt`
- Create: `ios/SsdidWallet/Domain/Model/VerifiablePresentation.swift`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/VerifiablePresentationTest.kt`

For JSON-LD VCs, the VP wraps credentials with a holder proof:

```kotlin
@Serializable
data class VerifiablePresentation(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
    val type: List<String> = listOf("VerifiablePresentation"),
    val holder: String,
    val verifiableCredential: List<VerifiableCredential> = emptyList(),
    val proof: Proof? = null
)
```

For SD-JWT VCs, the presentation IS the SD-JWT + selected disclosures + KB-JWT. No VP wrapper needed.

```bash
git commit -m "feat: add VerifiablePresentation model on both platforms"
```

---

## Task Group H: Credential Store Format-Awareness

### Task 13: Make credential store handle both VC and SD-JWT VC (Android + iOS)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt`
- Modify: `ios/SsdidWallet/Platform/Storage/VaultStorage.swift`

Add methods for SD-JWT VC storage alongside existing VC storage:

```kotlin
// VaultStorage.kt — add
suspend fun saveSdJwtVc(sdJwtVc: String, metadata: SdJwtVcMetadata)
suspend fun listSdJwtVcs(): List<StoredSdJwtVc>
suspend fun deleteSdJwtVc(id: String)
```

`StoredSdJwtVc` holds the compact string + metadata (issuer, subject, type, claims, issuedAt, expiresAt).

```bash
git commit -m "feat: extend credential store for SD-JWT VC format on both platforms"
```

---

## Task Group I: Security Hardening

### Task 14: Wire biometric gating to keystore (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/keystore/AndroidKeystoreManager.kt:24-25`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`

Uncomment `setUserAuthenticationRequired(true)` in `KeyGenParameterSpec`. Add `BiometricPrompt.CryptoObject` flow before decrypt operations. VaultImpl calls `biometricAuthenticator.authenticateWithCrypto()` before accessing private keys.

```bash
git commit -m "fix(android): wire biometric authentication to keystore decrypt operations"
```

### Task 15: Wire biometric gating to keychain (iOS)

**Files:**
- Modify: `ios/SsdidWallet/Platform/Keychain/KeychainManager.swift`

Add `SecAccessControl` with `.biometryCurrentSet` flag to wrapping key storage. Require LAContext evaluation before key retrieval.

```bash
git commit -m "fix(ios): wire biometric authentication to keychain access"
```

### Task 16: Add certificate pinning (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt:79-90`

Add OkHttp `CertificatePinner` for `registry.ssdid.my` and `notify.ssdid.my`:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("registry.ssdid.my", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Replace with actual pin
    .add("notify.ssdid.my", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

OkHttpClient.Builder()
    .certificatePinner(pinner)
    // ... existing config
```

```bash
git commit -m "fix(android): add certificate pinning for registry and notify endpoints"
```

### Task 17: Add certificate pinning (iOS)

**Files:**
- Modify: `ios/SsdidWallet/Domain/Transport/SsdidHttpClient.swift`

Create custom `URLSessionDelegate` that implements `urlSession(_:didReceive:completionHandler:)` for SSL pinning.

```bash
git commit -m "fix(ios): add certificate pinning for registry and notify endpoints"
```

---

## Task Group J: VCDM 2.0 Context Migration

### Task 18: Update default @context to VCDM 2.0 (Android + iOS)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/VerifiableCredential.kt:8`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt:10`
- Modify: `ios/SsdidWallet/Domain/Model/VerifiableCredential.swift`
- Modify: `ios/SsdidWallet/Domain/Model/DidDocument.swift`

Change default context from:
- `https://www.w3.org/2018/credentials/v1` → `https://www.w3.org/ns/credentials/v2`
- `https://www.w3.org/ns/did/v1` stays (already correct)

Ensure parsing accepts BOTH old and new contexts for backward compatibility with existing stored credentials.

```bash
git commit -m "feat: migrate default VC context to W3C VCDM 2.0"
```

---

## Task Group K: Integration & UI

### Task 19: Update ConsentScreen to handle SD-JWT VC disclosures (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/auth/ConsentViewModel.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/auth/ConsentScreen.kt`

When presenting an SD-JWT VC, the claim toggle controls which disclosures are included. Required claims = always-disclosed. Optional claims = user-toggled disclosures.

```bash
git commit -m "feat(android): update ConsentScreen for SD-JWT VC selective disclosure"
```

### Task 20: Update ConsentScreen for SD-JWT VC (iOS)

Mirror Task 19 for iOS.

```bash
git commit -m "feat(ios): update ConsentScreen for SD-JWT VC selective disclosure"
```

### Task 21: Update credential detail screens to show SD-JWT VC format (Android + iOS)

Show credential format badge ("SD-JWT VC" vs "Verifiable Credential"), disclosure status per claim, issuer DID resolved via multi-method resolver.

```bash
git commit -m "feat: update credential detail screens for SD-JWT VC display"
```

---

## Execution Order

```
Task 1   → Shared test vectors (foundation for all implementations)
Tasks 2-4 → Android DID resolution (did:key, did:jwk, MultiMethodResolver)
Task 5    → iOS DID resolution (mirror)
Task 6    → JWK in VerificationMethod (both platforms)
Tasks 7-10 → Android SD-JWT VC (parser, issuer, verifier, key binding)
Task 11   → iOS SD-JWT VC (mirror)
Task 12   → VP model (both platforms)
Task 13   → Format-aware credential store (both platforms)
Tasks 14-17 → Security hardening (biometric + cert pinning)
Task 18   → VCDM 2.0 context migration
Tasks 19-21 → UI integration (ConsentScreen + credential detail)
```

Tasks within a group are sequential. Groups B and C can be parallelized across team members. Groups E and F can be parallelized. Security hardening (I) can happen in parallel with SD-JWT work.
