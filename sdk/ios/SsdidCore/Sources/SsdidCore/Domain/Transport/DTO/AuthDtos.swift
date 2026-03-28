import Foundation

/// Request/response DTOs for authentication flows.

public struct ClaimRequest: Codable {
    public let key: String
    public var required: Bool = false
}

public struct AuthChallengeResponse: Codable {
    public let challenge: String
    public let serverName: String
    public let serverDid: String
    public let serverKeyId: String

    enum CodingKeys: String, CodingKey {
        case challenge
        case serverName = "server_name"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
    }
}

public struct AuthVerifyRequest: Codable {
    public let did: String
    public let keyId: String
    public let signedChallenge: String
    public let sharedClaims: [String: String]
    public let amr: [String]
    public var sessionId: String? = nil

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case sharedClaims = "shared_claims"
        case amr
        case sessionId = "session_id"
    }
}

public struct AuthVerifyResponse: Codable {
    public let sessionToken: String
    public let serverDid: String
    public let serverKeyId: String
    public let serverSignature: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}
