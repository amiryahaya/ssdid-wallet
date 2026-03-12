import Foundation

/// Request/response DTOs for Registry API interactions.

struct RegisterDidRequest: Codable {
    let didDocument: DidDocument
    let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

struct RegisterDidResponse: Codable {
    let did: String
    let status: String
}

struct UpdateDidRequest: Codable {
    let didDocument: DidDocument
    let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

struct DeactivateDidRequest: Codable {
    let proof: Proof
}

struct ChallengeResponse: Codable {
    let challenge: String
    var expiresAt: String? = nil
    var domain: String? = nil

    enum CodingKeys: String, CodingKey {
        case challenge
        case expiresAt = "expires_at"
        case domain
    }
}

struct RegistryInfoResponse: Codable {
    let name: String
    let version: String
    let didMethod: String
    var supportedAlgorithms: [String] = []
    var supportedProofTypes: [String] = []
    var policies: RegistryPolicies? = nil

    enum CodingKeys: String, CodingKey {
        case name, version
        case didMethod = "did_method"
        case supportedAlgorithms = "supported_algorithms"
        case supportedProofTypes = "supported_proof_types"
        case policies
    }
}

struct RegistryPolicies: Codable {
    var proofMaxAgeSeconds: Int? = nil

    enum CodingKeys: String, CodingKey {
        case proofMaxAgeSeconds = "proof_max_age_seconds"
    }
}
