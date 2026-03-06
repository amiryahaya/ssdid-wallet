package my.ssdid.mobile.domain.vault

import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.model.VerifiableCredential

interface VaultStorage {
    suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray)
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String)
    suspend fun getEncryptedPrivateKey(keyId: String): ByteArray?
    suspend fun saveCredential(credential: VerifiableCredential)
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun deleteCredential(credentialId: String)
}
