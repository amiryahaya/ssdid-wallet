import Foundation

struct StatusListCredential: Codable {
    let context: [String]
    let id: String?
    let type: [String]
    let issuer: String
    let credentialSubject: StatusListSubject
    let proof: Proof?

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, type, issuer, credentialSubject, proof
    }

    init(
        context: [String] = [],
        id: String? = nil,
        type: [String],
        issuer: String,
        credentialSubject: StatusListSubject,
        proof: Proof? = nil
    ) {
        self.context = context
        self.id = id
        self.type = type
        self.issuer = issuer
        self.credentialSubject = credentialSubject
        self.proof = proof
    }
}

struct StatusListSubject: Codable {
    let type: String
    let statusPurpose: String
    let encodedList: String
}
