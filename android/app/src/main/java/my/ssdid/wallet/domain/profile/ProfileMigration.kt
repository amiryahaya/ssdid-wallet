package my.ssdid.wallet.domain.profile

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.ssdid.wallet.domain.vault.Vault
import javax.inject.Inject

class ProfileMigration @Inject constructor(private val vault: Vault) {

    private val migrationMutex = Mutex()

    companion object {
        private const val LEGACY_PROFILE_ID = "urn:ssdid:profile"
    }

    suspend fun migrateIfNeeded() = migrationMutex.withLock {
        val profileVc = vault.listCredentials().find { it.id == LEGACY_PROFILE_ID }
            ?: return@withLock

        val profileName = profileVc.credentialSubject.claims["name"]
        val email = profileVc.credentialSubject.claims["email"]
        val identities = vault.listIdentities()

        // If no identities, preserve the profile VC so data isn't lost
        if (identities.isEmpty()) return@withLock

        // Copy profile to the first identity that doesn't already have email
        val target = identities.firstOrNull { it.email.isNullOrBlank() }
        if (target != null && (!email.isNullOrBlank() || !profileName.isNullOrBlank())) {
            vault.updateIdentityProfile(
                target.keyId,
                profileName = profileName,
                email = email
            )
        }

        // Delete the legacy profile VC
        vault.deleteCredential(LEGACY_PROFILE_ID)
    }
}
