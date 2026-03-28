import Foundation

/// Facade for identity lifecycle operations.
public struct IdentityApi {
    let vault: Vault
    let client: SsdidClient

    public func create(name: String, algorithm: Algorithm) async throws -> Identity {
        try await client.initIdentity(name: name, algorithm: algorithm)
    }

    public func list() async -> [Identity] {
        await vault.listIdentities()
    }

    public func get(keyId: String) async -> Identity? {
        await vault.getIdentity(keyId: keyId)
    }

    public func delete(keyId: String) async throws {
        try await vault.deleteIdentity(keyId: keyId)
    }

    public func buildDidDocument(keyId: String) async throws -> DidDocument {
        try await vault.buildDidDocument(keyId: keyId)
    }

    public func updateDidDocument(keyId: String) async throws {
        try await client.updateDidDocument(keyId: keyId)
    }

    public func deactivate(keyId: String) async throws {
        try await client.deactivateDid(keyId: keyId)
    }
}
