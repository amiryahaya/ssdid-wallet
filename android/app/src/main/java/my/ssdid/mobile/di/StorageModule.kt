package my.ssdid.mobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.model.VerifiableCredential
import my.ssdid.mobile.domain.vault.VaultStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideVaultStorage(): VaultStorage = InMemoryVaultStorage()
}

private class InMemoryVaultStorage : VaultStorage {
    private val identities = mutableMapOf<String, Identity>()
    private val privateKeys = mutableMapOf<String, ByteArray>()
    private val credentials = mutableMapOf<String, VerifiableCredential>()

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
}
