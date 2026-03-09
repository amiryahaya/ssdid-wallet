package my.ssdid.wallet.domain.recovery

import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.keystore.KeystoreManager
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
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
        val wrappingAlias = "ssdid_recovery_${stableAlias(identity.keyId)}"
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

    /**
     * Restore identity using offline recovery private key.
     * Generates new primary keypair, stores locally.
     * Caller should subsequently publish updated DID Document via SsdidClient.
     */
    suspend fun restoreWithRecoveryKey(
        did: String,
        recoveryPrivateKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ): Result<Identity> = runCatching {
        val recoveryPrivateKey = Base64.getDecoder().decode(recoveryPrivateKeyBase64)

        // Generate new primary keypair
        val provider = providerFor(algorithm)
        val kp = provider.generateKeyPair(algorithm)

        // Create new identity
        val keyId = "$did#${UUID.randomUUID().toString().take(8)}"
        val publicKeyMultibase = Multibase.encode(kp.publicKey)
        val now = Instant.now().toString()
        val identity = Identity(
            name = name,
            did = did,
            keyId = keyId,
            algorithm = algorithm,
            publicKeyMultibase = publicKeyMultibase,
            createdAt = now
        )

        // Encrypt and store new private key
        val alias = stableAlias(keyId)
        keystoreManager.generateWrappingKey(alias)
        val encryptedKey = keystoreManager.encrypt(alias, kp.privateKey)
        kp.privateKey.fill(0)
        recoveryPrivateKey.fill(0)
        storage.saveIdentity(identity, encryptedKey)

        identity
    }

    private fun stableAlias(keyId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(keyId.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).take(16)
    }
}
