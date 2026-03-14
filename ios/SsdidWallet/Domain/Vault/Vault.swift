import Foundation

/// Error types for vault operations.
enum VaultError: Error, LocalizedError {
    case identityNotFound(String)
    case identityNameExists(String)
    case privateKeyNotFound(String)
    case credentialNotFound(String)
    case storageFailed(String)

    var errorDescription: String? {
        switch self {
        case .identityNotFound(let keyId):
            return "Identity not found: \(keyId)"
        case .identityNameExists(let name):
            return "An identity with the name \"\(name)\" already exists"
        case .privateKeyNotFound(let keyId):
            return "Private key not found for: \(keyId)"
        case .credentialNotFound(let id):
            return "Credential not found: \(id)"
        case .storageFailed(let reason):
            return "Storage operation failed: \(reason)"
        }
    }
}

/// Vault protocol for managing identities, credentials, and cryptographic operations.
protocol Vault: Sendable {
    /// Creates a new identity with the given name and algorithm.
    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity

    /// Retrieves an identity by its key ID.
    func getIdentity(keyId: String) async -> Identity?

    /// Lists all stored identities.
    func listIdentities() async -> [Identity]

    /// Deletes an identity by its key ID.
    func deleteIdentity(keyId: String) async throws

    /// Signs data using the private key associated with the given key ID.
    func sign(keyId: String, data: Data) async throws -> Data

    /// Builds a DID Document for the identity associated with the given key ID.
    func buildDidDocument(keyId: String) async throws -> DidDocument

    /// Creates a W3C Data Integrity proof for a document.
    func createProof(
        keyId: String,
        document: [String: Any],
        proofPurpose: String,
        challenge: String?,
        domain: String?
    ) async throws -> Proof

    /// Stores a verifiable credential.
    func storeCredential(_ credential: VerifiableCredential) async throws

    /// Lists all stored verifiable credentials.
    func listCredentials() async -> [VerifiableCredential]

    /// Retrieves a credential whose subject ID matches the given DID.
    func getCredentialForDid(_ did: String) async -> VerifiableCredential?

    /// Deletes a credential by its ID.
    func deleteCredential(credentialId: String) async throws

    // MARK: - mdoc / mDL

    /// Stores an mdoc credential.
    func storeMDoc(_ mdoc: StoredMDoc) async throws

    /// Lists all stored mdoc credentials.
    func listMDocs() async -> [StoredMDoc]

    /// Retrieves an mdoc by its ID.
    func getMDoc(id: String) async -> StoredMDoc?

    /// Deletes an mdoc by its ID.
    func deleteMDoc(id: String) async throws
}
