import Foundation

public struct Identity: Codable, Identifiable {
    public let name: String
    public let did: String
    public let keyId: String
    public let algorithm: Algorithm
    public let publicKeyMultibase: String
    public let createdAt: String
    public var isActive: Bool = true
    public var recoveryKeyId: String? = nil
    public var hasRecoveryKey: Bool = false
    public var preRotatedKeyId: String? = nil
    public var profileName: String? = nil
    public var email: String? = nil
    public var emailVerified: Bool = false

    public var id: String { keyId }

    public init(
        name: String,
        did: String,
        keyId: String,
        algorithm: Algorithm,
        publicKeyMultibase: String,
        createdAt: String,
        isActive: Bool = true,
        recoveryKeyId: String? = nil,
        hasRecoveryKey: Bool = false,
        preRotatedKeyId: String? = nil,
        profileName: String? = nil,
        email: String? = nil,
        emailVerified: Bool = false
    ) {
        self.name = name
        self.did = did
        self.keyId = keyId
        self.algorithm = algorithm
        self.publicKeyMultibase = publicKeyMultibase
        self.createdAt = createdAt
        self.isActive = isActive
        self.recoveryKeyId = recoveryKeyId
        self.hasRecoveryKey = hasRecoveryKey
        self.preRotatedKeyId = preRotatedKeyId
        self.profileName = profileName
        self.email = email
        self.emailVerified = emailVerified
    }

    /// Returns a claims map suitable for sharing with services.
    public func claimsMap() -> [String: String] {
        var claims: [String: String] = [:]
        if let profileName = profileName { claims["name"] = profileName }
        if let email = email { claims["email"] = email }
        return claims
    }
}
