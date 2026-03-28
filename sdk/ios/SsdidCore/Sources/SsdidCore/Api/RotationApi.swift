import Foundation

/// Facade for KERI-inspired key rotation operations.
public struct RotationApi {
    let manager: KeyRotationManager

    public func prepare(identity: Identity) async throws -> String {
        try await manager.prepareRotation(identity: identity)
    }

    public func execute(identity: Identity) async throws -> Identity {
        try await manager.executeRotation(identity: identity)
    }

    public func getStatus(identity: Identity) async -> RotationStatus {
        await manager.getRotationStatus(identity: identity)
    }
}
