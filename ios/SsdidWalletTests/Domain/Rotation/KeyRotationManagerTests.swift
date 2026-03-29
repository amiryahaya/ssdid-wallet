@testable import SsdidCore
import XCTest
@testable import SsdidWallet

// MARK: - Test Doubles

/// Stub Vault for KeyRotationManager tests.
/// Records calls and returns pre-configured responses.
private final class StubVault: Vault, @unchecked Sendable {
    var identities: [String: Identity] = [:]
    var signResult: Data = Data(repeating: 0xAB, count: 64)

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        fatalError("Not used in KeyRotationManager tests")
    }

    func getIdentity(keyId: String) async -> Identity? {
        identities[keyId]
    }

    func listIdentities() async -> [Identity] {
        Array(identities.values)
    }

    func deleteIdentity(keyId: String) async throws {
        identities.removeValue(forKey: keyId)
    }

    func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {}

    func sign(keyId: String, data: Data) async throws -> Data {
        signResult
    }

    func buildDidDocument(keyId: String) async throws -> DidDocument {
        fatalError("Not used in KeyRotationManager tests")
    }

    func createProof(keyId: String, document: [String: Any], proofPurpose: String, challenge: String?, domain: String?) async throws -> Proof {
        fatalError("Not used in KeyRotationManager tests")
    }

    func storeCredential(_ credential: VerifiableCredential) async throws {}
    func listCredentials() async -> [VerifiableCredential] { [] }
    func getCredentialForDid(_ did: String) async -> VerifiableCredential? { nil }
    func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] { [] }
    func deleteCredential(credentialId: String) async throws {}
}

/// Stub CryptoProvider that returns deterministic key pairs.
private final class StubCryptoProvider: CryptoProvider, @unchecked Sendable {
    var keyPairToReturn: KeyPairResult = KeyPairResult(
        publicKey: Data(repeating: 0x01, count: 32),
        privateKey: Data(repeating: 0x02, count: 32)
    )

    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool { true }

    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        keyPairToReturn
    }

    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        Data(repeating: 0xAB, count: 64)
    }

    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        true
    }
}

/// In-memory VaultStorage for testing.
private final class InMemoryVaultStorage: VaultStorage, @unchecked Sendable {
    private var identities: [Identity] = []
    private var encryptedKeys: [String: Data] = [:]
    private var recoveryPublicKeys: [String: Data] = [:]
    private var credentials: [VerifiableCredential] = []
    private var sdJwtVcs: [StoredSdJwtVc] = []
    private var preRotatedKeys: [String: PreRotatedKeyData] = [:]
    private var onboardingDone = false

    func saveIdentity(_ identity: Identity, encryptedPrivateKey: Data) async throws {
        if let idx = identities.firstIndex(where: { $0.keyId == identity.keyId }) {
            identities[idx] = identity
        } else {
            identities.append(identity)
        }
        encryptedKeys[identity.keyId] = encryptedPrivateKey
    }

    func getIdentity(keyId: String) async -> Identity? {
        identities.first { $0.keyId == keyId }
    }

    func listIdentities() async -> [Identity] { identities }

    func deleteIdentity(keyId: String) async throws {
        identities.removeAll { $0.keyId == keyId }
        encryptedKeys.removeValue(forKey: keyId)
    }

    func getEncryptedPrivateKey(keyId: String) async -> Data? {
        encryptedKeys[keyId]
    }

    func saveCredential(_ credential: VerifiableCredential) async throws {
        credentials.append(credential)
    }

    func listCredentials() async -> [VerifiableCredential] { credentials }

    func deleteCredential(credentialId: String) async throws {
        credentials.removeAll { $0.id == credentialId }
    }

    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws {
        sdJwtVcs.append(sdJwtVc)
    }

    func listSdJwtVcs() async -> [StoredSdJwtVc] { sdJwtVcs }

    func deleteSdJwtVc(id: String) async throws {
        sdJwtVcs.removeAll { $0.id == id }
    }

    func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws {
        recoveryPublicKeys[keyId] = encryptedPublicKey
    }

    func getRecoveryPublicKey(keyId: String) async -> Data? {
        recoveryPublicKeys[keyId]
    }

    func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws {
        preRotatedKeys[keyId] = PreRotatedKeyData(encryptedPrivateKey: encryptedPrivateKey, publicKey: publicKey)
    }

    func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData? {
        preRotatedKeys[keyId]
    }

    func deletePreRotatedKey(keyId: String) async throws {
        preRotatedKeys.removeValue(forKey: keyId)
    }

    // Rotation history
    private var rotationHistory: [String: [RotationEntry]] = [:]

