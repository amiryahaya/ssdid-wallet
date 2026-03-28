import Foundation

/// Facade for cryptographic vault operations (signing, proof creation).
public struct VaultApi {
    let vault: Vault

    public func sign(keyId: String, data: Data) async throws -> Data {
        try await vault.sign(keyId: keyId, data: data)
    }

    public func createProof(
        keyId: String,
        document: [String: Any],
        proofPurpose: String,
        challenge: String? = nil,
        domain: String? = nil
    ) async throws -> Proof {
        try await vault.createProof(
            keyId: keyId,
            document: document,
            proofPurpose: proofPurpose,
            challenge: challenge,
            domain: domain
        )
    }
}
