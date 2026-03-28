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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.InetAddress
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Integration tests against the live SSDID registry at https://registry.ssdid.my.
 * Uses W3C Data Integrity signing payload: SHA3-256(canonical(proofOptions)) || SHA3-256(canonical(document)).
 * Skipped when the registry is unreachable.
 */
class RegistryIntegrationTest {

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

        classical = ClassicalProvider()
        pqc = PqcProvider()
    }

    private fun now(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

    /**
     * Creates a W3C Data Integrity proof with domain-separated signing payload.
     * Payload = SHA3-256(canonical(proofOptions)) || SHA3-256(canonical(document))
     */
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

    /**
     * Probes the registry for PQC support by attempting a ML-DSA-44 registration.
     * Skips the test if the registry's ApJavaCrypto GenServer is not running
     * (PQC verification requires the JRuby/BouncyCastle backend to be started
     * in the registry's supervision tree).
     */
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
        assumeTrue("Registry PQC verification unavailable (ApJavaCrypto GenServer not started in supervision tree)", supported)
    }

    private fun didDocToJsonObject(didDoc: DidDocument): JsonObject {
        val jsonStr = json.encodeToString(didDoc)
        return json.parseToJsonElement(jsonStr).jsonObject
    }

    /** Helper: register a DID with any algorithm and provider */
    private suspend fun registerAndResolve(algorithm: Algorithm, provider: CryptoProvider) {
        val keyPair = provider.generateKeyPair(algorithm)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val publicKeyMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, algorithm, publicKeyMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), algorithm, keyPair.privateKey, provider)

        val registerResp = registryApi.registerDid(RegisterDidRequest(didDoc, proof))
        assertThat(registerResp.did).isEqualTo(did.value)

        val resolved = registryApi.resolveDid(did.value)
        assertThat(resolved.id).isEqualTo(did.value)
        assertThat(resolved.verificationMethod).hasSize(1)
        assertThat(resolved.verificationMethod[0].id).isEqualTo(keyId)
        assertThat(resolved.verificationMethod[0].type).isEqualTo(algorithm.w3cType)
        assertThat(resolved.verificationMethod[0].publicKeyMultibase).isEqualTo(publicKeyMultibase)
    }

    // === Classical algorithms ===

    @Test
    fun `register and resolve DID with Ed25519`() = runTest {
        registerAndResolve(Algorithm.ED25519, classical)
    }

    @Test
    fun `register and resolve DID with ECDSA P-256`() = runTest {
        registerAndResolve(Algorithm.ECDSA_P256, classical)
    }

    @Test
    fun `register and resolve DID with ECDSA P-384`() = runTest {
        registerAndResolve(Algorithm.ECDSA_P384, classical)
    }

    // === ML-DSA (FIPS 204) ===
    // These require ApJavaCrypto on the registry. Skipped if registry returns 401.

    @Test
    fun `register and resolve DID with ML-DSA-44`() = runTest {
        assumePqcSupported()
        registerAndResolve(Algorithm.ML_DSA_44, pqc)
    }

    @Test
    fun `register and resolve DID with ML-DSA-65`() = runTest {
        assumePqcSupported()
        registerAndResolve(Algorithm.ML_DSA_65, pqc)
    }

    @Test
    fun `register and resolve DID with ML-DSA-87`() = runTest {
        assumePqcSupported()
        registerAndResolve(Algorithm.ML_DSA_87, pqc)
    }

    // === SLH-DSA (FIPS 205) ===

    @Test
    fun `register and resolve DID with SLH-DSA-SHA2-128s`() = runTest {
        assumePqcSupported()
        registerAndResolve(Algorithm.SLH_DSA_SHA2_128S, pqc)
    }

    @Test
    fun `register and resolve DID with SLH-DSA-SHAKE-128f`() = runTest {
        assumePqcSupported()
        registerAndResolve(Algorithm.SLH_DSA_SHAKE_128F, pqc)
    }

    // === DID Update (requires challenge) ===

    @Test
    fun `update DID document adds new verification method`() = runTest {
        val keyPair1 = classical.generateKeyPair(Algorithm.ED25519)
        val did = Did.generate()
        val keyId1 = did.keyId(1)
        val pubMultibase1 = Multibase.encode(keyPair1.publicKey)

        val didDoc1 = DidDocument.build(did, keyId1, Algorithm.ED25519, pubMultibase1)
        val proof1 = createW3CProof(keyId1, didDocToJsonObject(didDoc1), Algorithm.ED25519, keyPair1.privateKey, classical)
        registryApi.registerDid(RegisterDidRequest(didDoc1, proof1))

        val challengeResp = registryApi.createChallenge(did.value)

        val keyPair2 = classical.generateKeyPair(Algorithm.ED25519)
        val keyId2 = did.keyId(2)
        val pubMultibase2 = Multibase.encode(keyPair2.publicKey)

        val updatedDoc = DidDocument(
            id = did.value,
            controller = did.value,
            verificationMethod = listOf(
                VerificationMethod(keyId1, "Ed25519VerificationKey2020", did.value, pubMultibase1),
                VerificationMethod(keyId2, "Ed25519VerificationKey2020", did.value, pubMultibase2)
            ),
            authentication = listOf(keyId1, keyId2),
            assertionMethod = listOf(keyId1, keyId2),
            capabilityInvocation = listOf(keyId1, keyId2)
        )
        val updateProof = createW3CProof(
            keyId1, didDocToJsonObject(updatedDoc), Algorithm.ED25519, keyPair1.privateKey, classical,
            proofPurpose = "capabilityInvocation",
            challenge = challengeResp.challenge,
            domain = "registry.ssdid.example"
        )
        val updateResp = registryApi.updateDid(did.value, UpdateDidRequest(updatedDoc, updateProof))
        assertThat(updateResp.did).isEqualTo(did.value)

        val resolved = registryApi.resolveDid(did.value)
        assertThat(resolved.verificationMethod).hasSize(2)
    }

    // === Challenge Endpoint ===

    @Test
    fun `create challenge for registered DID`() = runTest {
        val keyPair = classical.generateKeyPair(Algorithm.ED25519)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, keyPair.privateKey, classical)
        registryApi.registerDid(RegisterDidRequest(didDoc, proof))

        val challengeResp = registryApi.createChallenge(did.value)
        assertThat(challengeResp.challenge).isNotEmpty()
    }

    // === Error Cases ===

    @Test
    fun `resolve non-existent DID returns error`() = runTest {
        val result = runCatching { registryApi.resolveDid("did:ssdid:nonexistent_did_12345") }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `register DID with invalid proof is rejected`() = runTest {
        val keyPair = classical.generateKeyPair(Algorithm.ED25519)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibase)

        val wrongKeyPair = classical.generateKeyPair(Algorithm.ED25519)
        val badProof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, wrongKeyPair.privateKey, classical)

        val result = runCatching { registryApi.registerDid(RegisterDidRequest(didDoc, badProof)) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `register duplicate DID is rejected`() = runTest {
        val keyPair = classical.generateKeyPair(Algorithm.ED25519)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, keyPair.privateKey, classical)
        registryApi.registerDid(RegisterDidRequest(didDoc, proof))

        val proof2 = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, keyPair.privateKey, classical)
        val result = runCatching { registryApi.registerDid(RegisterDidRequest(didDoc, proof2)) }
        assertThat(result.isFailure).isTrue()
    }

    // === KAZ-Sign (Malaysian PQC) ===

    /**
     * Probes the registry for KAZ-Sign support. Skipped if the native
     * KAZ-Sign library is not available (JNI not loaded in unit-test JVM)
     * or the registry cannot verify KAZ-Sign signatures.
     */
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

    @Test
    fun `register and resolve DID with KAZ-SIGN-128`() = runTest {
        assumeKazSignSupported()
        registerAndResolve(Algorithm.KAZ_SIGN_128, pqc)
    }

    @Test
    fun `register and resolve DID with KAZ-SIGN-192`() = runTest {
        assumeKazSignSupported()
        registerAndResolve(Algorithm.KAZ_SIGN_192, pqc)
    }

    @Test
    fun `register and resolve DID with KAZ-SIGN-256`() = runTest {
        assumeKazSignSupported()
        registerAndResolve(Algorithm.KAZ_SIGN_256, pqc)
    }

    // === DID Deactivation ===

    @Test
    fun `deactivate DID makes it unresolvable`() = runTest {
        // Register a fresh DID
        val keyPair = classical.generateKeyPair(Algorithm.ED25519)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val pubMultibase = Multibase.encode(keyPair.publicKey)

        val didDoc = DidDocument.build(did, keyId, Algorithm.ED25519, pubMultibase)
        val proof = createW3CProof(keyId, didDocToJsonObject(didDoc), Algorithm.ED25519, keyPair.privateKey, classical)
        registryApi.registerDid(RegisterDidRequest(didDoc, proof))

        // Verify it resolves
        val resolved = registryApi.resolveDid(did.value)
        assertThat(resolved.id).isEqualTo(did.value)

        // Request challenge for deactivation
        val challengeResp = registryApi.createChallenge(did.value)
        assertThat(challengeResp.challenge).isNotEmpty()

        // Build deactivation proof with capabilityInvocation purpose
        val deactivateData = buildJsonObject {
            put("action", "deactivate")
            put("did", did.value)
        }
        val deactivateProof = createW3CProof(
            keyId, deactivateData, Algorithm.ED25519, keyPair.privateKey, classical,
            proofPurpose = "capabilityInvocation",
            challenge = challengeResp.challenge,
            domain = challengeResp.domain
        )
        registryApi.deactivateDid(did.value, DeactivateDidRequest(deactivateProof))

        // Verify DID is no longer resolvable
        val resolveResult = runCatching { registryApi.resolveDid(did.value) }
        assertThat(resolveResult.isFailure).isTrue()
    }
}