    func addRotationEntry(did: String, entry: RotationEntry) async throws {
        rotationHistory[did, default: []].append(entry)
    }

    func getRotationHistory(did: String) async -> [RotationEntry] {
        rotationHistory[did] ?? []
    }

    func isOnboardingCompleted() async -> Bool { onboardingDone }
    func setOnboardingCompleted() async throws { onboardingDone = true }
}

/// Stub SsdidClient that records updateDidDocument calls and can be
/// configured to succeed or fail.
private final class StubSsdidClient: DidDocumentUpdater, @unchecked Sendable {
    var updateDidDocumentCalled = false
    var updateDidDocumentShouldFail = false

    func updateDidDocument(keyId: String) async throws {
        updateDidDocumentCalled = true
        if updateDidDocumentShouldFail {
            throw NSError(domain: "test", code: 500, userInfo: [NSLocalizedDescriptionKey: "Registry update failed"])
        }
    }
}

/// Stub KeychainManager for testing (no real Keychain access).
private final class StubKeychainManager: KeychainManagerProtocol, @unchecked Sendable {
    private var keys: Set<String> = []
    private var encrypted: [String: Data] = [:]

    func generateWrappingKey(alias: String) throws {
        keys.insert(alias)
    }

    func encrypt(alias: String, data: Data) throws -> Data {
        // Return data prefixed with a marker so we can verify it was "encrypted"
        let marker = Data("ENC:".utf8)
        return marker + data
    }

    func decrypt(alias: String, data: Data) throws -> Data {
        let marker = Data("ENC:".utf8)
        if data.starts(with: marker) {
            return data.dropFirst(marker.count)
        }
        return data
    }

    func deleteKey(alias: String) {
        keys.remove(alias)
    }

    func hasKey(alias: String) -> Bool {
        keys.contains(alias)
    }

    func hasEphemeralKey(alias: String) -> Bool { false }
    func hasLegacyKey(alias: String) -> Bool { keys.contains(alias) }
    func decryptLegacy(alias: String, data: Data) throws -> Data { try decrypt(alias: alias, data: data) }
    func deleteLegacyKey(alias: String) { keys.remove(alias) }
}

// MARK: - KeyRotationManagerTests

/// Tests for KeyRotationManager — the KERI-inspired key rotation workflow.
///
/// These tests are written TDD-first: they will NOT compile until
/// `KeyRotationManager`, `RotationStatus`, and `RotationEntry` are implemented.
final class KeyRotationManagerTests: XCTestCase {

    private var vault: StubVault!
    private var storage: InMemoryVaultStorage!
    private var cryptoProvider: StubCryptoProvider!
    private var keychainManager: StubKeychainManager!
    private var ssdidClient: StubSsdidClient!
    private var sut: KeyRotationManager!

    override func setUp() {
        super.setUp()
        vault = StubVault()
        storage = InMemoryVaultStorage()
        cryptoProvider = StubCryptoProvider()
        keychainManager = StubKeychainManager()
        ssdidClient = StubSsdidClient()

        sut = KeyRotationManager(
            vault: vault,
            storage: storage,
            cryptoProvider: cryptoProvider,
            keychainManager: keychainManager,
            ssdidClient: ssdidClient
        )
    }

    override func tearDown() {
        vault = nil
        storage = nil
        cryptoProvider = nil
        keychainManager = nil
        ssdidClient = nil
        sut = nil
        super.tearDown()
    }

    // MARK: - Helpers

    private func makeIdentity(
        keyId: String = "did:ssdid:testRotation123#key1",
        did: String = "did:ssdid:testRotation123",
        algorithm: Algorithm = .ED25519
    ) -> Identity {
        Identity(
            name: "Rotation Test",
            did: did,
            keyId: keyId,
            algorithm: algorithm,
            publicKeyMultibase: "uAQEB",
            createdAt: ISO8601DateFormatter().string(from: Date())
        )
    }

    private func seedIdentity(_ identity: Identity) async throws {
        try await storage.saveIdentity(identity, encryptedPrivateKey: Data(repeating: 0x02, count: 32))
        vault.identities[identity.keyId] = identity
    }

    // MARK: - Test 1: prepareRotation generates pre-commitment hash

