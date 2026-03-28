import Foundation

/// Request/response DTOs for Registry API interactions.

public struct RegisterDidRequest: Codable {
    public let didDocument: DidDocument
    public let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

public struct RegisterDidResponse: Codable {
    public let did: String
    public let status: String
}

public struct UpdateDidRequest: Codable {
    public let didDocument: DidDocument
    public let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

public struct DeactivateDidRequest: Codable {
    public let proof: Proof
}

public struct ChallengeResponse: Codable {
    public let challenge: String
    public var expiresAt: String? = nil
    public var domain: String? = nil
    public var protocolVersion: String? = nil

    enum CodingKeys: String, CodingKey {
        case challenge
        case expiresAt = "expires_at"
        case domain
        case protocolVersion = "protocol_version"
    }
}

public struct RegistryInfoResponse: Codable {
    public let name: String
    public let version: String
    public let didMethod: String
    public var supportedAlgorithms: [String] = []
    public var supportedProofTypes: [String] = []
    public var policies: RegistryPolicies? = nil

    enum CodingKeys: String, CodingKey {
        case name, version
        case didMethod = "did_method"
        case supportedAlgorithms = "supported_algorithms"
        case supportedProofTypes = "supported_proof_types"
        case policies
    }
}

public struct RegistryPolicies: Codable {
    public var proofMaxAgeSeconds: Int? = nil

    enum CodingKeys: String, CodingKey {
        case proofMaxAgeSeconds = "proof_max_age_seconds"
    }
}
