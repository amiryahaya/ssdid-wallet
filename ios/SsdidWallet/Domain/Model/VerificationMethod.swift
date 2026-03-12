import Foundation

struct VerificationMethod: Codable {
    let id: String
    let type: String
    let controller: String
    let publicKeyMultibase: String
}
