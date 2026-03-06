package my.ssdid.mobile.domain.recovery

import my.ssdid.mobile.domain.crypto.CryptoProvider
import my.ssdid.mobile.domain.model.Algorithm
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.domain.vault.VaultStorage
import my.ssdid.mobile.platform.keystore.KeystoreManager
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RecoveryManager @Inject constructor(
    private val vault: Vault,
    private val storage: VaultStorage,
    @Named("classical") private val classicalProvider: CryptoProvider,
    @Named("pqc") private val pqcProvider: CryptoProvider,
    private val keystoreManager: KeystoreManager
) {
    private fun providerFor(algorithm: Algorithm): CryptoProvider =
        if (algorithm.isPostQuantum) pqcProvider else classicalProvider

    /**
     * Generate a recovery keypair for the given identity.
     * Returns the recovery private key bytes — caller must export/store offline.
     * The recovery public key is stored in the vault for future DID Document updates.
     */
    suspend fun generateRecoveryKey(identity: Identity): Result<ByteArray> = runCatching {
        val provider = providerFor(identity.algorithm)
        val recoveryKeyPair = provider.generateKeyPair(identity.algorithm)
        val recoveryKeyId = "${identity.keyId}-recovery"

        // Store the recovery public key encrypted (for DID Document building)
        val wrappingAlias = "ssdid_recovery_${identity.keyId.hashCode().toUInt()}"
        keystoreManager.generateWrappingKey(wrappingAlias)
        val encryptedRecoveryPubKey = keystoreManager.encrypt(wrappingAlias, recoveryKeyPair.publicKey)

        // Save recovery key reference in vault storage
        storage.saveRecoveryPublicKey(recoveryKeyId, encryptedRecoveryPubKey)

        // Update identity to mark as having recovery key
        val updatedIdentity = identity.copy(
            recoveryKeyId = recoveryKeyId,
            hasRecoveryKey = true
        )
        // Re-save identity with updated metadata (get encrypted private key first)
        val encryptedPrivateKey = storage.getEncryptedPrivateKey(identity.keyId)
            ?: throw IllegalStateException("Private key not found for: ${identity.keyId}")
        storage.saveIdentity(updatedIdentity, encryptedPrivateKey)

        // Return the recovery private key for the user to store offline
        val recoveryPrivateKey = recoveryKeyPair.privateKey.copyOf()
        recoveryKeyPair.privateKey.fill(0)
        recoveryPrivateKey
    }

    /**
     * Check if an identity has a recovery key configured.
     */
    suspend fun hasRecoveryKey(keyId: String): Boolean {
        val identity = storage.getIdentity(keyId)
        return identity?.hasRecoveryKey == true
    }
}
