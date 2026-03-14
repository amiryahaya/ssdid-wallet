import Foundation

/// W3C Verifiable Presentation wrapper for presenting one or more VCs.
/// Used for JSON-LD VC presentations. For SD-JWT VCs, the presentation
/// is implicit (SD-JWT + selected disclosures + KB-JWT).
struct VerifiablePresentation: Codable {
    let context: [String]
    let type: [String]
    let holder: String
    var verifiableCredential: [VerifiableCredential]
    var proof: Proof?

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case type, holder, verifiableCredential, proof
    }

    init(
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
