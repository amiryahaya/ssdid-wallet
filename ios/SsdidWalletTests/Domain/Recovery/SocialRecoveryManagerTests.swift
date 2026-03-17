import XCTest
@testable import SsdidWallet

// MARK: - Test Doubles for SocialRecoveryManager

/// In-memory VaultStorage (duplicated here because Swift test targets do not share
/// private types across files without a shared test-helpers module).
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

    func getEncryptedPrivateKey(keyId: String) async -> Data? { encryptedKeys[keyId] }

    func saveCredential(_ credential: VerifiableCredential) async throws {
        credentials.append(credential)
    }
    func listCredentials() async -> [VerifiableCredential] { credentials }
    func deleteCredential(credentialId: String) async throws {
        credentials.removeAll { $0.id == credentialId }
    }

    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws { sdJwtVcs.append(sdJwtVc) }
    func listSdJwtVcs() async -> [StoredSdJwtVc] { sdJwtVcs }
    func deleteSdJwtVc(id: String) async throws { sdJwtVcs.removeAll { $0.id == id } }

    func saveRecoveryPublicKey(keyId: String, encryptedPublicKey: Data) async throws {
        recoveryPublicKeys[keyId] = encryptedPublicKey
    }
    func getRecoveryPublicKey(keyId: String) async -> Data? { recoveryPublicKeys[keyId] }

    func savePreRotatedKey(keyId: String, encryptedPrivateKey: Data, publicKey: Data) async throws {
        preRotatedKeys[keyId] = PreRotatedKeyData(encryptedPrivateKey: encryptedPrivateKey, publicKey: publicKey)
    }
    func getPreRotatedKey(keyId: String) async -> PreRotatedKeyData? { preRotatedKeys[keyId] }
    func deletePreRotatedKey(keyId: String) async throws { preRotatedKeys.removeValue(forKey: keyId) }

    func isOnboardingCompleted() async -> Bool { onboardingDone }
    func setOnboardingCompleted() async throws { onboardingDone = true }
}

/// Stub Vault for SocialRecoveryManager tests.
private final class StubVault: Vault, @unchecked Sendable {
    let storage: InMemoryVaultStorage

    init(storage: InMemoryVaultStorage) { self.storage = storage }

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        fatalError("Not used")
    }
    func getIdentity(keyId: String) async -> Identity? { await storage.getIdentity(keyId: keyId) }
    func listIdentities() async -> [Identity] { await storage.listIdentities() }
    func deleteIdentity(keyId: String) async throws { try await storage.deleteIdentity(keyId: keyId) }
    func updateIdentityProfile(keyId: String, profileName: String?, email: String?, emailVerified: Bool?) async throws {}
    func sign(keyId: String, data: Data) async throws -> Data { Data() }
    func buildDidDocument(keyId: String) async throws -> DidDocument { fatalError("Not used") }
    func createProof(keyId: String, document: [String: Any], proofPurpose: String, challenge: String?, domain: String?) async throws -> Proof { fatalError("Not used") }
    func storeCredential(_ credential: VerifiableCredential) async throws {}
    func listCredentials() async -> [VerifiableCredential] { [] }
    func getCredentialForDid(_ did: String) async -> VerifiableCredential? { nil }
    func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] { [] }
    func deleteCredential(credentialId: String) async throws {}
}

// MARK: - SocialRecoveryManagerTests

final class SocialRecoveryManagerTests: XCTestCase {

    private var storage: InMemoryVaultStorage!
    private var vault: StubVault!
    private var classicalProvider: ClassicalProvider!
    private var keychainManager: KeychainManager!
    private var recoveryManager: RecoveryManager!
    private var defaults: UserDefaults!
    private var sut: SocialRecoveryManager!

    override func setUp() {
        super.setUp()
        storage = InMemoryVaultStorage()
        vault = StubVault(storage: storage)
        classicalProvider = ClassicalProvider()
        keychainManager = KeychainManager(
            servicePrefix: "my.ssdid.wallet.social.test.\(UUID().uuidString.prefix(8))",
            requireBiometric: false
        )
        recoveryManager = RecoveryManager(
            vault: vault,
            storage: storage,
            classicalProvider: classicalProvider,
            pqcProvider: classicalProvider,
            keychainManager: keychainManager
        )
        // Use a unique suite name so tests do not pollute each other or real UserDefaults.
        let suiteName = "SocialRecoveryTest.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!

        sut = SocialRecoveryManager(
            recoveryManager: recoveryManager,
            vault: vault,
            defaults: defaults
        )
    }

    override func tearDown() {
        if let suiteName = defaults.volatileDomainNames.first {
            UserDefaults.standard.removePersistentDomain(forName: suiteName)
        }
        // Clean up the test suite from UserDefaults
        defaults.removePersistentDomain(forName: defaults.description)
        storage = nil
        vault = nil
        classicalProvider = nil
        keychainManager = nil
        recoveryManager = nil
        defaults = nil
        sut = nil
        super.tearDown()
    }

    // MARK: - Helpers

    private func seedIdentity() async throws -> Identity {
        let kp = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let keyId = "did:ssdid:social-test#key1"
        let identity = Identity(
            name: "SocialTest",
            did: "did:ssdid:social-test",
            keyId: keyId,
            algorithm: .ED25519,
            publicKeyMultibase: "zSocialTest",
            createdAt: ISO8601DateFormatter().string(from: Date())
        )
        try await storage.saveIdentity(identity, encryptedPrivateKey: kp.privateKey)
        return identity
    }

