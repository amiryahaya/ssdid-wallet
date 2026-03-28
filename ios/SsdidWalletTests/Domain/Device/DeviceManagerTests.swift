@testable import SsdidCore
import XCTest
@testable import SsdidWallet

// MARK: - Test Doubles

/// Stub Vault that records calls and returns preconfigured results.
private final class StubVault: Vault, @unchecked Sendable {
    var signResult: Result<Data, Error> = .success(Data([1, 2, 3, 4]))
    var buildDidDocumentResult: Result<DidDocument, Error>?
    var signCalledWith: (keyId: String, data: Data)?

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        fatalError("Not needed in DeviceManager tests")
    }
    func getIdentity(keyId: String) async -> Identity? { nil }
    func listIdentities() async -> [Identity] { [] }
    func deleteIdentity(keyId: String) async throws {}
    func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {}

    func sign(keyId: String, data: Data) async throws -> Data {
        signCalledWith = (keyId, data)
        return try signResult.get()
    }

    func buildDidDocument(keyId: String) async throws -> DidDocument {
        guard let result = buildDidDocumentResult else {
            fatalError("buildDidDocumentResult not configured")
        }
        return try result.get()
    }

    // Remaining Vault protocol requirements — stubs
    func createProof(keyId: String, document: [String: Any], proofPurpose: String, challenge: String?, domain: String?) async throws -> Proof {
        fatalError("Not needed in DeviceManager tests")
    }
    func storeCredential(_ credential: VerifiableCredential) async throws {}
    func listCredentials() async -> [VerifiableCredential] { [] }
    func getCredentialForDid(_ did: String) async -> VerifiableCredential? { nil }
    func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] { [] }
    func deleteCredential(credentialId: String) async throws {}
}

/// Stub RegistryApi replacement that captures calls and returns preconfigured responses.
private final class StubRegistryClient: DeviceManagerRegistryClient, @unchecked Sendable {
    var initPairingResult: Result<PairingInitResponse, Error> = .success(PairingInitResponse(pairingId: "pairing-1"))
    var joinPairingResult: Result<PairingJoinResponse, Error> = .success(PairingJoinResponse(status: "joined"))
    var approvePairingResult: Result<Void, Error> = .success(())
    var getPairingStatusResult: Result<PairingStatusResponse, Error> = .success(PairingStatusResponse(status: "pending"))

    var initPairingCalledWith: (did: String, request: PairingInitRequest)?
    var joinPairingCalledWith: (did: String, pairingId: String, request: PairingJoinRequest)?
    var approvePairingCalledWith: (did: String, pairingId: String, request: PairingApproveRequest)?
    var getPairingStatusCalledWith: (did: String, pairingId: String)?

    func initPairing(did: String, request: PairingInitRequest) async throws -> PairingInitResponse {
        initPairingCalledWith = (did, request)
        return try initPairingResult.get()
    }

    func joinPairing(did: String, pairingId: String, request: PairingJoinRequest) async throws -> PairingJoinResponse {
        joinPairingCalledWith = (did, pairingId, request)
        return try joinPairingResult.get()
    }

    func approvePairing(did: String, pairingId: String, request: PairingApproveRequest) async throws {
        approvePairingCalledWith = (did, pairingId, request)
        try approvePairingResult.get()
    }

    func getPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse {
        getPairingStatusCalledWith = (did, pairingId)
        return try getPairingStatusResult.get()
    }
}

/// Stub SsdidClient replacement for updateDidDocument calls.
private final class StubSsdidClientProvider: DeviceManagerSsdidClientProvider, @unchecked Sendable {
    var updateDidDocumentResult: Result<Void, Error> = .success(())
    var updateDidDocumentCalledWith: String?

    func updateDidDocument(keyId: String) async throws {
        updateDidDocumentCalledWith = keyId
        try updateDidDocumentResult.get()
    }
}

// MARK: - Tests

final class DeviceManagerTests: XCTestCase {

    private var vault: StubVault!
    private var registryClient: StubRegistryClient!
    private var ssdidClientProvider: StubSsdidClientProvider!
    private var manager: DeviceManager!

    private let testIdentity = Identity(
        name: "Test",
        did: "did:ssdid:abc123",
        keyId: "key-1",
        algorithm: .ED25519,
        publicKeyMultibase: "uPublicKey",
        createdAt: "2024-01-01T00:00:00Z"
    )

