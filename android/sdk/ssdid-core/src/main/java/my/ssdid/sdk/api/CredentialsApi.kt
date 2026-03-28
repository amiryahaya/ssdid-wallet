package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.vault.Vault

class CredentialsApi internal constructor(private val vault: Vault) {
    suspend fun store(credential: VerifiableCredential): Result<Unit> = vault.storeCredential(credential)
    suspend fun list(): List<VerifiableCredential> = vault.listCredentials()
    suspend fun getForDid(did: String): List<VerifiableCredential> = vault.getCredentialsForDid(did)
    suspend fun delete(credentialId: String): Result<Unit> = vault.deleteCredential(credentialId)
}
