import Foundation

/// Issuer metadata resolved from well-known endpoints.
public struct IssuerMetadata: @unchecked Sendable {
    public let credentialIssuer: String
    public let credentialEndpoint: String
    public let credentialConfigurationsSupported: [String: [String: Any]]
    public let tokenEndpoint: String
    public let authorizationEndpoint: String?

    public init(credentialIssuer: String, credentialEndpoint: String, credentialConfigurationsSupported: [String: [String: Any]], tokenEndpoint: String, authorizationEndpoint: String?) {
        self.credentialIssuer = credentialIssuer
        self.credentialEndpoint = credentialEndpoint
        self.credentialConfigurationsSupported = credentialConfigurationsSupported
        self.tokenEndpoint = tokenEndpoint
        self.authorizationEndpoint = authorizationEndpoint
    }
}
