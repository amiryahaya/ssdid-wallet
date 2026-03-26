import Foundation

/// A self-contained verification bundle that enables offline credential verification.
/// Contains the issuer's DID Document and optionally a cached status list snapshot.
struct VerificationBundle: Codable {
    let issuerDid: String
    let didDocument: DidDocument
    var statusList: StatusListCredential? = nil
    let fetchedAt: String
    let expiresAt: String
}

/// Storage for verification bundles.
protocol BundleStore: Sendable {
    func saveBundle(_ bundle: VerificationBundle) async throws
    func getBundle(issuerDid: String) async -> VerificationBundle?
    func deleteBundle(issuerDid: String) async throws
    func listBundles() async throws -> [VerificationBundle]
}
