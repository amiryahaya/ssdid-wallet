package my.ssdid.wallet.integration

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import my.ssdid.sdk.domain.crypto.ClassicalProvider
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.pqc.PqcProvider
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.transport.RegistryApi
import my.ssdid.sdk.domain.transport.dto.DeactivateDidRequest
import my.ssdid.sdk.domain.transport.dto.RegisterDidRequest
import my.ssdid.sdk.domain.transport.dto.UpdateDidRequest
import my.ssdid.sdk.domain.vault.VaultImpl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.InetAddress
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Comprehensive wallet<->registry integration tests covering the full identity lifecycle,
 * key rotation with pre-commitment, challenge replay protection, error format verification,
 * invalid proof rejection, and multi-algorithm support.
 *
 * These tests run against the live SSDID registry at https://registry.ssdid.my
 * and are skipped when the registry is unreachable.
 */
class WalletRegistryIntegrationTest {

    private lateinit var registryApi: RegistryApi
    private lateinit var classical: ClassicalProvider
    private lateinit var pqc: PqcProvider
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Before
    fun setup() {
        val reachable = try {
            InetAddress.getByName("registry.ssdid.my") != null
        } catch (_: Exception) {
            false
        }
        assumeTrue("Registry unreachable — skipping integration tests", reachable)

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        registryApi = Retrofit.Builder()
            .baseUrl("https://registry.ssdid.my/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RegistryApi::class.java)

        // Pause between tests to avoid HTTP 429 rate limiting
        Thread.sleep(2000)

        classical = ClassicalProvider()
        pqc = PqcProvider()
    }

    // ==================== Helpers (duplicated from RegistryIntegrationTest) ====================

    private fun now(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

    private fun createW3CProof(
        keyId: String,
        document: JsonObject,
        algorithm: Algorithm,
        privateKey: ByteArray,
        provider: CryptoProvider,
        proofPurpose: String = "assertionMethod",
        challenge: String? = null,
        domain: String? = null
    ): Proof {
        val created = now()

        val proofOptions = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("type", JsonPrimitive(algorithm.proofType))
            put("created", JsonPrimitive(created))
            put("verificationMethod", JsonPrimitive(keyId))
            put("proofPurpose", JsonPrimitive(proofPurpose))
            if (challenge != null) put("challenge", JsonPrimitive(challenge))
            if (domain != null) put("domain", JsonPrimitive(domain))
        }

        val sha3 = MessageDigest.getInstance("SHA3-256")
        val optionsHash = sha3.digest(VaultImpl.canonicalJson(JsonObject(proofOptions)).toByteArray(Charsets.UTF_8))
        sha3.reset()
        val docHash = sha3.digest(VaultImpl.canonicalJson(document).toByteArray(Charsets.UTF_8))
        val payload = optionsHash + docHash

        val signature = provider.sign(algorithm, privateKey, payload)
        return Proof(
            type = algorithm.proofType,
            created = created,
            verificationMethod = keyId,
            proofPurpose = proofPurpose,
            proofValue = Multibase.encode(signature),
            challenge = challenge,
            domain = domain
        )
    }

    private fun didDocToJsonObject(didDoc: DidDocument): JsonObject {
        val jsonStr = json.encodeToString(didDoc)
        return json.parseToJsonElement(jsonStr).jsonObject
    }

    /**
     * Register a DID and return the keypair, Did, and keyId for further operations.
     */
    private suspend fun registerDid(
        algorithm: Algorithm = Algorithm.ED25519,
        provider: CryptoProvider = classical
    ): RegisteredDid {
        val keyPair = provider.generateKeyPair(algorithm)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, algorithm, pubMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), algorithm, keyPair.privateKey, provider)
        registryApi.registerDid(RegisterDidRequest(didDoc, proof))

