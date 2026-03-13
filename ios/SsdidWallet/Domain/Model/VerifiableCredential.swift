import Foundation

struct VerifiableCredential: Codable, Identifiable, @unchecked Sendable {
    let context: [String]
    let id: String
    let type: [String]
    let issuer: String
    let issuanceDate: String
    var expirationDate: String? = nil
    let credentialSubject: CredentialSubject
    var credentialStatus: CredentialStatus? = nil
    let proof: Proof

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, type, issuer, issuanceDate, expirationDate
        case credentialSubject, credentialStatus, proof
    }

    init(
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
