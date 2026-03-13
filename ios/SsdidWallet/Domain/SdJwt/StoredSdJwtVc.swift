import Foundation

/// Metadata for a stored SD-JWT VC, alongside the compact SD-JWT string.
struct StoredSdJwtVc: Codable, Equatable {
    let id: String
    let compact: String
    let issuer: String
    let subject: String
    let type: String
    let claims: [String: String]
    let disclosableClaims: [String]
    let issuedAt: Int64
    let expiresAt: Int64?

    init(
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
