import Foundation

/// Metadata for a stored SD-JWT VC, alongside the compact SD-JWT string.
public struct StoredSdJwtVc: Codable, Equatable {
    public let id: String
    public let compact: String
    public let issuer: String
    public let subject: String
    public let type: String
    public let claims: [String: String]
    public let disclosableClaims: [String]
    public let issuedAt: Int64
    public let expiresAt: Int64?

    public init(
        id: String,
        compact: String,
        issuer: String,
        subject: String,
        type: String,
        claims: [String: String],
        disclosableClaims: [String],
        issuedAt: Int64,
        expiresAt: Int64? = nil
    ) {
        self.id = id
        self.compact = compact
        self.issuer = issuer
        self.subject = subject
        self.type = type
        self.claims = claims
        self.disclosableClaims = disclosableClaims
        self.issuedAt = issuedAt
        self.expiresAt = expiresAt
    }
}