    func testPrepareRotationGeneratesPreCommitmentHash() async throws {
        let identity = makeIdentity()
        try await seedIdentity(identity)

        let nextKeyHash = try await sut.prepareRotation(identity: identity)

        // The pre-commitment hash should be multibase-encoded (starts with "u" for base64url)
        XCTAssertTrue(nextKeyHash.hasPrefix("u"), "Pre-commitment hash should be multibase base64url-encoded (u prefix)")
        XCTAssertTrue(nextKeyHash.count > 1, "Pre-commitment hash should be non-empty")

        // A pre-rotated key should now be stored in vault storage
        let preRotatedKey = await storage.getPreRotatedKey(keyId: identity.keyId)
        XCTAssertNotNil(preRotatedKey, "Pre-rotated key material should be stored in vault after prepareRotation")
    }

    // MARK: - Test 2: executeRotation fails without pre-commitment

    func testExecuteRotationFailsWithoutPreCommitment() async throws {
        let identity = makeIdentity()
        try await seedIdentity(identity)

        // Do NOT call prepareRotation — there should be no pre-commitment
        do {
            _ = try await sut.executeRotation(identity: identity)
            XCTFail("executeRotation should throw when no pre-commitment exists")
        } catch {
            // Expected: rotation requires a prior prepareRotation call
            XCTAssertTrue(
                "\(error)".lowercased().contains("pre-commitment")
                || "\(error)".lowercased().contains("pre-rotated")
                || "\(error)".lowercased().contains("not prepared")
                || error is RotationError,
                "Error should indicate missing pre-commitment, got: \(error)"
            )
        }
    }

    // MARK: - Test 3: executeRotation promotes pre-rotated key

    func testExecuteRotationPromotesPreRotatedKey() async throws {
        let identity = makeIdentity()
        try await seedIdentity(identity)

        // Step 1: prepare
        _ = try await sut.prepareRotation(identity: identity)

        // Re-fetch identity from storage so preRotatedKeyId is set
        guard let preparedIdentity = await storage.getIdentity(keyId: identity.keyId) else {
            XCTFail("Identity should exist in storage after prepareRotation")
            return
        }

        // Step 2: execute
        let newIdentity = try await sut.executeRotation(identity: preparedIdentity)

        // The new identity should keep the same DID but have a new keyId
        XCTAssertEqual(newIdentity.did, identity.did, "Rotated identity should preserve the DID")
        XCTAssertNotEqual(newIdentity.keyId, identity.keyId, "Rotated identity should have a new keyId")

        // The registry should have been updated
        XCTAssertTrue(ssdidClient.updateDidDocumentCalled, "executeRotation should publish the updated DID document to the registry")

        // The old pre-rotated key should be cleaned up
        let preRotatedKey = await storage.getPreRotatedKey(keyId: identity.keyId)
        XCTAssertNil(preRotatedKey, "Pre-rotated key material should be deleted after successful rotation")
    }

    // MARK: - Test 4: executeRotation preserves old identity when registry update fails

    func testExecuteRotationPreservesOldIdentityOnRegistryFailure() async throws {
        let identity = makeIdentity()
        try await seedIdentity(identity)

        // Prepare rotation
        _ = try await sut.prepareRotation(identity: identity)

        // Re-fetch identity from storage so preRotatedKeyId is set
        guard let preparedIdentity = await storage.getIdentity(keyId: identity.keyId) else {
            XCTFail("Identity should exist in storage after prepareRotation")
            return
        }

        // Configure the stub to fail on registry update
        ssdidClient.updateDidDocumentShouldFail = true

        do {
            _ = try await sut.executeRotation(identity: preparedIdentity)
            XCTFail("executeRotation should throw when registry update fails")
        } catch {
            // Expected: registry update failed
        }

        // The old identity should still exist in the vault
        let oldIdentity = await vault.getIdentity(keyId: identity.keyId)
        XCTAssertNotNil(oldIdentity, "Old identity should be preserved when registry update fails")
        XCTAssertEqual(oldIdentity?.keyId, identity.keyId)
    }

    // MARK: - Test 5: getRotationStatus returns correct state

    func testGetRotationStatusReturnsCorrectState() async throws {
        let identity = makeIdentity()
        try await seedIdentity(identity)

        // Before preparation: no pre-commitment
        let statusBefore = await sut.getRotationStatus(identity: identity)
        XCTAssertFalse(statusBefore.hasPreCommitment, "Should have no pre-commitment before prepareRotation")
        XCTAssertNil(statusBefore.nextKeyHash, "nextKeyHash should be nil before prepareRotation")

        // After preparation: has pre-commitment
        let nextKeyHash = try await sut.prepareRotation(identity: identity)
        let statusAfter = await sut.getRotationStatus(identity: identity)
        XCTAssertTrue(statusAfter.hasPreCommitment, "Should have pre-commitment after prepareRotation")
        XCTAssertEqual(statusAfter.nextKeyHash, nextKeyHash, "nextKeyHash should match the value returned by prepareRotation")
    }
}
