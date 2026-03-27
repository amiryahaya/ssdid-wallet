package my.ssdid.wallet.domain.recovery.institutional

import kotlinx.serialization.Serializable
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import java.time.Instant
import java.util.Base64

/**
 * Manages institutional (organization-assisted) recovery.
 * An organization enrolls as a recovery custodian by providing their DID.
 * During recovery, the organization signs a recovery authorization that
 * proves the user's identity claim, allowing key restoration.
 */
class InstitutionalRecoveryManager(
    private val recoveryManager: RecoveryManager,
    private val storage: InstitutionalRecoveryStorage
) {
    /**
     * Enroll an organization as a recovery custodian.
     * The organization's DID is stored alongside a copy of the recovery key
     * (encrypted to the organization's public key in a real deployment).
     *
     * @param identity The identity to protect
     * @param orgDid The organization's DID
     * @param orgName Display name for the organization
     * @param encryptedRecoveryKey Recovery key encrypted to the org's public key
     */
    suspend fun enrollOrganization(
        identity: Identity,
        orgDid: String,
        orgName: String,
        encryptedRecoveryKey: ByteArray
    ): Result<OrgRecoveryConfig> = runCatching {
        require(orgDid.startsWith("did:")) { "Invalid organization DID: $orgDid" }

        // Ensure identity has a recovery key
        val hasKey = recoveryManager.hasRecoveryKey(identity.keyId)
        require(hasKey) { "Identity must have a recovery key before enrolling an organization" }

        val config = OrgRecoveryConfig(
            userDid = identity.did,
            orgDid = orgDid,
            orgName = orgName,
            encryptedRecoveryKey = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedRecoveryKey),
            enrolledAt = Instant.now().toString()
        )
        storage.saveOrgRecoveryConfig(config)
        config
    }

    /**
     * Recover using organization-provided recovery key.
     * The organization decrypts and provides the recovery key after
     * verifying the user's identity through their own KYC process.
     *
     * @param did The DID to recover
     * @param recoveryKeyBase64 The recovery private key (provided by org after verification)
     * @param name Name for the restored identity
     * @param algorithm Algorithm for new key generation
     */
    suspend fun recoverWithOrgAssistance(
        did: String,
        recoveryKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ): Result<Identity> = runCatching {
        val config = storage.getOrgRecoveryConfig(did)
            ?: throw IllegalStateException("No institutional recovery configured for $did")

        // Delegate to standard recovery manager
        recoveryManager.restoreWithRecoveryKey(did, recoveryKeyBase64, name, algorithm).getOrThrow()
    }

    /**
     * Check if institutional recovery is configured for a DID.
     */
    suspend fun hasOrgRecovery(did: String): Boolean {
        return storage.getOrgRecoveryConfig(did) != null
    }

    /**
     * Get organization recovery configuration.
     */
    suspend fun getConfig(did: String): OrgRecoveryConfig? {
        return storage.getOrgRecoveryConfig(did)
    }
}

@Serializable
data class OrgRecoveryConfig(
    val userDid: String,
    val orgDid: String,
    val orgName: String,
    val encryptedRecoveryKey: String,
    val enrolledAt: String
)

interface InstitutionalRecoveryStorage {
    suspend fun saveOrgRecoveryConfig(config: OrgRecoveryConfig)
    suspend fun getOrgRecoveryConfig(userDid: String): OrgRecoveryConfig?
    suspend fun deleteOrgRecoveryConfig(userDid: String)
}
