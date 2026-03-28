import Foundation

/// Request/response DTOs for server (service) interactions.

public struct RegisterStartRequest: Codable {
    public     let did: String
    public     let keyId: String

    public init(did: String, keyId: String) {
        self.did = did
        self.keyId = keyId
    }

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
    }
}

public struct RegisterStartResponse: Codable {
    public     let challenge: String
    public     let serverDid: String
    public     let serverKeyId: String
    public     let serverSignature: String

    public init(challenge: String, serverDid: String, serverKeyId: String, serverSignature: String) {
        self.challenge = challenge
        self.serverDid = serverDid
        self.serverKeyId = serverKeyId
        self.serverSignature = serverSignature
    }

    enum CodingKeys: String, CodingKey {
        case challenge
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}

public struct RegisterVerifyRequest: Codable {
    public     let did: String
    public     let keyId: String
    public     let signedChallenge: String
    public     let inviteToken: String?
    public     let sharedClaims: [String: String]?

    public init(did: String, keyId: String, signedChallenge: String, inviteToken: String? = nil, sharedClaims: [String: String]? = nil) {
        self.did = did
        self.keyId = keyId
        self.signedChallenge = signedChallenge
        self.inviteToken = inviteToken
        self.sharedClaims = sharedClaims
    }

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case inviteToken = "invite_token"
        case sharedClaims = "shared_claims"
    }
}

public struct RegisterVerifyResponse: Codable {
    public     let credential: VerifiableCredential

    public init(credential: VerifiableCredential) {
        self.credential = credential
    }
}

public struct AuthenticateRequest: Codable {
    public     let credential: VerifiableCredential

    public init(credential: VerifiableCredential) {
        self.credential = credential
    }
}

public struct AuthenticateResponse: Codable {
    public let sessionToken: String
    public let serverDid: String
    public let serverKeyId: String
    public var serverSignature: String? = nil
    public var status: String? = nil
    public var did: String? = nil

    public init(sessionToken: String, serverDid: String, serverKeyId: String, serverSignature: String? = nil, status: String? = nil, did: String? = nil) {
        self.sessionToken = sessionToken
        self.serverDid = serverDid
        self.serverKeyId = serverKeyId
        self.serverSignature = serverSignature
        self.status = status
        self.did = did
    }

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
        case status
        case did
    }
}

public struct TxChallengeRequest: Codable {
    public     let sessionToken: String

    public init(sessionToken: String) {
        self.sessionToken = sessionToken
    }

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
    }
}

public struct TxChallengeResponse: Codable {
    public     let challenge: String
    public     var transaction: [String: String] = [:]
    public     var did: String? = nil

    public init(challenge: String, transaction: [String: String] = [:], did: String? = nil) {
        self.challenge = challenge
        self.transaction = transaction
        self.did = did
    }
}

public struct TxSubmitRequest: Codable {
    public     let sessionToken: String
    public     let did: String
    public     let keyId: String
    public     let signedChallenge: String
    public     let transaction: [String: String]

    public init(sessionToken: String, did: String, keyId: String, signedChallenge: String, transaction: [String: String]) {
        self.sessionToken = sessionToken
        self.did = did
        self.keyId = keyId
        self.signedChallenge = signedChallenge
        self.transaction = transaction
    }

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case keyId = "key_id"
        case signedChallenge = "signed_challenge"
        case transaction
    }
}

public struct TxSubmitResponse: Codable {
    public let transactionId: String
    public let status: String

    public init(transactionId: String, status: String) {
        self.transactionId = transactionId
        self.status = status
    }

    enum CodingKeys: String, CodingKey {
        case transactionId = "transaction_id"
        case status
    }
}
