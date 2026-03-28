import Foundation

public struct Proof: Codable {
    public let type: String
    public let created: String
    public let verificationMethod: String
    public let proofPurpose: String
    public let proofValue: String
    public var domain: String? = nil
    public var challenge: String? = nil

    public init(
        type: String,
        created: String,
        verificationMethod: String,
        proofPurpose: String,
        proofValue: String,
        domain: String? = nil,
        challenge: String? = nil
    ) {
        self.type = type
        self.created = created
        self.verificationMethod = verificationMethod
        self.proofPurpose = proofPurpose
        self.proofValue = proofValue
        self.domain = domain
        self.challenge = challenge
    }
}
