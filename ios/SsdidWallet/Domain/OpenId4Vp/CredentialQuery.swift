import Foundation

struct CredentialQuery {
    let descriptors: [CredentialQueryDescriptor]
}

struct CredentialQueryDescriptor {
    let id: String
    let format: String
    let vctFilter: String?
    let requiredClaims: [String]
    let optionalClaims: [String]
}

struct MatchResult {
    let descriptorId: String
    let credentialId: String
    let credentialType: String
    let availableClaims: [String: ClaimInfo]
    let source: CredentialSource
}

struct ClaimInfo {
    let name: String
    let required: Bool
    let available: Bool
}

enum CredentialSource {
    case sdJwtVc
    case identity
}
