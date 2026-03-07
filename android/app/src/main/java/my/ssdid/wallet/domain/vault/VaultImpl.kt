package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.platform.keystore.KeystoreManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        DidDocument.build(
            did = Did(identity.did),
            keyId = identity.keyId,
            algorithm = identity.algorithm,
            publicKeyMultibase = identity.publicKeyMultibase
        )
    }

    override suspend fun createProof(keyId: String, document: Map<String, String>, proofPurpose: String, challenge: String?): Result<Proof> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val canonicalJson = Json.encodeToString(document)
        val dataToSign = if (challenge != null) {
            challenge.toByteArray() + canonicalJson.toByteArray()
        } else {
            canonicalJson.toByteArray()
        }
        val signature = sign(keyId, dataToSign).getOrThrow()
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        Proof(
            type = identity.algorithm.proofType,
            created = now,
            verificationMethod = identity.keyId,
            proofPurpose = proofPurpose,
            proofValue = Multibase.encode(signature),
            challenge = challenge
        )
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
