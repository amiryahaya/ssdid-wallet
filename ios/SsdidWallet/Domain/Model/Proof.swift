import Foundation

struct Proof: Codable {
    let type: String
    let created: String
    let verificationMethod: String
    let proofPurpose: String
    let proofValue: String
    var domain: String? = nil
    var challenge: String? = nil
}
