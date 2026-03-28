package my.ssdid.sdk.api

import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.vault.Vault

class IdentityApi internal constructor(
    private val vault: Vault,
    private val client: SsdidClient
) {
    suspend fun create(name: String, algorithm: Algorithm): Result<Identity> =
        client.initIdentity(name, algorithm)
    suspend fun list(): List<Identity> = vault.listIdentities()
    suspend fun get(keyId: String): Identity? = vault.getIdentity(keyId)
    suspend fun delete(keyId: String): Result<Unit> = vault.deleteIdentity(keyId)
    suspend fun buildDidDocument(keyId: String): Result<DidDocument> = vault.buildDidDocument(keyId)
    suspend fun updateDidDocument(keyId: String): Result<Unit> = client.updateDidDocument(keyId)
    suspend fun deactivate(keyId: String): Result<Unit> = client.deactivateDid(keyId)
}