    override func setUp() {
        super.setUp()
        vault = StubVault()
        registryClient = StubRegistryClient()
        ssdidClientProvider = StubSsdidClientProvider()
        manager = DeviceManager(
            vault: vault,
            registryClient: registryClient,
            ssdidClientProvider: ssdidClientProvider,
            deviceName: "Test Device",
            platform: "ios"
        )
    }

    // MARK: - 1. initiatePairing returns PairingData with correct DID

    func testInitiatePairingReturnsPairingDataWithCorrectDid() async throws {
        let data = try await manager.initiatePairing(identity: testIdentity)

        XCTAssertEqual(data.did, "did:ssdid:abc123")
        XCTAssertEqual(data.pairingId, "pairing-1")
        XCTAssertFalse(data.challenge.isEmpty)
    }

    // MARK: - 2. initiatePairing throws on network error

    func testInitiatePairingThrowsOnNetworkError() async {
        registryClient.initPairingResult = .failure(NSError(domain: "test", code: -1, userInfo: [NSLocalizedDescriptionKey: "Network error"]))

        do {
            _ = try await manager.initiatePairing(identity: testIdentity)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Network error"))
        }
    }

    // MARK: - 3. joinPairing signs challenge and returns status

    func testJoinPairingSignsChallengeAndReturnsStatus() async throws {
        let status = try await manager.joinPairing(
            did: "did:ssdid:abc123",
            pairingId: "pairing-1",
            challenge: "test-challenge",
            identity: testIdentity,
            deviceName: "Test Phone"
        )

        XCTAssertEqual(status, "joined")

        // Verify sign was called with the correct payload
        XCTAssertNotNil(vault.signCalledWith)
        XCTAssertEqual(vault.signCalledWith?.keyId, "key-1")
        XCTAssertEqual(vault.signCalledWith?.data, "join:test-challenge".data(using: .utf8))

        // Verify registry was called with correct parameters
        XCTAssertNotNil(registryClient.joinPairingCalledWith)
        XCTAssertEqual(registryClient.joinPairingCalledWith?.did, "did:ssdid:abc123")
        XCTAssertEqual(registryClient.joinPairingCalledWith?.pairingId, "pairing-1")
        XCTAssertEqual(registryClient.joinPairingCalledWith?.request.deviceName, "Test Phone")
        XCTAssertEqual(registryClient.joinPairingCalledWith?.request.platform, "ios")
        XCTAssertEqual(registryClient.joinPairingCalledWith?.request.publicKey, "uPublicKey")
    }

    // MARK: - 4. joinPairing throws when signing fails

    func testJoinPairingThrowsWhenSigningFails() async {
        vault.signResult = .failure(NSError(domain: "test", code: -1, userInfo: [NSLocalizedDescriptionKey: "Key not found"]))

        do {
            _ = try await manager.joinPairing(
                did: "did:ssdid:abc123",
                pairingId: "pairing-1",
                challenge: "test-challenge",
                identity: testIdentity,
                deviceName: "Test Phone"
            )
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Key not found"))
        }
    }

    // MARK: - 5. approvePairing signs approval and updates DID document

    func testApprovePairingSignsApprovalAndUpdatesDidDocument() async throws {
        try await manager.approvePairing(identity: testIdentity, pairingId: "pairing-1")

        // Verify sign was called with approval payload
        XCTAssertNotNil(vault.signCalledWith)
        XCTAssertEqual(vault.signCalledWith?.keyId, "key-1")
        XCTAssertEqual(vault.signCalledWith?.data, "approve:pairing-1".data(using: .utf8))

        // Verify registry was called
        XCTAssertNotNil(registryClient.approvePairingCalledWith)
        XCTAssertEqual(registryClient.approvePairingCalledWith?.did, "did:ssdid:abc123")
        XCTAssertEqual(registryClient.approvePairingCalledWith?.pairingId, "pairing-1")
        XCTAssertEqual(registryClient.approvePairingCalledWith?.request.did, "did:ssdid:abc123")
        XCTAssertEqual(registryClient.approvePairingCalledWith?.request.keyId, "key-1")

        // Verify DID document was updated
        XCTAssertEqual(ssdidClientProvider.updateDidDocumentCalledWith, "key-1")
    }

    // MARK: - 6. approvePairing throws when signing fails

    func testApprovePairingThrowsWhenSigningFails() async {
        vault.signResult = .failure(NSError(domain: "test", code: -1, userInfo: [NSLocalizedDescriptionKey: "Signing failed"]))

        do {
            try await manager.approvePairing(identity: testIdentity, pairingId: "pairing-1")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Signing failed"))
        }
    }

