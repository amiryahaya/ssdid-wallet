import Foundation

public struct CredentialQuery {
    public let descriptors: [CredentialQueryDescriptor]
}

public struct CredentialQueryDescriptor {
    public let id: String
    public let format: String
    public let vctFilter: String?
    public let requiredClaims: [String]
    public let optionalClaims: [String]
}

public struct MatchResult {
    public let descriptorId: String
    public let credentialId: String
    public let credentialType: String
    public let availableClaims: [String: ClaimInfo]
    public let source: CredentialSource
}

public struct ClaimInfo {
    public let name: String
    public let required: Bool
    public let available: Bool
}

public enum CredentialSource {
    case sdJwtVc
    case identity
}
