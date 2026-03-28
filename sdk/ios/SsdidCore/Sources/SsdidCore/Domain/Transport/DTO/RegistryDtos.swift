import Foundation

/// Request/response DTOs for Registry API interactions.

public struct RegisterDidRequest: Codable, Sendable {
    public let didDocument: DidDocument
    public let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

public struct RegisterDidResponse: Codable, Sendable {
    public let did: String
    public let status: String
}

public struct UpdateDidRequest: Codable, Sendable {
    public let didDocument: DidDocument
    public let proof: Proof

    enum CodingKeys: String, CodingKey {
        case didDocument = "did_document"
        case proof
    }
}

public struct DeactivateDidRequest: Codable, Sendable {
    public let proof: Proof
}

public struct ChallengeResponse: Codable, Sendable {
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

public struct RegistryInfoResponse: Codable, Sendable {
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

public struct RegistryPolicies: Codable, Sendable {
    public var proofMaxAgeSeconds: Int? = nil

    enum CodingKeys: String, CodingKey {
        case proofMaxAgeSeconds = "proof_max_age_seconds"
    }
}
