import Foundation

struct VerificationMethod: Codable, Equatable {
    let id: String
    let type: String
    let controller: String
    var publicKeyMultibase: String = ""
    var publicKeyJwk: [String: String]? = nil
}
