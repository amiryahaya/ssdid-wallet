import Foundation

/// Issuer metadata resolved from well-known endpoints.
struct IssuerMetadata: @unchecked Sendable {
    let credentialIssuer: String
    let credentialEndpoint: String
    let credentialConfigurationsSupported: [String: [String: Any]]
    let tokenEndpoint: String
    let authorizationEndpoint: String?
}
