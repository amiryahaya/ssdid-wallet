import XCTest
@testable import SsdidCore

// MARK: - Test Doubles

/// In-memory VaultStorage for testing (avoids file system and UserDefaults side effects).
private final class InMemoryVaultStorage: VaultStorage, @unchecked Sendable {
    private var identities: [Identity] = []
    private var encryptedKeys: [String: Data] = [:]
    private var recoveryPublicKeys: [String: Data] = [:]
    private var credentials: [VerifiableCredential] = []
    private var sdJwtVcs: [StoredSdJwtVc] = []
    private var preRotatedKeys: [String: PreRotatedKeyData] = [:]
    private var rotationHistoryMap: [String: [RotationEntry]] = [:]
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

    func addRotationEntry(did: String, entry: RotationEntry) async throws {
        rotationHistoryMap[did, default: []].append(entry)
    }

    func getRotationHistory(did: String) async -> [RotationEntry] {
        rotationHistoryMap[did] ?? []
    }

    func isOnboardingCompleted() async -> Bool { onboardingDone }
    func setOnboardingCompleted() async throws { onboardingDone = true }
}

/// Stub Vault implementation that delegates identity listing to its storage.
private final class StubVault: Vault, @unchecked Sendable {
    let storage: InMemoryVaultStorage

    init(storage: InMemoryVaultStorage) {
        self.storage = storage
    }

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        fatalError("Not used in RecoveryManager tests")
    }

    func getIdentity(keyId: String) async -> Identity? {
        await storage.getIdentity(keyId: keyId)
    }

    func listIdentities() async -> [Identity] {
        await storage.listIdentities()
    }

    func deleteIdentity(keyId: String) async throws {
        try await storage.deleteIdentity(keyId: keyId)
    }

    func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {}

    func sign(keyId: String, data: Data) async throws -> Data { Data() }

    func buildDidDocument(keyId: String) async throws -> DidDocument {
        fatalError("Not used in RecoveryManager tests")
    }

    func createProof(keyId: String, document: [String: Any], proofPurpose: String, challenge: String?, domain: String?) async throws -> Proof {
        fatalError("Not used in RecoveryManager tests")
    }

    func storeCredential(_ credential: VerifiableCredential) async throws {}
    func listCredentials() async -> [VerifiableCredential] { [] }
    func getCredentialForDid(_ did: String) async -> VerifiableCredential? { nil }
    func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] { [] }
    func deleteCredential(credentialId: String) async throws {}
}

// MARK: - RecoveryManagerTests

final class RecoveryManagerTests: XCTestCase {

    private var storage: InMemoryVaultStorage!
    private var vault: StubVault!
    private var classicalProvider: ClassicalProvider!
    private var keychainManager: KeychainManager!
    private var sut: RecoveryManager!

    /// A helper identity seeded into storage before each test.
    private var testIdentity: Identity!

    override func setUp() {
        super.setUp()
        storage = InMemoryVaultStorage()
        vault = StubVault(storage: storage)
        classicalProvider = ClassicalProvider()
        // Use a unique service prefix per test run to avoid keychain collisions.
        keychainManager = KeychainManager(servicePrefix: "my.ssdid.wallet.test.\(UUID().uuidString.prefix(8))", requireBiometric: false)

        sut = RecoveryManager(
            vault: vault,
            storage: storage,
            classicalProvider: classicalProvider,
            pqcProvider: classicalProvider,   // PQC not needed for Ed25519 tests
            keychainManager: keychainManager
        )
    }

    override func tearDown() {
        storage = nil
        vault = nil
        classicalProvider = nil
        keychainManager = nil
        sut = nil
        testIdentity = nil
        super.tearDown()
    }

    // MARK: - Helpers

    /// Creates and stores a test identity, returning it.
    private func seedIdentity(algorithm: Algorithm = .ED25519) async throws -> Identity {
        let kp = try classicalProvider.generateKeyPair(algorithm: algorithm)
        let keyId = "did:ssdid:test123#key1"
        let identity = Identity(
            name: "Test",
            did: "did:ssdid:test123",
            keyId: keyId,
            algorithm: algorithm,
            publicKeyMultibase: "zTest",
            createdAt: ISO8601DateFormatter().string(from: Date())
        )
        // Store with a fake encrypted private key (our InMemoryVaultStorage does not decrypt).
        try await storage.saveIdentity(identity, encryptedPrivateKey: kp.privateKey)
        return identity
    }

    // MARK: - Tests

    func testGenerateRecoveryKeyReturnsNonEmptyData() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()

