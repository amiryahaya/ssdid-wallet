import Foundation

public struct StatusListCredential: Codable {
    public let context: [String]
    public let id: String?
    public let type: [String]
    public let issuer: String
    public let credentialSubject: StatusListSubject
    public let proof: Proof?

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, type, issuer, credentialSubject, proof
    }

    public init(
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

public struct StatusListSubject: Codable {
    public let type: String
    public let statusPurpose: String
    public let encodedList: String
}
