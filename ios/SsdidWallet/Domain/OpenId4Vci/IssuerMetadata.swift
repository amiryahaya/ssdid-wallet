import Foundation

/// Issuer metadata resolved from well-known endpoints.
struct IssuerMetadata {
    let credentialIssuer: String
    let credentialEndpoint: String
    let credentialConfigurationsSupported: [String: [String: Any]]
    let tokenEndpoint: String
    let authorizationEndpoint: String?
}
