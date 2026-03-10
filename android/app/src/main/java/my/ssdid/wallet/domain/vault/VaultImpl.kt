package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class VaultImpl(
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider,
    private val keystoreManager: KeystoreManager,
    private val storage: VaultStorage
) : Vault {

    private fun providerFor(algorithm: Algorithm): CryptoProvider {
        return if (algorithm.isPostQuantum) pqcProvider else classicalProvider
    }

    override suspend fun createIdentity(name: String, algorithm: Algorithm): Result<Identity> = runCatching {
        val existing = storage.listIdentities().any { it.name.equals(name, ignoreCase = true) }
        require(!existing) { "An identity with the name \"$name\" already exists" }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "vault"; message = "Generating key pair"; level = SentryLevel.INFO
            data["algorithm"] = algorithm.name; data["isPostQuantum"] = algorithm.isPostQuantum.toString()
        })
        val provider = providerFor(algorithm)
        val keyPair = provider.generateKeyPair(algorithm)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val publicKeyMultibase = Multibase.encode(keyPair.publicKey)

        val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
        keystoreManager.generateWrappingKey(wrappingAlias)
        val encryptedPrivateKey = keystoreManager.encrypt(wrappingAlias, keyPair.privateKey)

        keyPair.privateKey.fill(0) // Zero from memory

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val identity = Identity(
            name = name,
            did = did.value,
            keyId = keyId,
            algorithm = algorithm,
            publicKeyMultibase = publicKeyMultibase,
            createdAt = now
        )
        storage.saveIdentity(identity, encryptedPrivateKey)
        identity
    }

    override suspend fun getIdentity(keyId: String): Identity? = storage.getIdentity(keyId)

    override suspend fun listIdentities(): List<Identity> = storage.listIdentities()

    override suspend fun deleteIdentity(keyId: String): Result<Unit> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val did = Did(identity.did)
        keystoreManager.deleteKey("ssdid_wrap_${did.methodSpecificId()}")
        storage.deleteIdentity(keyId)
    }

    override suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val did = Did(identity.did)
        val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
        val encryptedPrivateKey = storage.getEncryptedPrivateKey(keyId)
            ?: throw IllegalStateException("Private key not found for: $keyId")
        val privateKey = keystoreManager.decrypt(wrappingAlias, encryptedPrivateKey)
        try {
            providerFor(identity.algorithm).sign(identity.algorithm, privateKey, data)
        } finally {
            privateKey.fill(0)
        }
    }

    override suspend fun buildDidDocument(keyId: String): Result<DidDocument> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val did = Did(identity.did)

        // Check for pre-rotated key hash
        val nextKeyHash = if (identity.preRotatedKeyId != null) {
            val preRotated = storage.getPreRotatedKey(identity.preRotatedKeyId!!)
            if (preRotated != null) {
                val sha3 = MessageDigest.getInstance("SHA3-256")
                val hash = sha3.digest(preRotated.publicKey)
                my.ssdid.wallet.domain.crypto.Multibase.encode(hash)
            } else null
        } else null

        DidDocument.build(did, identity.keyId, identity.algorithm, identity.publicKeyMultibase)
            .copy(nextKeyHash = nextKeyHash)
    }

    override suspend fun createProof(keyId: String, document: JsonObject, proofPurpose: String, challenge: String?, domain: String?): Result<Proof> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        // Build proof options (without proofValue — that's what we're computing)
        val proofOptions = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("type", JsonPrimitive(identity.algorithm.proofType))
            put("created", JsonPrimitive(now))
            put("verificationMethod", JsonPrimitive(identity.keyId))
            put("proofPurpose", JsonPrimitive(proofPurpose))
            if (challenge != null) put("challenge", JsonPrimitive(challenge))
            if (domain != null) put("domain", JsonPrimitive(domain))
        }

        // W3C Data Integrity signing payload:
        // SHA3-256(canonical_json(proof_options)) || SHA3-256(canonical_json(document))
        val sha3 = MessageDigest.getInstance("SHA3-256")
        val optionsHash = sha3.digest(canonicalJson(JsonObject(proofOptions)).toByteArray(Charsets.UTF_8))
        sha3.reset()
        val docHash = sha3.digest(canonicalJson(document).toByteArray(Charsets.UTF_8))
        val payload = optionsHash + docHash

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "vault"; message = "Signing proof payload"; level = SentryLevel.INFO
            data["proofPurpose"] = proofPurpose; data["algorithm"] = identity.algorithm.name
            data["payloadSize"] = payload.size.toString()
        })
        val signature = sign(keyId, payload).getOrThrow()
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "vault"; message = "Proof signed"; level = SentryLevel.INFO
            data["signatureSize"] = signature.size.toString()
        })
        Proof(
            type = identity.algorithm.proofType,
            created = now,
            verificationMethod = identity.keyId,
            proofPurpose = proofPurpose,
            proofValue = Multibase.encode(signature),
            domain = domain,
            challenge = challenge
        )
    }

    companion object {
        /**
         * Produces deterministic JSON by recursively sorting map keys.
         * Matches the registry's canonical_json implementation.
         */
        fun canonicalJson(element: kotlinx.serialization.json.JsonElement): String {
            return when (element) {
                is JsonObject -> {
                    val members = element.entries
                        .sortedBy { it.key }
                        .joinToString(",") { (k, v) ->
                            "\"${escapeJsonString(k)}\":${canonicalJson(v)}"
                        }
                    "{$members}"
                }
                is kotlinx.serialization.json.JsonArray -> {
                    val members = element.joinToString(",") { canonicalJson(it) }
                    "[$members]"
                }
                else -> element.toString() // JsonPrimitive (string, number, bool, null)
            }
        }

        private fun escapeJsonString(s: String): String {
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        }
    }

    override suspend fun storeCredential(credential: VerifiableCredential): Result<Unit> = runCatching {
        storage.saveCredential(credential)
    }

    override suspend fun listCredentials(): List<VerifiableCredential> = storage.listCredentials()

    override suspend fun getCredentialForDid(did: String): VerifiableCredential? {
        return storage.listCredentials().firstOrNull { it.credentialSubject.id == did }
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> = runCatching {
        storage.deleteCredential(credentialId)
    }
}
