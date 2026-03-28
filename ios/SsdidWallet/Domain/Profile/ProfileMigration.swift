import Foundation
import SsdidCore

/// One-time migration: copies global profile VC data to the first identity.
enum ProfileMigration {

    private static let legacyProfileId = "urn:ssdid:profile"
    private static let migrationKey = "ssdid_profile_migration_v1"

    static func migrateIfNeeded(vault: Vault) async {
        // Idempotency guard
        guard !UserDefaults.standard.bool(forKey: migrationKey) else { return }

        let credentials = await vault.listCredentials()
        guard let profileVc = credentials.first(where: { $0.id == legacyProfileId }) else {
            // No legacy profile — mark migration complete
            UserDefaults.standard.set(true, forKey: migrationKey)
            return
        }

        let profileName = profileVc.credentialSubject.claims["name"]
        let email = profileVc.credentialSubject.claims["email"]
        let identities = await vault.listIdentities()

        // If no identities, preserve the profile VC so data isn't lost
        guard !identities.isEmpty else { return }

        // Copy profile to the first identity that doesn't already have email
        if let target = identities.first(where: { ($0.email ?? "").isEmpty }) {
            let hasData = !(email ?? "").isEmpty || !(profileName ?? "").isEmpty
            if hasData {
                do {
                    try await vault.updateIdentityProfile(
                        keyId: target.keyId,
                        profileName: profileName,
                        email: email,
                        emailVerified: nil
                    )
                } catch {
                    // Do NOT delete the credential if migration failed — preserve data
                    return
                }
            }
        }

        // Delete the legacy profile VC only after successful migration
        try? await vault.deleteCredential(credentialId: legacyProfileId)
        UserDefaults.standard.set(true, forKey: migrationKey)
    }
}
