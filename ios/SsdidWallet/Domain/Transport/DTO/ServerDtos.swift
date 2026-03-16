import Foundation

/// Request/response DTOs for server (service) interactions.

struct RegisterStartRequest: Codable {
    let did: String
    let keyId: String

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
    }
}

struct RegisterStartResponse: Codable {
    let challenge: String
    let serverDid: String
    let serverKeyId: String
    let serverSignature: String

    enum CodingKeys: String, CodingKey {
        case challenge
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}

struct RegisterVerifyRequest: Codable {
    let did: String
    let keyId: String
    let signedChallenge: String
    let inviteToken: String?
    let sharedClaims: [String: String]?

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case inviteToken = "invite_token"
        case sharedClaims = "shared_claims"
    }
}

struct RegisterVerifyResponse: Codable {
    let credential: VerifiableCredential
}

struct AuthenticateRequest: Codable {
    let credential: VerifiableCredential
}

struct AuthenticateResponse: Codable {
    let sessionToken: String
    let serverDid: String
    let serverKeyId: String
    var serverSignature: String? = nil
    var status: String? = nil
    var did: String? = nil

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
        case status
        case did
    }
}

struct TxChallengeRequest: Codable {
    let sessionToken: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
    }
}

struct TxChallengeResponse: Codable {
    let challenge: String
    var transaction: [String: String] = [:]
    var did: String? = nil
}

struct TxSubmitRequest: Codable {
    let sessionToken: String
    let did: String
    let keyId: String
    let signedChallenge: String
    let transaction: [String: String]

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case transaction
    }
}

struct TxSubmitResponse: Codable {
    let transactionId: String
    let status: String

    enum CodingKeys: String, CodingKey {
        case transactionId = "transaction_id"
        case status
    }
}
