import Foundation

/// Protocol for verifying DID signatures and credentials.
protocol Verifier {
    /// Resolves a DID Document from the registry.
    func resolveDid(did: String) async throws -> DidDocument

    /// Verifies a signature against data for a given DID and key ID.
    func verifySignature(did: String, keyId: String, signature: Data, data: Data) async throws -> Bool

    /// Verifies a multibase-encoded challenge response.
    func verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String) async throws -> Bool

    /// Verifies a verifiable credential's proof and expiration.
    func verifyCredential(credential: VerifiableCredential) async throws -> Bool
}

/// Concrete Verifier implementation that resolves DIDs via the Registry
/// and verifies signatures using the appropriate CryptoProvider.
final class VerifierImpl: Verifier {

    private let didResolver: DidResolver
    private let classicalProvider: CryptoProvider
    private let pqcProvider: CryptoProvider

    init(didResolver: DidResolver, classicalProvider: CryptoProvider, pqcProvider: CryptoProvider) {
        self.didResolver = didResolver
        self.classicalProvider = classicalProvider
        self.pqcProvider = pqcProvider
    }

    func resolveDid(did: String) async throws -> DidDocument {
        _ = try Did.validate(did)
        return try await didResolver.resolve(did: did)
    }

    func verifySignature(did: String, keyId: String, signature: Data, data: Data) async throws -> Bool {
        _ = try Did.validate(did)
        let doc = try await resolveDid(did: did)
        guard let vm = doc.verificationMethod.first(where: { $0.id == keyId }) else {
            throw VerifierError.keyNotFound(keyId: keyId, did: did)
        }

        let publicKey = try Multibase.decode(vm.publicKeyMultibase)
        let algorithm = try algorithmFromW3cType(vm.type)
        let provider = algorithm.isPostQuantum ? pqcProvider : classicalProvider
        return try provider.verify(algorithm: algorithm, publicKey: publicKey, signature: signature, data: data)
    }

    func verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String) async throws -> Bool {
        let signature = try Multibase.decode(signedChallenge)
        return try await verifySignature(
            did: did,
            keyId: keyId,
            signature: signature,
            data: Data(challenge.utf8)
        )
    }

    func verifyCredential(credential: VerifiableCredential) async throws -> Bool {
        let formatter = ISO8601DateFormatter()

        // Check not-before (nbf): credential must not be used before issuance date
        if let issuedDate = formatter.date(from: credential.issuanceDate), issuedDate > Date() {
            throw VerifierError.credentialNotYetValid(credential.issuanceDate)
        }

        // Check expiration
        if let expirationDate = credential.expirationDate {
            if let expDate = formatter.date(from: expirationDate), expDate < Date() {
                throw VerifierError.credentialExpired(expirationDate)
            }
        }

        // Verify issuer signature
        let proof = credential.proof
        let issuerDid = Did.fromKeyId(proof.verificationMethod)
        let doc = try await resolveDid(did: issuerDid.value)

        guard let vm = doc.verificationMethod.first(where: { $0.id == proof.verificationMethod }) else {
            throw VerifierError.keyNotFound(keyId: proof.verificationMethod, did: issuerDid.value)
        }

        let publicKey = try Multibase.decode(vm.publicKeyMultibase)
        let algorithm = try algorithmFromW3cType(vm.type)
        let provider = algorithm.isPostQuantum ? pqcProvider : classicalProvider
        let signature = try Multibase.decode(proof.proofValue)

        // Canonical signed data = credential JSON with proof field removed
        let signedData = try canonicalizeCredentialWithoutProof(credential)
        return try provider.verify(algorithm: algorithm, publicKey: publicKey, signature: signature, data: signedData)
    }

    // MARK: - Helpers

    private func algorithmFromW3cType(_ type: String) throws -> Algorithm {
        guard let algorithm = Algorithm.fromW3cType(type) else {
            throw VerifierError.unknownAlgorithmType(type)
        }
        return algorithm
    }

    private func canonicalizeCredentialWithoutProof(_ credential: VerifiableCredential) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(credential)

        // Parse, remove proof, re-canonicalize recursively
        guard var dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw VerifierError.serializationFailed
        }
        dict.removeValue(forKey: "proof")

        return Data(JsonUtils.canonicalJson(dict).utf8)
    }
}

/// Errors specific to verification operations.
enum VerifierError: Error, LocalizedError {
    case keyNotFound(keyId: String, did: String)
    case unknownAlgorithmType(String)
    case credentialExpired(String)
    case credentialNotYetValid(String)
    case serializationFailed

    var errorDescription: String? {
        switch self {
        case .keyNotFound(let keyId, let did):
            return "Key \(keyId) not found in DID Document for \(did)"
        case .unknownAlgorithmType(let type):
            return "Unknown W3C verification method type: \(type)"
        case .credentialExpired(let date):
            return "Credential expired at \(date)"
        case .credentialNotYetValid(let date):
            return "Credential not yet valid until \(date)"
        case .serializationFailed:
            return "Failed to serialize credential for verification"
        }
    }
}
