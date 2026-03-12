import Foundation

/// Request/response DTOs for credential offer and acceptance.

struct CredentialOfferResponse: Codable {
    let offerId: String
    let issuerDid: String
    let credentialType: String
    let claims: [String: String]
    var expiresAt: String? = nil

    enum CodingKeys: String, CodingKey {
        case offerId = "offer_id"
        case issuerDid = "issuer_did"
        case credentialType = "credential_type"
        case claims
        case expiresAt = "expires_at"
    }
}

struct CredentialAcceptRequest: Codable {
    let did: String
    let keyId: String
    let signedAcceptance: String

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedAcceptance = "signed_acceptance"
    }
}

struct CredentialAcceptResponse: Codable {
    let credential: VerifiableCredential
}

/// Drive authentication request with credential presentation.
struct DriveAuthenticateRequest: Codable {
    let credential: VerifiableCredential
    var challengeId: String? = nil

    enum CodingKeys: String, CodingKey {
        case credential
        case challengeId = "challenge_id"
    }
}

/// Drive authentication response with session token.
struct DriveAuthenticateResponse: Codable {
    let sessionToken: String
    var did: String? = nil
    let serverDid: String
    var serverSignature: String? = nil

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case serverDid = "server_did"
        case serverSignature = "server_signature"
    }
}
