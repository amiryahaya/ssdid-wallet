import Foundation

/// Facade for multi-device management operations.
public struct DeviceApi {
    private let manager: DeviceManager

    init(manager: DeviceManager) {
        self.manager = manager
    }

    public func initiatePairing(identity: Identity) async throws -> PairingData {
        try await manager.initiatePairing(identity: identity)
    }

    public func joinPairing(
        did: String,
        pairingId: String,
        challenge: String,
        identity: Identity,
        deviceName: String
    ) async throws -> String {
        try await manager.joinPairing(
            did: did,
            pairingId: pairingId,
            challenge: challenge,
            identity: identity,
            deviceName: deviceName
        )
    }

    public func checkPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse {
        try await manager.checkPairingStatus(did: did, pairingId: pairingId)
    }

    public func approvePairing(identity: Identity, pairingId: String) async throws {
        try await manager.approvePairing(identity: identity, pairingId: pairingId)
    }

    public func listDevices(identity: Identity) async throws -> [DeviceInfo] {
        try await manager.listDevices(identity: identity)
    }

    /// Revoke a device key. Currently throws ``DeviceManagerError/notSupported(_:)``
    /// because full key revocation requires multi-device key management support.
    public func revokeDevice(identity: Identity, targetKeyId: String) async throws {
        try await manager.revokeDevice(identity: identity, targetKeyId: targetKeyId)
    }
}
