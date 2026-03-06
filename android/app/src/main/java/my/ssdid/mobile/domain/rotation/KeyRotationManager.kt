package my.ssdid.mobile.domain.rotation

import kotlinx.serialization.Serializable
import my.ssdid.mobile.domain.crypto.CryptoProvider
import my.ssdid.mobile.domain.crypto.Multibase
import my.ssdid.mobile.domain.model.Algorithm
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.vault.VaultStorage
import my.ssdid.mobile.platform.keystore.KeystoreManager
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class RotationStatus(
    val hasPreCommitment: Boolean,
    val nextKeyHash: String?,
    val lastRotatedAt: String?,
    val rotationHistory: List<RotationEntry>
)

@Serializable
data class RotationEntry(
    val timestamp: String,
    val oldKeyIdFragment: String,
    val newKeyIdFragment: String
)

@Singleton
class KeyRotationManager @Inject constructor(
    private val storage: VaultStorage,
    @Named("classical") private val classicalProvider: CryptoProvider,
    @Named("pqc") private val pqcProvider: CryptoProvider,
    private val keystoreManager: KeystoreManager
) {
    private fun providerFor(algorithm: Algorithm): CryptoProvider =
        if (algorithm.isPostQuantum) pqcProvider else classicalProvider

    /**
     * Prepare rotation: generate next keypair, compute SHA3-256 hash of public key,
     * store pre-rotated key encrypted. Returns the hash to publish in DID Document.
     */
    suspend fun prepareRotation(identity: Identity): Result<String> = runCatching {
        val provider = providerFor(identity.algorithm)
        val nextKeyPair = provider.generateKeyPair(identity.algorithm)

        // Compute SHA3-256 hash of next public key
        val sha3 = MessageDigest.getInstance("SHA3-256")
        val hashBytes = sha3.digest(nextKeyPair.publicKey)
        val nextKeyHash = "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)

        // Encrypt and store pre-rotated key material
        val preRotatedKeyId = "${identity.keyId}-prerotated"
        val wrappingAlias = "ssdid_prerot_${identity.keyId.hashCode().toUInt()}"
        keystoreManager.generateWrappingKey(wrappingAlias)

        val encryptedPrivateKey = keystoreManager.encrypt(wrappingAlias, nextKeyPair.privateKey)
        nextKeyPair.privateKey.fill(0)

        // Store pre-rotated key data
        storage.savePreRotatedKey(preRotatedKeyId, encryptedPrivateKey, nextKeyPair.publicKey)

        // Update identity with pre-rotation reference
        val updatedIdentity = identity.copy(preRotatedKeyId = preRotatedKeyId)
        val existingEncKey = storage.getEncryptedPrivateKey(identity.keyId)
            ?: throw IllegalStateException("Private key not found for: ${identity.keyId}")
        storage.saveIdentity(updatedIdentity, existingEncKey)

        nextKeyHash
    }

    /**
     * Execute rotation: promote pre-committed key to active, generate new pre-commitment.
     * Returns the updated identity with the new key.
     */
    suspend fun executeRotation(identity: Identity): Result<Identity> = runCatching {
        val preRotatedKeyId = identity.preRotatedKeyId
            ?: throw IllegalStateException("No pre-committed key — call prepareRotation first")

        // Retrieve pre-rotated key material
        val preRotatedData = storage.getPreRotatedKey(preRotatedKeyId)
            ?: throw IllegalStateException("Pre-rotated key data not found: $preRotatedKeyId")

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        // Create new identity entry with the pre-rotated key as active
        val newKeyId = identity.did + "#key-${System.currentTimeMillis()}"
        val publicKeyMultibase = Multibase.encode(preRotatedData.publicKey)

        val newIdentity = identity.copy(
            keyId = newKeyId,
            publicKeyMultibase = publicKeyMultibase,
            preRotatedKeyId = null
        )

        // Store the new identity with the pre-rotated encrypted private key
        storage.saveIdentity(newIdentity, preRotatedData.encryptedPrivateKey)

        // Record rotation in history
        storage.addRotationEntry(
            identity.did,
            RotationEntry(
                timestamp = now,
                oldKeyIdFragment = identity.keyId.substringAfterLast("#"),
                newKeyIdFragment = newKeyId.substringAfterLast("#")
            )
        )

        // Clean up old pre-rotated key
        storage.deletePreRotatedKey(preRotatedKeyId)

        newIdentity
    }

    /**
     * Get pre-rotation status for an identity.
     */
    suspend fun getRotationStatus(identity: Identity): RotationStatus {
        val history = storage.getRotationHistory(identity.did)
        return RotationStatus(
            hasPreCommitment = identity.preRotatedKeyId != null,
            nextKeyHash = if (identity.preRotatedKeyId != null) {
                val data = storage.getPreRotatedKey(identity.preRotatedKeyId!!)
                data?.let {
                    val sha3 = MessageDigest.getInstance("SHA3-256")
                    val hashBytes = sha3.digest(it.publicKey)
                    "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
                }
            } else null,
            lastRotatedAt = history.firstOrNull()?.timestamp,
            rotationHistory = history
        )
    }
}
