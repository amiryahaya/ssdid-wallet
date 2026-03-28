import Foundation

/// A self-contained verification bundle that enables offline credential verification.
/// Contains the issuer's DID Document and optionally a cached status list snapshot.
public struct VerificationBundle: Codable {
    public let issuerDid: String
    public let didDocument: DidDocument
    public var statusList: StatusListCredential? = nil
    public let fetchedAt: String
    public let expiresAt: String

    public init(issuerDid: String, didDocument: DidDocument, statusList: StatusListCredential? = nil, fetchedAt: String, expiresAt: String) {
        self.issuerDid = issuerDid
        self.didDocument = didDocument
        self.statusList = statusList
        self.fetchedAt = fetchedAt
        self.expiresAt = expiresAt
    }
}

/// Storage for verification bundles.
protocol BundleStore: Sendable {
    func saveBundle(_ bundle: VerificationBundle) async throws
    func getBundle(issuerDid: String) async -> VerificationBundle?
    func deleteBundle(issuerDid: String) async throws
    func listBundles() async throws -> [VerificationBundle]
}
