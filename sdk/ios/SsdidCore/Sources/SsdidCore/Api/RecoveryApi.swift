import Foundation

/// Facade for identity recovery operations.
public struct RecoveryApi {
    private let manager: RecoveryManager

    init(manager: RecoveryManager) {
        self.manager = manager
    }

    public func generateRecoveryKey(identity: Identity) async throws -> Data {
        try await manager.generateRecoveryKey(identity: identity)
    }

    public func hasRecoveryKey(keyId: String) async -> Bool {
        await manager.hasRecoveryKey(keyId: keyId)
    }

    public func restoreWithRecoveryKey(
        did: String,
        recoveryPrivateKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ) async throws -> Identity {
        try await manager.restoreWithRecoveryKey(
            did: did,
            recoveryPrivateKeyBase64: recoveryPrivateKeyBase64,
            name: name,
            algorithm: algorithm
        )
    }
}
