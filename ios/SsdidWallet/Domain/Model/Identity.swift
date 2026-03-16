import Foundation

struct Identity: Codable, Identifiable {
    let name: String
    let did: String
    let keyId: String
    let algorithm: Algorithm
    let publicKeyMultibase: String
    let createdAt: String
    var isActive: Bool = true
    var recoveryKeyId: String? = nil
    var hasRecoveryKey: Bool = false
    var preRotatedKeyId: String? = nil
    var profileName: String? = nil
    var email: String? = nil
    var emailVerified: Bool = false

    var id: String { keyId }

    /// Returns a claims map suitable for sharing with services.
    func claimsMap() -> [String: String] {
        var claims: [String: String] = [:]
        if let profileName = profileName { claims["name"] = profileName }
        if let email = email { claims["email"] = email }
        return claims
    }
}
