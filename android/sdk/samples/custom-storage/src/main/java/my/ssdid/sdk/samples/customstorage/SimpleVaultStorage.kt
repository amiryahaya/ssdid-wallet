package my.ssdid.sdk.samples.customstorage

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.rotation.RotationEntry
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import my.ssdid.sdk.domain.vault.PreRotatedKeyData
import my.ssdid.sdk.domain.vault.VaultStorage

/**
 * In-memory implementation of [VaultStorage] for demonstration purposes.
 *
 * This shows how to provide a custom storage backend to the SDK.
 * In production you might back this with Room, SQLCipher, or an encrypted file store.
 */
class SimpleVaultStorage : VaultStorage {

    private val identities = mutableMapOf<String, Identity>()
    private val privateKeys = mutableMapOf<String, ByteArray>()
    private val credentials = mutableMapOf<String, VerifiableCredential>()
    private val sdJwtVcs = mutableMapOf<String, StoredSdJwtVc>()
    private val recoveryKeys = mutableMapOf<String, ByteArray>()
    private val preRotatedKeys = mutableMapOf<String, PreRotatedKeyData>()
    private val rotationHistory = mutableMapOf<String, MutableList<RotationEntry>>()

    override suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray) {
        identities[identity.keyId] = identity
        privateKeys[identity.keyId] = encryptedPrivateKey
    }

    override suspend fun getIdentity(keyId: String): Identity? = identities[keyId]

    override suspend fun listIdentities(): List<Identity> = identities.values.toList()

    override suspend fun deleteIdentity(keyId: String) {
        identities.remove(keyId)
        privateKeys.remove(keyId)
    }

    override suspend fun getEncryptedPrivateKey(keyId: String): ByteArray? = privateKeys[keyId]

    override suspend fun saveCredential(credential: VerifiableCredential) {
        credentials[credential.id] = credential
    }

    override suspend fun listCredentials(): List<VerifiableCredential> = credentials.values.toList()

    override suspend fun deleteCredential(credentialId: String) {
        credentials.remove(credentialId)
    }

    override suspend fun saveSdJwtVc(sdJwtVc: StoredSdJwtVc) {
        sdJwtVcs[sdJwtVc.id] = sdJwtVc
    }

    override suspend fun listSdJwtVcs(): List<StoredSdJwtVc> = sdJwtVcs.values.toList()

    override suspend fun deleteSdJwtVc(id: String) {
        sdJwtVcs.remove(id)
    }

    override suspend fun saveRecoveryPublicKey(keyId: String, encryptedPublicKey: ByteArray) {
        recoveryKeys[keyId] = encryptedPublicKey
    }

    override suspend fun getRecoveryPublicKey(keyId: String): ByteArray? = recoveryKeys[keyId]

    override suspend fun savePreRotatedKey(
        keyId: String,
        encryptedPrivateKey: ByteArray,
        publicKey: ByteArray
    ) {
        preRotatedKeys[keyId] = PreRotatedKeyData(encryptedPrivateKey, publicKey)
    }

    override suspend fun getPreRotatedKey(keyId: String): PreRotatedKeyData? = preRotatedKeys[keyId]

    override suspend fun deletePreRotatedKey(keyId: String) {
        preRotatedKeys.remove(keyId)
    }

    override suspend fun addRotationEntry(did: String, entry: RotationEntry) {
        rotationHistory.getOrPut(did) { mutableListOf() }.add(entry)
    }

    override suspend fun getRotationHistory(did: String): List<RotationEntry> =
        rotationHistory[did] ?: emptyList()

    /** Returns the number of identities currently held in memory. */
    fun identityCount(): Int = identities.size
}