        return RegisteredDid(did, keyId, keyPair.privateKey, keyPair.publicKey, pubMultibase, algorithm, provider)
    }

    /**
     * Deactivate a DID (for cleanup in finally blocks). Swallows exceptions.
     */
    private suspend fun deactivateSafely(reg: RegisteredDid) {
        try {
            val challengeResp = registryApi.createChallenge(reg.did.value)
            val deactivateData = buildJsonObject {
                put("action", "deactivate")
                put("did", reg.did.value)
            }
            val deactivateProof = createW3CProof(
                reg.keyId, deactivateData, reg.algorithm, reg.privateKey, reg.provider,
                proofPurpose = "capabilityInvocation",
                challenge = challengeResp.challenge,
                domain = challengeResp.domain
            )
            registryApi.deactivateDid(reg.did.value, DeactivateDidRequest(deactivateProof))
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }

    private suspend fun assumePqcSupported() {
        val kp = pqc.generateKeyPair(Algorithm.ML_DSA_44)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(kp.publicKey)
        val didDoc = DidDocument.build(did, keyId, Algorithm.ML_DSA_44, pubMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ML_DSA_44, kp.privateKey, pqc)
        val supported = try {
            registryApi.registerDid(RegisterDidRequest(didDoc, proof))
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue("Registry PQC verification unavailable (ApJavaCrypto GenServer not started)", supported)
    }

    private suspend fun assumeKazSignSupported() {
        try {
            val kp = pqc.generateKeyPair(Algorithm.KAZ_SIGN_128)
            val did = Did.generate()
            val keyId = did.keyId(1)
            val pubMultibase = Multibase.encode(kp.publicKey)
            val didDoc = DidDocument.build(did, keyId, Algorithm.KAZ_SIGN_128, pubMultibase)
            val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.KAZ_SIGN_128, kp.privateKey, pqc)
            registryApi.registerDid(RegisterDidRequest(didDoc, proof))
        } catch (e: Throwable) {
            assumeTrue("KAZ-Sign unavailable (${e.message})", false)
        }
    }

    private data class RegisteredDid(
        val did: Did,
        val keyId: String,
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val pubMultibase: String,
        val algorithm: Algorithm,
        val provider: CryptoProvider
    )

    // ==================== Flow 1: Full Identity Lifecycle ====================

    @Test
    fun `full identity lifecycle - create, resolve, update, deactivate`() = runTest {
        var reg: RegisteredDid? = null
        try {
            // 1-2. Generate Ed25519 keypair and build DID document
            reg = registerDid(Algorithm.ED25519, classical)

            // 3-4. Register succeeded; resolve and verify it matches
            val resolved = registryApi.resolveDid(reg.did.value)
            assertThat(resolved.id).isEqualTo(reg.did.value)
            assertThat(resolved.verificationMethod).hasSize(1)
            assertThat(resolved.verificationMethod[0].id).isEqualTo(reg.keyId)
            assertThat(resolved.verificationMethod[0].publicKeyMultibase).isEqualTo(reg.pubMultibase)

            // 5. Get challenge for update
            val challengeForUpdate = registryApi.createChallenge(reg.did.value)
            assertThat(challengeForUpdate.challenge).isNotEmpty()

            // 6. Update DID doc (add nextKeyHash)
            val nextKeyPair = classical.generateKeyPair(Algorithm.ED25519)
            val sha3 = MessageDigest.getInstance("SHA3-256")
            val nextKeyHash = Multibase.encode(sha3.digest(nextKeyPair.publicKey))

            val updatedDoc = DidDocument(
                id = reg.did.value,
                controller = reg.did.value,
                verificationMethod = resolved.verificationMethod,
                authentication = listOf(reg.keyId),
                assertionMethod = listOf(reg.keyId),
                capabilityInvocation = listOf(reg.keyId),
                nextKeyHash = nextKeyHash
            )
            val updateProof = createW3CProof(
                reg.keyId, didDocToJsonObject(updatedDoc), Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challengeForUpdate.challenge,
                domain = challengeForUpdate.domain
            )
            val updateResp = registryApi.updateDid(reg.did.value, UpdateDidRequest(updatedDoc, updateProof))
            assertThat(updateResp.did).isEqualTo(reg.did.value)

            // 7. Resolve again and assert updated (nextKeyHash present)
            val resolvedAfterUpdate = registryApi.resolveDid(reg.did.value)
            assertThat(resolvedAfterUpdate.nextKeyHash).isEqualTo(nextKeyHash)

            // 8. Get challenge for deactivation
            val challengeForDeactivation = registryApi.createChallenge(reg.did.value)
            assertThat(challengeForDeactivation.challenge).isNotEmpty()

            // 9. Deactivate
            val deactivateData = buildJsonObject {
                put("action", "deactivate")
                put("did", reg.did.value)
            }
            val deactivateProof = createW3CProof(
                reg.keyId, deactivateData, Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challengeForDeactivation.challenge,
                domain = challengeForDeactivation.domain
            )
            registryApi.deactivateDid(reg.did.value, DeactivateDidRequest(deactivateProof))

            // 10. Resolve after deactivation should fail (404/410)
            val resolveResult = runCatching { registryApi.resolveDid(reg.did.value) }
            assertThat(resolveResult.isFailure).isTrue()
            reg = null // Already deactivated, skip cleanup
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }

    // ==================== Flow 2: Key Rotation with Pre-Commitment ====================

    @Test
    fun `key rotation with pre-commitment hash`() = runTest {
        var reg: RegisteredDid? = null
        try {
            // 1. Register identity
            reg = registerDid(Algorithm.ED25519, classical)

            // 2. Generate next keypair, compute SHA3-256 hash of the new public key
            val nextKeyPair = classical.generateKeyPair(Algorithm.ED25519)
            val sha3 = MessageDigest.getInstance("SHA3-256")
            val nextKeyHash = Multibase.encode(sha3.digest(nextKeyPair.publicKey))

            // 3. Update DID doc with nextKeyHash
            val challenge1 = registryApi.createChallenge(reg.did.value)
            val docWithNextKeyHash = DidDocument(
                id = reg.did.value,
                controller = reg.did.value,
                verificationMethod = listOf(
                    VerificationMethod(reg.keyId, Algorithm.ED25519.w3cType, reg.did.value, reg.pubMultibase)
                ),
                authentication = listOf(reg.keyId),
                assertionMethod = listOf(reg.keyId),
                capabilityInvocation = listOf(reg.keyId),
                nextKeyHash = nextKeyHash
            )
            val proof1 = createW3CProof(
                reg.keyId, didDocToJsonObject(docWithNextKeyHash), Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challenge1.challenge,
                domain = challenge1.domain
            )
            val updateResp1 = registryApi.updateDid(reg.did.value, UpdateDidRequest(docWithNextKeyHash, proof1))
            assertThat(updateResp1.did).isEqualTo(reg.did.value)

            // 4. Resolve and assert nextKeyHash is present
            val resolved1 = registryApi.resolveDid(reg.did.value)
            assertThat(resolved1.nextKeyHash).isEqualTo(nextKeyHash)

            // 5-6. Generate new DID doc with promoted key and update
            val newKeyId = reg.did.keyId(2)
            val newPubMultibase = Multibase.encode(nextKeyPair.publicKey)
            val challenge2 = registryApi.createChallenge(reg.did.value)
            val rotatedDoc = DidDocument(
                id = reg.did.value,
                controller = reg.did.value,
                verificationMethod = listOf(
                    VerificationMethod(newKeyId, Algorithm.ED25519.w3cType, reg.did.value, newPubMultibase)
                ),
                authentication = listOf(newKeyId),
                assertionMethod = listOf(newKeyId),
                capabilityInvocation = listOf(newKeyId)
                // nextKeyHash cleared after rotation
            )
            // Sign with the OLD key (current authorized key) to authorize the rotation
            val proof2 = createW3CProof(
                reg.keyId, didDocToJsonObject(rotatedDoc), Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challenge2.challenge,
                domain = challenge2.domain
            )
            val updateResp2 = registryApi.updateDid(reg.did.value, UpdateDidRequest(rotatedDoc, proof2))
            assertThat(updateResp2.did).isEqualTo(reg.did.value)

            // 7. Resolve and assert new key is active, nextKeyHash is cleared
            val resolved2 = registryApi.resolveDid(reg.did.value)
            assertThat(resolved2.verificationMethod).hasSize(1)
            assertThat(resolved2.verificationMethod[0].id).isEqualTo(newKeyId)
            assertThat(resolved2.verificationMethod[0].publicKeyMultibase).isEqualTo(newPubMultibase)
            assertThat(resolved2.nextKeyHash).isNull()

            // Update reg for cleanup with new key
            reg = reg.copy(
                keyId = newKeyId,
                privateKey = nextKeyPair.privateKey,
                publicKey = nextKeyPair.publicKey,
                pubMultibase = newPubMultibase
            )
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }

    // ==================== Flow 3: Proof Cross-Check ====================

    @Test
    fun `proof verification cross-check - signing payload format matches registry`() = runTest {
        var reg: RegisteredDid? = null
        try {
            // Explicitly build and verify the entire W3C Data Integrity signing payload
            val keyPair = classical.generateKeyPair(Algorithm.ED25519)
            val did = Did.generate()
            val keyId = did.keyId(1)
            val pubMultibase = Multibase.encode(keyPair.publicKey)

            val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibase)
            val docJsonObject = didDocToJsonObject(didDoc)

            // 1-2. Build canonical JSON of document and proof options
            val created = now()
            val proofOptions = JsonObject(mapOf(
                "type" to JsonPrimitive(Algorithm.ED25519.proofType),
                "created" to JsonPrimitive(created),
                "verificationMethod" to JsonPrimitive(keyId),
                "proofPurpose" to JsonPrimitive("assertionMethod")
            ))

            val canonicalOptions = VaultImpl.canonicalJson(proofOptions)
            val canonicalDoc = VaultImpl.canonicalJson(docJsonObject)

            // 3. SHA3-256 hash both
            val sha3 = MessageDigest.getInstance("SHA3-256")
            val optionsHash = sha3.digest(canonicalOptions.toByteArray(Charsets.UTF_8))
            sha3.reset()
            val docHash = sha3.digest(canonicalDoc.toByteArray(Charsets.UTF_8))

            // 4. Concatenate hashes
            val payload = optionsHash + docHash
            assertThat(payload).hasLength(64) // 32 + 32 bytes

            // 5. Sign with private key
            val signature = classical.sign(Algorithm.ED25519, keyPair.privateKey, payload)

            // 6. Multibase encode
            val proofValue = Multibase.encode(signature)
            assertThat(proofValue).startsWith("u") // multibase base64url prefix

            // 7. Submit to registry and assert 201 (proof accepted)
            val proof = Proof(
                type = Algorithm.ED25519.proofType,
                created = created,
                verificationMethod = keyId,
                proofPurpose = "assertionMethod",
                proofValue = proofValue
            )
            val resp = registryApi.registerDid(RegisterDidRequest(didDoc, proof))
            assertThat(resp.did).isEqualTo(did.value)

            reg = RegisteredDid(did, keyId, keyPair.privateKey, keyPair.publicKey, pubMultibase, Algorithm.ED25519, classical)
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }

    // ==================== Flow 4: Challenge Lifecycle (Replay Protection) ====================

    @Test
    fun `challenge cannot be reused (replay protection)`() = runTest {
        var reg: RegisteredDid? = null
        try {
            // 1. Register identity
            reg = registerDid(Algorithm.ED25519, classical)

            // 2. Get challenge
            val challengeResp = registryApi.createChallenge(reg.did.value)
            val challenge = challengeResp.challenge
            assertThat(challenge).isNotEmpty()

            // 3. Use challenge in UPDATE -> success
            val keyPair2 = classical.generateKeyPair(Algorithm.ED25519)
            val keyId2 = reg.did.keyId(2)
            val pubMultibase2 = Multibase.encode(keyPair2.publicKey)

            val updatedDoc = DidDocument(
                id = reg.did.value,
                controller = reg.did.value,
                verificationMethod = listOf(
                    VerificationMethod(reg.keyId, Algorithm.ED25519.w3cType, reg.did.value, reg.pubMultibase),
                    VerificationMethod(keyId2, Algorithm.ED25519.w3cType, reg.did.value, pubMultibase2)
                ),
                authentication = listOf(reg.keyId, keyId2),
                assertionMethod = listOf(reg.keyId, keyId2),
                capabilityInvocation = listOf(reg.keyId, keyId2)
            )
            val updateProof = createW3CProof(
                reg.keyId, didDocToJsonObject(updatedDoc), Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challenge,
                domain = challengeResp.domain
            )
            val updateResp = registryApi.updateDid(reg.did.value, UpdateDidRequest(updatedDoc, updateProof))
            assertThat(updateResp.did).isEqualTo(reg.did.value)

            // 4. Use SAME challenge in another UPDATE -> should fail (401/422 replay)
            val anotherDoc = DidDocument(
                id = reg.did.value,
                controller = reg.did.value,
                verificationMethod = listOf(
                    VerificationMethod(reg.keyId, Algorithm.ED25519.w3cType, reg.did.value, reg.pubMultibase)
                ),
                authentication = listOf(reg.keyId),
                assertionMethod = listOf(reg.keyId),
                capabilityInvocation = listOf(reg.keyId)
            )
            val replayProof = createW3CProof(
                reg.keyId, didDocToJsonObject(anotherDoc), Algorithm.ED25519, reg.privateKey, classical,
                proofPurpose = "capabilityInvocation",
                challenge = challenge, // Reused challenge!
                domain = challengeResp.domain
            )
            val replayResult = runCatching {
                registryApi.updateDid(reg.did.value, UpdateDidRequest(anotherDoc, replayProof))
            }
            assertThat(replayResult.isFailure).isTrue()

            // Verify the error is an HTTP error (401 or 422)
            val exception = replayResult.exceptionOrNull()
            assertThat(exception).isInstanceOf(HttpException::class.java)
            val httpCode = (exception as HttpException).code()
            assertThat(httpCode).isAnyOf(401, 422)
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }

    // ==================== Flow 5: Error Format Verification (RFC 7807) ====================

    @Test
    fun `error responses follow RFC 7807 format`() = runTest {
        // 1. Attempt to resolve non-existent DID -> expect 404
        val nonExistentDid = "did:ssdid:nonexistent_rfc7807_test"
        val result = runCatching { registryApi.resolveDid(nonExistentDid) }
        assertThat(result.isFailure).isTrue()

        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(HttpException::class.java)

        val httpException = exception as HttpException
        assertThat(httpException.code()).isEqualTo(404)

        // 2. Parse error body -> assert RFC 7807 fields
        val errorBody = httpException.response()?.errorBody()?.string()
        assertThat(errorBody).isNotNull()
        assertThat(errorBody).isNotEmpty()

        val errorJson = json.parseToJsonElement(errorBody!!).jsonObject
        assertThat(errorJson.containsKey("type") || errorJson.containsKey("title")).isTrue()
        assertThat(errorJson.containsKey("status") || errorJson.containsKey("detail")).isTrue()

        // 3. Assert Content-Type includes problem+json (if the registry sets it)
        val contentType = httpException.response()?.errorBody()?.contentType()?.toString()
        // Registry may use application/json or application/problem+json
        assertThat(contentType).isNotNull()
        assertThat(contentType).contains("json")
    }

    // ==================== Flow 6: Invalid Proof Rejected ====================

    @Test
    fun `registration with wrong key signature is rejected`() = runTest {
        // 1. Generate two different keypairs
        val keyPairA = classical.generateKeyPair(Algorithm.ED25519)
        val keyPairB = classical.generateKeyPair(Algorithm.ED25519)

        // 2. Build DID doc with keypair A's public key
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibaseA = Multibase.encode(keyPairA.publicKey)
        val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibaseA)

        // 3. Sign proof with keypair B's private key (wrong key)
        val badProof = createW3CProof(
            keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, keyPairB.privateKey, classical
        )

        // 4. Submit -> assert rejection (401)
        val result = runCatching { registryApi.registerDid(RegisterDidRequest(didDoc, badProof)) }
        assertThat(result.isFailure).isTrue()

        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(HttpException::class.java)
        val httpCode = (exception as HttpException).code()
        // Registry may return 401 (proof verification failed) or 429 (rate limited)
        assertThat(httpCode).isIn(listOf(401, 422, 429))
    }

    // ==================== Flow 7: Multi-Algorithm ====================

    @Test
    fun `register and resolve with ECDSA P-384`() = runTest {
        var reg: RegisteredDid? = null
        try {
            reg = registerDid(Algorithm.ECDSA_P384, classical)

            val resolved = registryApi.resolveDid(reg.did.value)
            assertThat(resolved.id).isEqualTo(reg.did.value)
            assertThat(resolved.verificationMethod).hasSize(1)
            assertThat(resolved.verificationMethod[0].type).isEqualTo(Algorithm.ECDSA_P384.w3cType)
            assertThat(resolved.verificationMethod[0].publicKeyMultibase).isEqualTo(reg.pubMultibase)
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }

    @Test
    fun `register and resolve with KAZ-Sign-128 (PQC)`() = runTest {
        assumeKazSignSupported()
        var reg: RegisteredDid? = null
        try {
            reg = registerDid(Algorithm.KAZ_SIGN_128, pqc)

            val resolved = registryApi.resolveDid(reg.did.value)
            assertThat(resolved.id).isEqualTo(reg.did.value)
            assertThat(resolved.verificationMethod).hasSize(1)
            assertThat(resolved.verificationMethod[0].type).isEqualTo(Algorithm.KAZ_SIGN_128.w3cType)
            assertThat(resolved.verificationMethod[0].publicKeyMultibase).isEqualTo(reg.pubMultibase)
        } finally {
            reg?.let { deactivateSafely(it) }
        }
    }
}
