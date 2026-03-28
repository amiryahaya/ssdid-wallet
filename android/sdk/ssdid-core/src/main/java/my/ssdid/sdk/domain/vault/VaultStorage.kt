package my.ssdid.sdk.domain.vault

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.rotation.RotationEntry
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc

data class PreRotatedKeyData(
    val encryptedPrivateKey: ByteArray,
    val publicKey: ByteArray
)

interface VaultStorage {
    suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray)
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String)
    suspend fun getEncryptedPrivateKey(keyId: String): ByteArray?
    suspend fun saveCredential(credential: VerifiableCredential)
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun deleteCredential(credentialId: String)

    // SD-JWT VC storage
    suspend fun saveSdJwtVc(sdJwtVc: StoredSdJwtVc)
    suspend fun listSdJwtVcs(): List<StoredSdJwtVc>
    suspend fun deleteSdJwtVc(id: String)

    // Recovery key storage
    suspend fun saveRecoveryPublicKey(keyId: String, encryptedPublicKey: ByteArray)
    suspend fun getRecoveryPublicKey(keyId: String): ByteArray?

    // Pre-rotated key storage (KERI)
    suspend fun savePreRotatedKey(keyId: String, encryptedPrivateKey: ByteArray, publicKey: ByteArray)
    suspend fun getPreRotatedKey(keyId: String): PreRotatedKeyData?
    suspend fun deletePreRotatedKey(keyId: String)

    // Rotation history
    suspend fun addRotationEntry(did: String, entry: RotationEntry)
    suspend fun getRotationHistory(did: String): List<RotationEntry>
}