    private func guardianNames(count: Int) -> [(name: String, did: String)] {
        (1...count).map { i in
            (name: "Guardian \(i)", did: "did:ssdid:guardian\(i)")
        }
    }

    // MARK: - Tests

    func testSetupSocialRecoveryGeneratesSharesForEachGuardian() async throws {
        let identity = try await seedIdentity()
        let guardians = guardianNames(count: 3)

        let shares = try await sut.setupSocialRecovery(
            identity: identity,
            guardianNames: guardians,
            threshold: 2
        )

        XCTAssertEqual(shares.count, 3, "Should produce one share per guardian")
        for (_, shareValue) in shares {
            XCTAssertFalse(shareValue.isEmpty, "Share data must not be empty")
            // Verify it is valid Base64URL
            XCTAssertNotNil(Data(base64URLEncoded: shareValue), "Share should be valid Base64URL")
        }
    }

    func testRecoverWithSharesReconstructsIdentity() async throws {
        let identity = try await seedIdentity()
        let guardians = guardianNames(count: 3)

        let shares = try await sut.setupSocialRecovery(
            identity: identity,
            guardianNames: guardians,
            threshold: 2
        )

        // Get config to find guardian share indices
        let config = sut.getConfig(did: identity.did)!

        // Collect threshold (2) shares with their indices
        var collectedShares: [Int: String] = [:]
        for guardian in config.guardians.prefix(2) {
            collectedShares[guardian.shareIndex] = shares[guardian.id]!
        }

        let restored = try await sut.recoverWithShares(
            did: identity.did,
            collectedShares: collectedShares,
            name: "Recovered",
            algorithm: .ED25519
        )

        XCTAssertEqual(restored.did, identity.did, "Recovered identity should have the same DID")
        XCTAssertEqual(restored.name, "Recovered")
        XCTAssertFalse(restored.publicKeyMultibase.isEmpty)
    }

    func testSetupSocialRecoveryFailsWithThresholdLessThanTwo() async throws {
        let identity = try await seedIdentity()

        do {
            _ = try await sut.setupSocialRecovery(
                identity: identity,
                guardianNames: guardianNames(count: 3),
                threshold: 1
            )
            XCTFail("Should throw for threshold < 2")
        } catch let error as SocialRecoveryError {
            if case .thresholdTooLow = error {
                // Expected
            } else {
                XCTFail("Expected thresholdTooLow, got \(error)")
            }
        }
    }

    func testSetupSocialRecoveryFailsWhenGuardiansLessThanThreshold() async throws {
        let identity = try await seedIdentity()

        do {
            _ = try await sut.setupSocialRecovery(
                identity: identity,
                guardianNames: guardianNames(count: 2),
                threshold: 5
            )
            XCTFail("Should throw when guardians < threshold")
        } catch let error as SocialRecoveryError {
            if case .insufficientGuardians = error {
                // Expected
            } else {
                XCTFail("Expected insufficientGuardians, got \(error)")
            }
        }
    }

    func testHasSocialRecoveryReturnsTrueWhenConfigured() async throws {
        let identity = try await seedIdentity()

        _ = try await sut.setupSocialRecovery(
            identity: identity,
            guardianNames: guardianNames(count: 3),
            threshold: 2
        )

        XCTAssertTrue(sut.hasSocialRecovery(did: identity.did))
    }

    func testHasSocialRecoveryReturnsFalseWhenNotConfigured() {
        XCTAssertFalse(sut.hasSocialRecovery(did: "did:ssdid:unconfigured"))
    }

    func testRecoverFailsWithInsufficientShares() async throws {
        let identity = try await seedIdentity()
        let guardians = guardianNames(count: 4)

        let shares = try await sut.setupSocialRecovery(
            identity: identity,
            guardianNames: guardians,
            threshold: 3
        )

        let config = sut.getConfig(did: identity.did)!

        // Collect only 1 share (below threshold of 3)
        let firstGuardian = config.guardians[0]
        let collectedShares: [Int: String] = [firstGuardian.shareIndex: shares[firstGuardian.id]!]

        do {
            _ = try await sut.recoverWithShares(
                did: identity.did,
                collectedShares: collectedShares,
                name: "ShouldFail",
                algorithm: .ED25519
            )
            XCTFail("Should throw for insufficient shares")
        } catch let error as SocialRecoveryError {
            if case .insufficientShares(let needed, let got) = error {
                XCTAssertEqual(needed, 3)
                XCTAssertEqual(got, 1)
            } else {
                XCTFail("Expected insufficientShares, got \(error)")
            }
        }
    }

    func testRecoverFailsWhenNotConfigured() async {
        do {
            _ = try await sut.recoverWithShares(
                did: "did:ssdid:unknown",
                collectedShares: [1: "AAAA"],
                name: "Fail",
                algorithm: .ED25519
            )
            XCTFail("Should throw when no config exists")
        } catch let error as SocialRecoveryError {
            if case .notConfigured = error {
                // Expected
            } else {
                XCTFail("Expected notConfigured, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }
}
