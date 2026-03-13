import Foundation

/// Manages user profile data stored as a self-issued verifiable credential.
final class ProfileManager {

    static let profileId = "urn:ssdid:profile"
    static let selfIssuer = "did:ssdid:self"

    private let vault: Vault

    init(vault: Vault) {
        self.vault = vault
    }

    /// Saves or updates the user's profile (name, email) as a self-issued VC.
    func saveProfile(name: String, email: String) async throws {
        // Delete existing profile if present
        let existing = await vault.listCredentials()
        if existing.contains(where: { $0.id == Self.profileId }) {
            try await vault.deleteCredential(credentialId: Self.profileId)
        }

        let now = ISO8601DateFormatter().string(from: Date())
        let claims = ["name": name, "email": email]

        let credential = VerifiableCredential(
            id: Self.profileId,
            type: ["VerifiableCredential", "ProfileCredential"],
            issuer: Self.selfIssuer,
            issuanceDate: now,
            credentialSubject: CredentialSubject(id: Self.selfIssuer, claims: claims),
            proof: Proof(
                type: "SelfIssued2024",
                created: now,
                verificationMethod: Self.selfIssuer,
                proofPurpose: "selfAssertion",
                proofValue: ""
            )
        )

        try await vault.storeCredential(credential)
    }

    /// Retrieves the user's profile credential.
    func getProfile() async -> VerifiableCredential? {
        let credentials = await vault.listCredentials()
        return credentials.first { $0.id == Self.profileId }
    }

    /// Retrieves the user's profile claims (name, email, etc.).
    func getProfileClaims() async -> [String: String] {
        guard let profile = await getProfile() else { return [:] }
        return profile.credentialSubject.claims
    }
}
