@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class OfflineVerifierTests: XCTestCase {

    private var bundleStore: MockBundleStore!
    private var classicalProvider: ClassicalProvider!
    private var pqcProvider: MockCryptoProvider!
    private var ttlProvider: TtlProvider!
    private var verifier: OfflineVerifier!

    override func setUp() {
        super.setUp()
        bundleStore = MockBundleStore()
        classicalProvider = ClassicalProvider()
        pqcProvider = MockCryptoProvider()
        // Use a fresh UserDefaults domain to avoid shared state between tests
        let defaults = UserDefaults(suiteName: "OfflineVerifierTestSuite") ?? .standard
        defaults.set(7, forKey: "ssdid_bundle_ttl_days")
        ttlProvider = TtlProvider(userDefaults: defaults)
        verifier = OfflineVerifier(
            classicalProvider: classicalProvider,
            pqcProvider: pqcProvider,
            bundleStore: bundleStore,
            ttlProvider: ttlProvider
        )
    }

    func testVerifyCredentialReturnsErrorWhenNoBundleExists() async {
        let vc = makeTestVc(keyId: "did:ssdid:unknown#key-1")
        let result = await verifier.verifyCredential(vc)

        XCTAssertFalse(result.isValid)
        XCTAssertNotNil(result.error)
        XCTAssertTrue(result.error?.contains("No cached bundle") == true)
    }

    func testVerifyCredentialDetectsExpiredCredential() async {
        let formatter = ISO8601DateFormatter()
        let expired = formatter.string(from: Date().addingTimeInterval(-86400))

        var vc = makeTestVc(keyId: "did:ssdid:issuer#key-1")
        vc = VerifiableCredential(
            id: vc.id,
            type: vc.type,
            issuer: vc.issuer,
            issuanceDate: vc.issuanceDate,
            expirationDate: expired,
            credentialSubject: vc.credentialSubject,
            proof: vc.proof
        )

        let result = await verifier.verifyCredential(vc)

        XCTAssertFalse(result.isValid)
        XCTAssertTrue(result.error?.contains("expired") == true)
    }

    func testVerifyCredentialMarksStaleBundleAsFalse() async {
        let formatter = ISO8601DateFormatter()
        // fetchedAt is 8 days ago; with 7-day TTL the bundle is expired
        let bundle = VerificationBundle(
            issuerDid: "did:ssdid:issuer",
            didDocument: DidDocument(
                id: "did:ssdid:issuer",
                controller: "did:ssdid:issuer",
                verificationMethod: [],
                authentication: [],
                assertionMethod: []
            ),
            fetchedAt: formatter.string(from: Date().addingTimeInterval(-8 * 86400)),
            expiresAt: formatter.string(from: Date().addingTimeInterval(-86400))
        )
        bundleStore.bundles["did:ssdid:issuer"] = bundle

        let vc = makeTestVc(keyId: "did:ssdid:issuer#key-1")
        let result = await verifier.verifyCredential(vc)

        XCTAssertFalse(result.bundleFresh)
    }

    func testVerifyCredentialReturnsErrorWhenKeyNotInDocument() async {
        let formatter = ISO8601DateFormatter()
        let bundle = VerificationBundle(
            issuerDid: "did:ssdid:issuer",
            didDocument: DidDocument(
                id: "did:ssdid:issuer",
                controller: "did:ssdid:issuer",
                verificationMethod: [],
                authentication: [],
                assertionMethod: []
            ),
            fetchedAt: formatter.string(from: Date()),
            expiresAt: formatter.string(from: Date().addingTimeInterval(86400))
        )
        bundleStore.bundles["did:ssdid:issuer"] = bundle

        let vc = makeTestVc(keyId: "did:ssdid:issuer#key-1")
        let result = await verifier.verifyCredential(vc)

        XCTAssertFalse(result.isValid)
        XCTAssertTrue(result.error?.contains("Key not found") == true)
    }

    // MARK: - B2: DID key rotation — credential signed with new key B, bundle has old key A → invalid

    func testKeyRotation_oldBundle_newCredential_returnsFailed() async throws {
        // Key pair A: old key cached in DID Document
        let kpA = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let issuerDid = "did:ssdid:issuer-rotation"
        let keyId = "\(issuerDid)#key-1"

        let didDocWithKeyA = DidDocument(
            id: issuerDid,
            controller: issuerDid,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: "Ed25519VerificationKey2020",
                    controller: issuerDid,
                    publicKeyMultibase: Multibase.encode(kpA.publicKey)
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId]
        )

        let formatter = ISO8601DateFormatter()
        let bundle = VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDocWithKeyA,
            fetchedAt: formatter.string(from: Date()),
            expiresAt: formatter.string(from: Date().addingTimeInterval(86400))
        )
        bundleStore.bundles[issuerDid] = bundle

        // Key pair B: new rotated key — signs the credential but NOT in cached DID doc
        let kpB = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let newKeyId = "\(issuerDid)#key-2"

        let vc = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: newKeyId,
            privateKey: kpB.privateKey
        )

        let result = await verifier.verifyCredential(vc)

        // new key (key-2) is not in the cached DID doc → error
        XCTAssertFalse(result.signatureValid)
        XCTAssertTrue(result.error?.contains("Key not found") == true)
    }

    // MARK: - B3: Storage failure — FailingBundleStore returns nil on read, saveBundle throws

    func testStorageFailure_bundleStoreReturnsNilGracefully() async throws {
        // FailingBundleStore: always throws on saveBundle, returns nil on getBundle
        let failingStore = FailingBundleStore()
        let failingVerifier = OfflineVerifier(
            classicalProvider: classicalProvider,
            pqcProvider: pqcProvider,
            bundleStore: failingStore,
            ttlProvider: ttlProvider
        )

        let vc = makeTestVc(keyId: "did:ssdid:unknown#key-1")
        let result = await failingVerifier.verifyCredential(vc)

        // Store returns nil for any read → missing bundle → graceful error
        XCTAssertFalse(result.isValid)
        XCTAssertTrue(result.error?.contains("No cached bundle") == true)
    }

    // MARK: - B7: Status list URL mismatch → unknown revocation

    func testStatusListUrlMismatch_returnsUnknownRevocation() async throws {
        let kp = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let issuerDid = "did:ssdid:issuer-urlmismatch"
        let keyId = "\(issuerDid)#key-1"
        let formatter = ISO8601DateFormatter()

        let didDoc = DidDocument(
            id: issuerDid,
            controller: issuerDid,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: "Ed25519VerificationKey2020",
                    controller: issuerDid,
                    publicKeyMultibase: Multibase.encode(kp.publicKey)
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId]
        )

        // Status list cached with id = ".../status/1"
        let statusList = StatusListCredential(
            id: "https://registry.ssdid.my/status/1",
            type: ["VerifiableCredential", "BitstringStatusListCredential"],
            issuer: issuerDid,
            credentialSubject: StatusListSubject(
                type: "BitstringStatusList",
                statusPurpose: "revocation",
                encodedList: "H4sIAAAAAAAA/2NgAAIABQABKjN9HQAAAA" // empty bitstring placeholder
            ),
            proof: Proof(
                type: "Ed25519Signature2020",
                created: formatter.string(from: Date()),
                verificationMethod: keyId,
                proofPurpose: "assertionMethod",
                proofValue: "uDummy"
            )
        )

        let bundle = VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            statusList: statusList,
            fetchedAt: formatter.string(from: Date()),
            expiresAt: formatter.string(from: Date().addingTimeInterval(86400))
        )
        bundleStore.bundles[issuerDid] = bundle

        // Credential references ".../status/2" — mismatched URL
        let credentialStatus = CredentialStatus(
            id: "https://registry.ssdid.my/status/2#5",
            type: "BitstringStatusListEntry",
            statusPurpose: "revocation",
            statusListIndex: "5",
            statusListCredential: "https://registry.ssdid.my/status/2"
        )
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: kp.privateKey,
            credentialStatus: credentialStatus
        )

        let result = await verifier.verifyCredential(credential)

        XCTAssertTrue(result.signatureValid)
        XCTAssertEqual(result.revocationStatus, .unknown)
    }

    // MARK: - D9: Direct round-trip test with real Ed25519 key pair

    func testDirectRoundTrip_signatureValid_bundleFresh_revocationValid() async throws {
        // Create a real Ed25519 key pair
        let kp = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let issuerDid = "did:ssdid:issuer-roundtrip"
        let keyId = "\(issuerDid)#key-1"
        let formatter = ISO8601DateFormatter()

        // Build a DID Document containing the public key
        let didDoc = DidDocument(
            id: issuerDid,
            controller: issuerDid,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: "Ed25519VerificationKey2020",
                    controller: issuerDid,
                    publicKeyMultibase: Multibase.encode(kp.publicKey)
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId]
        )

        // Cache a fresh bundle (fetched 1 hour ago, expires in ~6.9 days)
        let bundle = VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            fetchedAt: formatter.string(from: Date().addingTimeInterval(-3600)),
            expiresAt: formatter.string(from: Date().addingTimeInterval(6 * 86400))
        )
        bundleStore.bundles[issuerDid] = bundle

        // Create a real signed credential using OfflineTestHelper
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: kp.privateKey
        )

        // Call offlineVerifier.verifyCredential() directly
        let result = await verifier.verifyCredential(credential)

        XCTAssertTrue(result.signatureValid, "Expected signatureValid == true for a real Ed25519 signature")
        XCTAssertTrue(result.bundleFresh, "Expected bundleFresh == true for a bundle fetched 1 hour ago")
        XCTAssertEqual(result.revocationStatus, .valid, "Expected revocationStatus == .valid when no credentialStatus")
        XCTAssertNil(result.error, "Expected no error for a valid round-trip verification")
        XCTAssertTrue(result.isValid, "Expected isValid == true")
    }

    // MARK: - Helpers

    private func makeTestVc(keyId: String) -> VerifiableCredential {
        let issuerDid = Did.fromKeyId(keyId).value
        let formatter = ISO8601DateFormatter()
        return VerifiableCredential(
            id: "urn:uuid:test",
            type: ["VerifiableCredential"],
            issuer: issuerDid,
            issuanceDate: formatter.string(from: Date()),
            credentialSubject: CredentialSubject(id: "did:ssdid:holder"),
            proof: Proof(
                type: "Ed25519Signature2020",
                created: formatter.string(from: Date()),
                verificationMethod: keyId,
                proofPurpose: "assertionMethod",
                proofValue: "uABC"
            )
        )
    }
}

