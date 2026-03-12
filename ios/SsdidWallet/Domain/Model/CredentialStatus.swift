import Foundation

struct CredentialStatus: Codable {
    let id: String
    let type: String
    let statusPurpose: String
    let statusListIndex: String
    let statusListCredential: String
}
