import Foundation

/// One-time migration: copies global profile VC data to the first identity.
enum ProfileMigration {

    private static let legacyProfileId = "urn:ssdid:profile"

    static func migrateIfNeeded(vault: Vault) async {
        let credentials = await vault.listCredentials()
        guard let profileVc = credentials.first(where: { $0.id == legacyProfileId }) else {
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
                try? await vault.updateIdentityProfile(
                    keyId: target.keyId,
                    profileName: profileName,
                    email: email,
                    emailVerified: nil
                )
            }
        }

        // Delete the legacy profile VC
        try? await vault.deleteCredential(credentialId: legacyProfileId)
    }
}