    // MARK: - 7. approvePairing throws when updateDidDocument fails

    func testApprovePairingThrowsWhenUpdateDidDocumentFails() async {
        ssdidClientProvider.updateDidDocumentResult = .failure(NSError(domain: "test", code: -1, userInfo: [NSLocalizedDescriptionKey: "Registry unreachable"]))

        do {
            try await manager.approvePairing(identity: testIdentity, pairingId: "pairing-1")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Registry unreachable"))
        }
    }

    // MARK: - 8. revokeDevice rejects primary key

    func testRevokeDeviceRejectsPrimaryKey() async {
        do {
            try await manager.revokeDevice(identity: testIdentity, targetKeyId: "key-1")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Cannot revoke primary device key"))
        }
    }

    // MARK: - 9. revokeDevice succeeds for non-primary key

    func testRevokeDeviceSucceedsForNonPrimaryKey() async throws {
        try await manager.revokeDevice(identity: testIdentity, targetKeyId: "key-2")

        // Verify DID document was updated using the primary key
        XCTAssertEqual(ssdidClientProvider.updateDidDocumentCalledWith, "key-1")
    }

    // MARK: - 10. listDevices returns at least primary device

    func testListDevicesReturnsAtLeastPrimaryDevice() async throws {
        let didDoc = DidDocument(
            context: ["https://www.w3.org/ns/did/v1"],
            id: "did:ssdid:abc123",
            controller: "did:ssdid:abc123",
            verificationMethod: [
                VerificationMethod(
                    id: "key-1",
                    type: "Ed25519VerificationKey2020",
                    controller: "did:ssdid:abc123",
                    publicKeyMultibase: "uPublicKey"
                )
            ],
            authentication: ["key-1"],
            assertionMethod: ["key-1"]
        )
        vault.buildDidDocumentResult = .success(didDoc)

        let devices = try await manager.listDevices(identity: testIdentity)

        XCTAssertFalse(devices.isEmpty)
        XCTAssertTrue(devices.contains { $0.isPrimary })
        XCTAssertEqual(devices.first { $0.isPrimary }?.keyId, "key-1")
    }

    // MARK: - 11. listDevices includes secondary devices

    func testListDevicesIncludesSecondaryDevices() async throws {
        let didDoc = DidDocument(
            context: ["https://www.w3.org/ns/did/v1"],
            id: "did:ssdid:abc123",
            controller: "did:ssdid:abc123",
            verificationMethod: [
                VerificationMethod(
                    id: "key-1",
                    type: "Ed25519VerificationKey2020",
                    controller: "did:ssdid:abc123",
                    publicKeyMultibase: "uPublicKey"
                ),
                VerificationMethod(
                    id: "key-2",
                    type: "Ed25519VerificationKey2020",
                    controller: "did:ssdid:abc123",
                    publicKeyMultibase: "uOtherKey"
                )
            ],
            authentication: ["key-1", "key-2"],
            assertionMethod: ["key-1", "key-2"]
        )
        vault.buildDidDocumentResult = .success(didDoc)

        let devices = try await manager.listDevices(identity: testIdentity)

        XCTAssertEqual(devices.count, 2)
        XCTAssertEqual(devices.filter { $0.isPrimary }.count, 1)
        XCTAssertEqual(devices.filter { !$0.isPrimary }.count, 1)
        XCTAssertEqual(devices.first { !$0.isPrimary }?.keyId, "key-2")
    }

    // MARK: - 12. listDevices falls back when verificationMethod is empty

    func testListDevicesFallsBackWhenVerificationMethodIsEmpty() async throws {
        let didDoc = DidDocument(
            context: ["https://www.w3.org/ns/did/v1"],
            id: "did:ssdid:abc123",
            controller: "did:ssdid:abc123",
            verificationMethod: [],
            authentication: [],
            assertionMethod: []
        )
        vault.buildDidDocumentResult = .success(didDoc)

        let devices = try await manager.listDevices(identity: testIdentity)

        XCTAssertEqual(devices.count, 1)
        XCTAssertTrue(devices.first!.isPrimary)
        XCTAssertEqual(devices.first!.keyId, "key-1")
    }

    // MARK: - 13. checkPairingStatus returns status from registry

    func testCheckPairingStatusReturnsStatusFromRegistry() async throws {
        let response = try await manager.checkPairingStatus(did: "did:ssdid:abc123", pairingId: "pairing-1")

        XCTAssertEqual(response.status, "pending")
    }
}
