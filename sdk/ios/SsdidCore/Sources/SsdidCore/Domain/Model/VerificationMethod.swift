import Foundation

public struct VerificationMethod: Codable, Equatable, Sendable {
    public let id: String
    public let type: String
    public let controller: String
    public var publicKeyMultibase: String = ""
    public var publicKeyJwk: [String: String]? = nil

    public init(
        id: String,
        type: String,
        controller: String,
        publicKeyMultibase: String = "",
        publicKeyJwk: [String: String]? = nil
    ) {
        self.id = id
        self.type = type
        self.controller = controller
        self.publicKeyMultibase = publicKeyMultibase
        self.publicKeyJwk = publicKeyJwk
    }
}
