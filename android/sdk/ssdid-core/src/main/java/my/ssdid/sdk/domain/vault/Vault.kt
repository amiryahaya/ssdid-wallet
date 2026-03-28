package my.ssdid.sdk.domain.vault

import kotlinx.serialization.json.JsonObject
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc

interface Vault {
    suspend fun createIdentity(name: String, algorithm: Algorithm): Result<Identity>
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String): Result<Unit>
    /**
     * Updates profile fields on an existing identity.
     * Pass null to leave a field unchanged (not to clear it).
     * Pass empty string ("") to clear a string field.
     */
    suspend fun updateIdentityProfile(
        keyId: String,
        profileName: String? = null,
        email: String? = null,
        emailVerified: Boolean? = null
    ): Result<Unit>
    suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray>
    suspend fun buildDidDocument(keyId: String): Result<DidDocument>
    suspend fun createProof(keyId: String, document: JsonObject, proofPurpose: String, challenge: String? = null, domain: String? = null): Result<Proof>
    suspend fun storeCredential(credential: VerifiableCredential): Result<Unit>
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun getCredentialForDid(did: String): VerifiableCredential?
    suspend fun getCredentialsForDid(did: String): List<VerifiableCredential>
    suspend fun deleteCredential(credentialId: String): Result<Unit>
    suspend fun getEncryptedPrivateKey(keyId: String): ByteArray?
    suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray)
    suspend fun listStoredSdJwtVcs(): List<StoredSdJwtVc>
    suspend fun storeStoredSdJwtVc(sdJwtVc: StoredSdJwtVc): Result<Unit>
}
