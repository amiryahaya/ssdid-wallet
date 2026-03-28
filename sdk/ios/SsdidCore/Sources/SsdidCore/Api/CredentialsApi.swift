import Foundation

/// Facade for verifiable credential storage operations.
public struct CredentialsApi {
    let vault: Vault

    public func store(_ credential: VerifiableCredential) async throws {
        try await vault.storeCredential(credential)
    }

    public func list() async -> [VerifiableCredential] {
        await vault.listCredentials()
    }

    public func getForDid(_ did: String) async -> [VerifiableCredential] {
        await vault.getCredentialsForDid(did)
    }

    public func delete(credentialId: String) async throws {
        try await vault.deleteCredential(credentialId: credentialId)
    }
}
