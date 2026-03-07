package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.model.*

interface Vault {
    suspend fun createIdentity(name: String, algorithm: Algorithm): Result<Identity>
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String): Result<Unit>
    suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray>
    suspend fun buildDidDocument(keyId: String): Result<DidDocument>
    suspend fun createProof(keyId: String, document: Map<String, String>, proofPurpose: String, challenge: String? = null): Result<Proof>
    suspend fun storeCredential(credential: VerifiableCredential): Result<Unit>
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun getCredentialForDid(did: String): VerifiableCredential?
    suspend fun deleteCredential(credentialId: String): Result<Unit>
}
