package my.ssdid.wallet.domain.recovery.social

import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages social recovery using Shamir's Secret Sharing.
 * Splits the recovery private key into shares distributed to trusted guardians.
 * Any K-of-N guardians can reconstruct the recovery key for restoration.
 */
class SocialRecoveryManager(
    private val recoveryManager: RecoveryManager,
    private val storage: SocialRecoveryStorage
) {
    /**
     * Set up social recovery for an identity.
     * Generates a recovery key, splits it into shares, and returns
     * the shares for distribution to guardians.
     *
     * @param identity The identity to protect
     * @param guardianNames Names and DIDs of guardians [(name, did)]
     * @param threshold Minimum shares needed to recover (K)
     * @return Map of guardian ID to their Base64-encoded share
     */
    suspend fun setupSocialRecovery(
        identity: Identity,
        guardianNames: List<Pair<String, String>>,
        threshold: Int
    ): Result<Map<String, String>> = runCatching {
        val totalShares = guardianNames.size
        require(threshold >= 2) { "Threshold must be at least 2" }
        require(totalShares >= threshold) { "Need at least $threshold guardians, got $totalShares" }
        require(totalShares <= 255) { "Maximum 255 guardians" }

        // Generate recovery key (this stores the public key in vault)
        val recoveryPrivateKey = recoveryManager.generateRecoveryKey(identity).getOrThrow()

        // Split recovery key into shares
        val shares = ShamirSecretSharing.split(recoveryPrivateKey, threshold, totalShares)
        recoveryPrivateKey.fill(0) // clear from memory

        val now = Instant.now().toString()
        val guardians = guardianNames.mapIndexed { idx, (name, did) ->
            Guardian(
                id = UUID.randomUUID().toString(),
                name = name,
                did = did,
                shareIndex = shares[idx].index,
                enrolledAt = now
            )
        }

        // Store social recovery configuration
        val config = SocialRecoveryConfig(
            did = identity.did,
            threshold = threshold,
            totalShares = totalShares,
            guardians = guardians,
            createdAt = now
        )
        storage.saveSocialRecoveryConfig(config)

        // Return guardian ID -> Base64-encoded share
        guardians.zip(shares).associate { (guardian, share) ->
            guardian.id to Base64.getUrlEncoder().withoutPadding().encodeToString(share.data)
        }
    }

    /**
     * Recover identity using shares collected from guardians.
     *
     * @param did The DID to recover
     * @param collectedShares Map of share index to Base64-encoded share data
     * @param name Name for the restored identity
     * @param algorithm Algorithm to use for new key generation
     */
    suspend fun recoverWithShares(
        did: String,
        collectedShares: Map<Int, String>,
        name: String,
        algorithm: Algorithm
    ): Result<Identity> = runCatching {
        val config = storage.getSocialRecoveryConfig(did)
            ?: throw IllegalStateException("No social recovery configured for $did")

        require(collectedShares.size >= config.threshold) {
            "Need at least ${config.threshold} shares, got ${collectedShares.size}"
        }

        // Reconstruct shares
        val shares = collectedShares.map { (index, data) ->
            ShamirSecretSharing.Share(index, Base64.getUrlDecoder().decode(data))
        }

        // Reconstruct recovery private key
        val recoveryPrivateKey = ShamirSecretSharing.combine(shares)
        val recoveryKeyBase64 = Base64.getEncoder().encodeToString(recoveryPrivateKey)
        recoveryPrivateKey.fill(0)

        // Delegate to existing recovery manager
        recoveryManager.restoreWithRecoveryKey(did, recoveryKeyBase64, name, algorithm).getOrThrow()
    }

    /**
     * Get social recovery configuration for a DID.
     */
    suspend fun getConfig(did: String): SocialRecoveryConfig? {
        return storage.getSocialRecoveryConfig(did)
    }

    /**
     * Check if social recovery is configured for a DID.
     */
    suspend fun hasSocialRecovery(did: String): Boolean {
        return storage.getSocialRecoveryConfig(did) != null
    }
}

/**
 * Storage interface for social recovery configuration.
 */
interface SocialRecoveryStorage {
    suspend fun saveSocialRecoveryConfig(config: SocialRecoveryConfig)
    suspend fun getSocialRecoveryConfig(did: String): SocialRecoveryConfig?
    suspend fun deleteSocialRecoveryConfig(did: String)
}