        let recoveryPrivateKey = try await sut.generateRecoveryKey(identity: identity)

        XCTAssertFalse(recoveryPrivateKey.isEmpty, "Recovery private key must not be empty")
    }

    func testHasRecoveryKeyReturnsTrueAfterGeneration() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()

        _ = try await sut.generateRecoveryKey(identity: identity)

        let hasKey = await sut.hasRecoveryKey(keyId: identity.keyId)
        XCTAssertTrue(hasKey, "hasRecoveryKey should return true after generating a recovery key")
    }

    func testHasRecoveryKeyReturnsFalseForUnknownKeyId() async throws {
        let hasKey = await sut.hasRecoveryKey(keyId: "did:ssdid:nonexistent#key999")
        XCTAssertFalse(hasKey, "hasRecoveryKey should return false for unknown key ID")
    }

    func testGenerateRecoveryKeyStoresRecoveryPublicKey() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()

        _ = try await sut.generateRecoveryKey(identity: identity)

        // The recovery public key should be stored under "<keyId>-recovery"
        let recoveryKeyId = "\(identity.keyId)-recovery"
        let stored = await storage.getRecoveryPublicKey(keyId: recoveryKeyId)
        XCTAssertNotNil(stored, "Recovery public key should be stored in vault storage")
        XCTAssertFalse(stored!.isEmpty, "Stored recovery public key must not be empty")
    }

    func testGenerateRecoveryKeyUpdatesIdentityMetadata() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()

        _ = try await sut.generateRecoveryKey(identity: identity)

        let updated = await storage.getIdentity(keyId: identity.keyId)
        XCTAssertNotNil(updated)
        XCTAssertTrue(updated!.hasRecoveryKey, "Identity should be marked as having a recovery key")
        XCTAssertEqual(updated!.recoveryKeyId, "\(identity.keyId)-recovery")
    }

    func testRestoreWithRecoveryKeyCreatesNewIdentityWithSameDid() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()
        let recoveryPrivateKey = try await sut.generateRecoveryKey(identity: identity)
        let recoveryKeyBase64 = recoveryPrivateKey.base64EncodedString()

        let restored = try await sut.restoreWithRecoveryKey(
            did: identity.did,
            recoveryPrivateKeyBase64: recoveryKeyBase64,
            name: "Restored",
            algorithm: .ED25519
        )

        XCTAssertEqual(restored.did, identity.did, "Restored identity should have the same DID")
        XCTAssertEqual(restored.name, "Restored")
        XCTAssertNotEqual(restored.keyId, identity.keyId, "Restored identity should have a new key ID")
        XCTAssertFalse(restored.publicKeyMultibase.isEmpty)
    }

    func testRestoreWithRecoveryKeyFailsWithWrongKey() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()
        _ = try await sut.generateRecoveryKey(identity: identity)

        // Generate a different key pair to get an unrelated private key
        let wrongKp = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let wrongKeyBase64 = wrongKp.privateKey.base64EncodedString()

        do {
            _ = try await sut.restoreWithRecoveryKey(
                did: identity.did,
                recoveryPrivateKeyBase64: wrongKeyBase64,
                name: "ShouldFail",
                algorithm: .ED25519
            )
            XCTFail("Restoration with wrong recovery key should throw")
        } catch {
            // Expected -- verification should fail
            XCTAssertTrue(
                error is RecoveryError || error is KeychainError || error is CryptoError,
                "Expected a RecoveryError, KeychainError, or CryptoError, got \(type(of: error))"
            )
        }
    }

    func testRestoreWithInvalidBase64Throws() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        do {
            _ = try await sut.restoreWithRecoveryKey(
                did: "did:ssdid:test",
                recoveryPrivateKeyBase64: "%%%not-base64%%%",
                name: "Bad",
                algorithm: .ED25519
            )
            XCTFail("Should throw for invalid Base64")
        } catch let error as RecoveryError {
            if case .invalidRecoveryKey = error {
                // Expected
            } else {
                XCTFail("Expected RecoveryError.invalidRecoveryKey, got \(error)")
            }
        }
    }

    func testGenerateRecoveryKeyProducesUniqueKeysPerCall() async throws {
        try XCTSkipUnless(KeychainAvailability.isAvailable, "Keychain not available (CI simulator)")
        let identity = try await seedIdentity()

        let key1 = try await sut.generateRecoveryKey(identity: identity)
        let key2 = try await sut.generateRecoveryKey(identity: identity)

        XCTAssertNotEqual(key1, key2, "Each call to generateRecoveryKey should produce a unique key pair")
    }
}
