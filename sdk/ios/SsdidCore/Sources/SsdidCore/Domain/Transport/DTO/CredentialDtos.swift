import Foundation

/// Request/response DTOs for credential offer and acceptance.

public struct CredentialOfferResponse: Codable {
    public     let offerId: String
    public     let issuerDid: String
    public     let credentialType: String
    public     let claims: [String: String]
    public     var expiresAt: String? = nil

    public init(
        offerId: String,
        issuerDid: String,
        credentialType: String,
        claims: [String: String],
        expiresAt: String? = nil
    ) {
        self.offerId = offerId
        self.issuerDid = issuerDid
        self.credentialType = credentialType
        self.claims = claims
        self.expiresAt = expiresAt
    }


    enum CodingKeys: String, CodingKey {
        case offerId = "offer_id"
        case issuerDid = "issuer_did"
        case credentialType = "credential_type"
        case claims
        case expiresAt = "expires_at"
    }
}

public struct CredentialAcceptRequest: Codable {
    public     let did: String
    public     let keyId: String
    public     let signedAcceptance: String

    public init(did: String, keyId: String, signedAcceptance: String) {
        self.did = did
        self.keyId = keyId
        self.signedAcceptance = signedAcceptance
    }

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedAcceptance = "signed_acceptance"
    }
}

public struct CredentialAcceptResponse: Codable {
    public     let credential: VerifiableCredential

    public init(credential: VerifiableCredential) {
        self.credential = credential
    }
}

/// Drive authentication request with credential presentation.
public struct DriveAuthenticateRequest: Codable {
    public     let credential: VerifiableCredential
    public     var challengeId: String? = nil

    public init(credential: VerifiableCredential, challengeId: String? = nil) {
        self.credential = credential
        self.challengeId = challengeId
    }

    enum CodingKeys: String, CodingKey {
        case credential
        case challengeId = "challenge_id"
    }
}

/// Drive authentication response with session token.
public struct DriveAuthenticateResponse: Codable {
    public     let sessionToken: String
    public     var did: String? = nil
    public     let serverDid: String
    public     var serverSignature: String? = nil

    public init(sessionToken: String, did: String? = nil, serverDid: String, serverSignature: String? = nil) {
        self.sessionToken = sessionToken
        self.did = did
        self.serverDid = serverDid
        self.serverSignature = serverSignature
    }

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case serverDid = "server_did"
        case serverSignature = "server_signature"
    }
}
