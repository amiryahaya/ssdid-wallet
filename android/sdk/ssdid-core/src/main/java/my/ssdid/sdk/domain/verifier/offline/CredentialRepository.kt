package my.ssdid.sdk.domain.verifier.offline

import my.ssdid.sdk.domain.model.VerifiableCredential

interface CredentialRepository {
    suspend fun saveCredential(credential: VerifiableCredential)
    suspend fun getHeldCredentials(): List<VerifiableCredential>
    suspend fun getUniqueIssuerDids(): List<String>
    suspend fun deleteCredential(credentialId: String)
}
