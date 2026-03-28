import Foundation

// MARK: - Protocols

/// Abstraction over registry API calls needed by DeviceManager.
public protocol DeviceManagerRegistryClient: Sendable {
    func initPairing(did: String, request: PairingInitRequest) async throws -> PairingInitResponse
    func joinPairing(did: String, pairingId: String, request: PairingJoinRequest) async throws -> PairingJoinResponse
    func approvePairing(did: String, pairingId: String, request: PairingApproveRequest) async throws
    func getPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse
}

/// Abstraction over SsdidClient for DID document updates.
public protocol DeviceManagerSsdidClientProvider: Sendable {
    func updateDidDocument(keyId: String) async throws
}

// MARK: - Models

/// Data returned after initiating a pairing session.
public struct PairingData {
    public let pairingId: String
    public let challenge: String
    public let did: String

    public init(pairingId: String, challenge: String, did: String) {
        self.pairingId = pairingId
        self.challenge = challenge
        self.did = did
    }
}

/// Information about a device enrolled under a DID.
public struct DeviceInfo {
    public let deviceId: String
    public let name: String
    public let platform: String
    public let keyId: String
    public let enrolledAt: String
    public let isPrimary: Bool

    public init(deviceId: String, name: String, platform: String, keyId: String, enrolledAt: String, isPrimary: Bool) {
        self.deviceId = deviceId
        self.name = name
        self.platform = platform
        self.keyId = keyId
        self.enrolledAt = enrolledAt
        self.isPrimary = isPrimary
    }
}

// MARK: - Errors

public enum DeviceManagerError: Error, LocalizedError {
    case cannotRevokePrimaryKey
    case notSupported(String)

    public var errorDescription: String? {
        switch self {
        case .cannotRevokePrimaryKey:
            return "Cannot revoke primary device key"
        case .notSupported(let reason):
            return "Operation not supported: \(reason)"
        }
    }
}

// MARK: - DeviceManager

/// Manages multi-device enrollment: pairing initiation, joining, approval, revocation, and listing.
public final class DeviceManager: @unchecked Sendable {
    private let vault: Vault
    private let registryClient: DeviceManagerRegistryClient
    private let ssdidClientProvider: DeviceManagerSsdidClientProvider
    private let deviceName: String
    private let platform: String

    public     init(
        vault: Vault,
        registryClient: DeviceManagerRegistryClient,
        ssdidClientProvider: DeviceManagerSsdidClientProvider,
        deviceName: String,
        platform: String
    ) {
        self.vault = vault
        self.registryClient = registryClient
        self.ssdidClientProvider = ssdidClientProvider
        self.deviceName = deviceName
        self.platform = platform
    }

    /// Initiates a pairing session by generating a challenge and calling the registry.
    public     func initiatePairing(identity: Identity) async throws -> PairingData {
        let challenge = UUID().uuidString
        let response = try await registryClient.initPairing(
            did: identity.did,
            request: PairingInitRequest(
                did: identity.did,
                challenge: challenge,
                primaryKeyId: identity.keyId
            )
        )
        return PairingData(
            pairingId: response.pairingId,
            challenge: challenge,
            did: identity.did
        )
    }

    /// Joins an existing pairing session by signing the challenge and submitting to the registry.
    public     func joinPairing(
        did: String,
        pairingId: String,
        challenge: String,
        identity: Identity,
        deviceName: String
    ) async throws -> String {
        let payload = "join:\(challenge)".data(using: .utf8)!
        let signature = try await vault.sign(keyId: identity.keyId, data: payload)
        let response = try await registryClient.joinPairing(
            did: did,
            pairingId: pairingId,
            request: PairingJoinRequest(
                pairingId: pairingId,
                publicKey: identity.publicKeyMultibase,
                signedChallenge: Multibase.encode(signature),
                deviceName: deviceName,
                platform: platform
            )
        )
        return response.status
    }

    /// Approves a pending pairing request, then updates the DID document.
    public     func approvePairing(identity: Identity, pairingId: String) async throws {
        let payload = "approve:\(pairingId)".data(using: .utf8)!
        let signature = try await vault.sign(keyId: identity.keyId, data: payload)
        try await registryClient.approvePairing(
            did: identity.did,
            pairingId: pairingId,
            request: PairingApproveRequest(
                did: identity.did,
                keyId: identity.keyId,
                signedApproval: Multibase.encode(signature)
            )
        )
        try await ssdidClientProvider.updateDidDocument(keyId: identity.keyId)
    }

    /// Revoke a device key. Currently publishes a DID document update but does not
    /// remove the target key from the document. Full key revocation requires
    /// multi-device key management support in a future release.
    public     func revokeDevice(identity: Identity, targetKeyId: String) async throws {
        guard targetKeyId != identity.keyId else {
            throw DeviceManagerError.cannotRevokePrimaryKey
        }
        // TODO: Remove targetKeyId from the DID document's verification methods
        // when multi-device key management is fully implemented. Currently the
        // rebuilt DID document does not exclude the target key.
        throw DeviceManagerError.notSupported(
            "Device key revocation requires multi-device key management support"
        )
    }

    /// Lists all devices enrolled under the identity by inspecting the DID document.
    public     func listDevices(identity: Identity) async throws -> [DeviceInfo] {
        let didDoc = try await vault.buildDidDocument(keyId: identity.keyId)
        let methods = didDoc.verificationMethod

        if methods.isEmpty {
            return [
                DeviceInfo(
                    deviceId: identity.keyId,
                    name: deviceName,
                    platform: platform,
                    keyId: identity.keyId,
                    enrolledAt: identity.createdAt,
                    isPrimary: true
                )
            ]
        }

        return methods.map { method in
            let keyId = method.id
            return DeviceInfo(
                deviceId: keyId,
                name: keyId == identity.keyId ? deviceName : "Unknown Device",
                platform: keyId == identity.keyId ? platform : "unknown",
                keyId: keyId,
                enrolledAt: identity.createdAt,
                isPrimary: keyId == identity.keyId
            )
        }
    }

    /// Checks the current status of a pairing session.
    public     func checkPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse {
        return try await registryClient.getPairingStatus(did: did, pairingId: pairingId)
    }
}

// MARK: - Adapters

/// Bridges RegistryApi to the DeviceManagerRegistryClient protocol.
public final class HttpDeviceManagerRegistryClient: DeviceManagerRegistryClient {
    private let registryApi: RegistryApi

    public     init(registryApi: RegistryApi) {
        self.registryApi = registryApi
    }

    public     func initPairing(did: String, request: PairingInitRequest) async throws -> PairingInitResponse {
        try await registryApi.initPairing(did: did, request: request)
    }

    public     func joinPairing(did: String, pairingId: String, request: PairingJoinRequest) async throws -> PairingJoinResponse {
        try await registryApi.joinPairing(did: did, pairingId: pairingId, request: request)
    }

    public     func approvePairing(did: String, pairingId: String, request: PairingApproveRequest) async throws {
        try await registryApi.approvePairing(did: did, pairingId: pairingId, request: request)
    }

    public     func getPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse {
        try await registryApi.getPairingStatus(did: did, pairingId: pairingId)
    }
}

/// Bridges SsdidClient to the DeviceManagerSsdidClientProvider protocol.
public final class SsdidClientDeviceProvider: DeviceManagerSsdidClientProvider {
    private let ssdidClient: SsdidClient

    public     init(ssdidClient: SsdidClient) {
        self.ssdidClient = ssdidClient
    }

    public     func updateDidDocument(keyId: String) async throws {
        try await ssdidClient.updateDidDocument(keyId: keyId)
    }
}