// MARK: - Mock Bundle Store

final class MockBundleStore: BundleStore, @unchecked Sendable {
    var bundles: [String: VerificationBundle] = [:]

    func saveBundle(_ bundle: VerificationBundle) async throws {
        bundles[bundle.issuerDid] = bundle
    }

    func getBundle(issuerDid: String) async -> VerificationBundle? {
        return bundles[issuerDid]
    }

    func deleteBundle(issuerDid: String) async throws {
        bundles.removeValue(forKey: issuerDid)
    }

    func listBundles() async throws -> [VerificationBundle] {
        return Array(bundles.values)
    }
}

// MARK: - Failing Bundle Store

final class FailingBundleStore: BundleStore, @unchecked Sendable {
    func saveBundle(_ bundle: VerificationBundle) async throws {
        throw NSError(domain: "TestDomain", code: -1, userInfo: [NSLocalizedDescriptionKey: "Disk full"])
    }

    func getBundle(issuerDid: String) async -> VerificationBundle? {
        return nil
    }

    func deleteBundle(issuerDid: String) async throws {}

    func listBundles() async throws -> [VerificationBundle] {
        return []
    }
}

// MARK: - Mock Crypto Provider

final class MockCryptoProvider: CryptoProvider {
    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool { true }
    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        KeyPairResult(publicKey: Data(repeating: 0, count: 32), privateKey: Data(repeating: 1, count: 64))
    }

    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        Data(repeating: 0xFF, count: 64)
    }

    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        false
    }
}
