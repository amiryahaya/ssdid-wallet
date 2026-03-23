import XCTest
@testable import SsdidWallet

final class OfflineVerifierTests: XCTestCase {

    private var bundleStore: MockBundleStore!
    private var classicalProvider: ClassicalProvider!
    private var pqcProvider: MockCryptoProvider!
    private var verifier: OfflineVerifier!

    override func setUp() {
        super.setUp()
        bundleStore = MockBundleStore()
        classicalProvider = ClassicalProvider()
        pqcProvider = MockCryptoProvider()
        verifier = OfflineVerifier(
            classicalProvider: classicalProvider,
            pqcProvider: pqcProvider,
            bundleStore: bundleStore
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
        let bundle = VerificationBundle(
            issuerDid: "did:ssdid:issuer",
            didDocument: DidDocument(
                id: "did:ssdid:issuer",
                controller: "did:ssdid:issuer",
                verificationMethod: [],
                authentication: [],
                assertionMethod: []
            ),
            fetchedAt: formatter.string(from: Date().addingTimeInterval(-172800)),
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

final class MockBundleStore: BundleStore {
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

// MARK: - Mock Crypto Provider

final class MockCryptoProvider: CryptoProvider {
    func generateKeyPair(algorithm: Algorithm) throws -> KeyPair {
        KeyPair(publicKey: Data(repeating: 0, count: 32), privateKey: Data(repeating: 1, count: 64))
    }

    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        Data(repeating: 0xFF, count: 64)
    }

    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        false
    }
}
