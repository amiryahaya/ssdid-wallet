import Foundation

/// Facade for online credential verification operations.
public struct VerifierApi {
    let verifier: Verifier

    public func verifyCredential(_ credential: VerifiableCredential) async throws -> Bool {
        try await verifier.verifyCredential(credential: credential)
    }

    public func verifySignature(did: String, keyId: String, signature: Data, data: Data) async throws -> Bool {
        try await verifier.verifySignature(did: did, keyId: keyId, signature: signature, data: data)
    }

    public func resolveDid(_ did: String) async throws -> DidDocument {
        try await verifier.resolveDid(did: did)
    }
}
