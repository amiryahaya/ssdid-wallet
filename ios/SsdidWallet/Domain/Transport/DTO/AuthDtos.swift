import Foundation

/// Request/response DTOs for authentication flows.

struct ClaimRequest: Codable {
    let key: String
    var required: Bool = false
}

struct AuthChallengeResponse: Codable {
    let challenge: String
    let serverName: String
    let serverDid: String
    let serverKeyId: String

    enum CodingKeys: String, CodingKey {
        case challenge
        case serverName = "server_name"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
    }
}

struct AuthVerifyRequest: Codable {
    let did: String
    let keyId: String
    let signedChallenge: String
    let sharedClaims: [String: String]
    let amr: [String]
    var sessionId: String? = nil

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case sharedClaims = "shared_claims"
        case amr
        case sessionId = "session_id"
    }
}

struct AuthVerifyResponse: Codable {
    let sessionToken: String
    let serverDid: String
    let serverKeyId: String
    let serverSignature: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}
