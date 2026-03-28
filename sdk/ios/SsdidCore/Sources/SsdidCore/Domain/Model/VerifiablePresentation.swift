import Foundation

/// W3C Verifiable Presentation wrapper for presenting one or more VCs.
/// Used for JSON-LD VC presentations. For SD-JWT VCs, the presentation
/// is implicit (SD-JWT + selected disclosures + KB-JWT).
public struct VerifiablePresentation: Codable {
    public let context: [String]
    public let type: [String]
    public let holder: String
    public var verifiableCredential: [VerifiableCredential]
    public var proof: Proof?

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case type, holder, verifiableCredential, proof
    }

    public init(
        context: [String] = ["https://www.w3.org/ns/credentials/v2"],
        type: [String] = ["VerifiablePresentation"],
        holder: String,
        verifiableCredential: [VerifiableCredential] = [],
        proof: Proof? = nil
    ) {
        self.context = context
        self.type = type
        self.holder = holder
        self.verifiableCredential = verifiableCredential
        self.proof = proof
    }
}
