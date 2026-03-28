package my.ssdid.sdk.domain.vault

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.rotation.RotationEntry
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc

class FakeVaultStorage : VaultStorage {
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
    override suspend fun getIdentity(keyId: String) = identities[keyId]
    override suspend fun listIdentities() = identities.values.toList()
    override suspend fun deleteIdentity(keyId: String) { identities.remove(keyId); privateKeys.remove(keyId) }
    override suspend fun getEncryptedPrivateKey(keyId: String) = privateKeys[keyId]
    override suspend fun saveCredential(credential: VerifiableCredential) { credentials[credential.id] = credential }
    override suspend fun listCredentials() = credentials.values.toList()
    override suspend fun deleteCredential(credentialId: String) { credentials.remove(credentialId) }
    override suspend fun saveSdJwtVc(sdJwtVc: StoredSdJwtVc) { sdJwtVcs[sdJwtVc.id] = sdJwtVc }
    override suspend fun listSdJwtVcs() = sdJwtVcs.values.toList()
    override suspend fun deleteSdJwtVc(id: String) { sdJwtVcs.remove(id) }
    override suspend fun saveRecoveryPublicKey(keyId: String, encryptedPublicKey: ByteArray) { recoveryKeys[keyId] = encryptedPublicKey }
    override suspend fun getRecoveryPublicKey(keyId: String) = recoveryKeys[keyId]
    override suspend fun savePreRotatedKey(keyId: String, encryptedPrivateKey: ByteArray, publicKey: ByteArray) {
        preRotatedKeys[keyId] = PreRotatedKeyData(encryptedPrivateKey, publicKey)
    }
    override suspend fun getPreRotatedKey(keyId: String) = preRotatedKeys[keyId]
    override suspend fun deletePreRotatedKey(keyId: String) { preRotatedKeys.remove(keyId) }
    override suspend fun addRotationEntry(did: String, entry: RotationEntry) {
        rotationHistory.getOrPut(did) { mutableListOf() }.add(entry)
    }
    override suspend fun getRotationHistory(did: String) = rotationHistory[did] ?: emptyList()
}
