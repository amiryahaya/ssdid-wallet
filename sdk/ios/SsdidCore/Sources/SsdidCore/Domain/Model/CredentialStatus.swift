import Foundation

public struct CredentialStatus: Codable {
    public let id: String
    public let type: String
    public let statusPurpose: String
    public let statusListIndex: String
    public let statusListCredential: String

    public init(id: String, type: String, statusPurpose: String, statusListIndex: String, statusListCredential: String) {
        self.id = id
        self.type = type
        self.statusPurpose = statusPurpose
        self.statusListIndex = statusListIndex
        self.statusListCredential = statusListCredential
    }
}
