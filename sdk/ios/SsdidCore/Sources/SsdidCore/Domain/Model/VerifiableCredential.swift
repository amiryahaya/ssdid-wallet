import Foundation

public struct VerifiableCredential: Codable, Identifiable, @unchecked Sendable {
    public let context: [String]
    public let id: String
    public let type: [String]
    public let issuer: String
    public let issuanceDate: String
    public var expirationDate: String? = nil
    public let credentialSubject: CredentialSubject
    public var credentialStatus: CredentialStatus? = nil
    public let proof: Proof

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, type, issuer, issuanceDate, expirationDate
        case credentialSubject, credentialStatus, proof
    }

    public init(
        context: [String] = ["https://www.w3.org/ns/credentials/v2"],
        id: String,
        type: [String],
        issuer: String,
        issuanceDate: String,
        expirationDate: String? = nil,
        credentialSubject: CredentialSubject,
        credentialStatus: CredentialStatus? = nil,
        proof: Proof
    ) {
        self.context = context
        self.id = id
        self.type = type
        self.issuer = issuer
        self.issuanceDate = issuanceDate
        self.expirationDate = expirationDate
        self.credentialSubject = credentialSubject
        self.credentialStatus = credentialStatus
        self.proof = proof
    }
}
