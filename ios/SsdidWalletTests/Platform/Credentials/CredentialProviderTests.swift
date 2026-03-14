import XCTest
@testable import SsdidWallet

/// Minimal mock vault that returns pre-configured mdocs.
private final class MockVaultForCredentials: Vault, @unchecked Sendable {

    var storedMDocs: [StoredMDoc] = []
    var storedCredentials: [VerifiableCredential] = []

    func createIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        fatalError("not implemented")
    }
    func getIdentity(keyId: String) async -> Identity? { nil }
    func listIdentities() async -> [Identity] { [] }
    func deleteIdentity(keyId: String) async throws {}
    func sign(keyId: String, data: Data) async throws -> Data { Data() }
    func buildDidDocument(keyId: String) async throws -> DidDocument {
        fatalError("not implemented")
    }
    func createProof(
        keyId: String,
        document: [String: Any],
        proofPurpose: String,
        challenge: String?,
        domain: String?
    ) async throws -> Proof {
        fatalError("not implemented")
    }
    func storeCredential(_ credential: VerifiableCredential) async throws {}
    func listCredentials() async -> [VerifiableCredential] { storedCredentials }
    func getCredentialForDid(_ did: String) async -> VerifiableCredential? { nil }
    func deleteCredential(credentialId: String) async throws {}
    func storeMDoc(_ mdoc: StoredMDoc) async throws {}
    func listMDocs() async -> [StoredMDoc] { storedMDocs }
    func getMDoc(id: String) async -> StoredMDoc? { storedMDocs.first { $0.id == id } }
    func deleteMDoc(id: String) async throws {}
}

@available(iOS 16.0, *)
final class CredentialProviderTests: XCTestCase {

    private var mockVault: MockVaultForCredentials!
    private var provider: SsdidCredentialProvider!

    override func setUp() {
        super.setUp()
        mockVault = MockVaultForCredentials()
        provider = SsdidCredentialProvider(vault: mockVault)
    }

    // MARK: - SD-JWT matching

    func testMatchFindsAllSdJwtCredentials() async {
        let vc1 = StoredSdJwtVc(
            id: "vc-1",
            compact: "eyJ...",
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder",
            type: "IdentityCredential",
            claims: ["name": "Ahmad"],
            disclosableClaims: ["name"],
            issuedAt: 1_700_000_000
        )
        let vc2 = StoredSdJwtVc(
            id: "vc-2",
            compact: "eyJ...",
            issuer: "did:ssdid:issuer2",
            subject: "did:ssdid:holder",
            type: "DriverLicense",
            claims: ["license_number": "DL-123"],
            disclosableClaims: ["license_number"],
            issuedAt: 1_700_000_000
        )

        let matches = await provider.matchCredentials(sdJwtVcs: [vc1, vc2])

        let sdJwtMatches = matches.filter {
            if case .sdJwt = $0.credentialRef { return true }
            return false
        }
        XCTAssertEqual(sdJwtMatches.count, 2)
        XCTAssertEqual(sdJwtMatches[0].id, "vc-1")
        XCTAssertEqual(sdJwtMatches[0].title, "IdentityCredential")
        XCTAssertEqual(sdJwtMatches[1].id, "vc-2")
        XCTAssertEqual(sdJwtMatches[1].title, "DriverLicense")
    }

    // MARK: - mdoc matching

    func testMatchFindsMDocCredentials() async {
        mockVault.storedMDocs = [
            StoredMDoc(
                id: "mdoc-1",
                docType: "org.iso.18013.5.1.mDL",
                issuerSignedCbor: Data([0xa0]),
                deviceKeyId: "key-1",
                issuedAt: 1_700_000_000,
                nameSpaces: ["org.iso.18013.5.1": ["family_name", "given_name"]]
            ),
            StoredMDoc(
                id: "mdoc-2",
                docType: "org.iso.23220.2.1.mID",
                issuerSignedCbor: Data([0xa0]),
                deviceKeyId: "key-2",
                issuedAt: 1_700_000_000
            )
        ]

        let matches = await provider.matchCredentials()

        let mdocMatches = matches.filter {
            if case .mdoc = $0.credentialRef { return true }
            return false
        }
        XCTAssertEqual(mdocMatches.count, 2)
        XCTAssertEqual(mdocMatches[0].id, "mdoc-1")
        XCTAssertEqual(mdocMatches[0].title, "Mobile Driver's License")
        XCTAssertEqual(mdocMatches[1].id, "mdoc-2")
        XCTAssertEqual(mdocMatches[1].title, "Mobile Identity Document")
    }

    // MARK: - Mixed matching

    func testMatchReturnsBothSdJwtAndMDocCredentials() async {
        let vc = StoredSdJwtVc(
            id: "vc-1",
            compact: "eyJ...",
            issuer: "did:ssdid:issuer",
            subject: "did:ssdid:holder",
            type: "IdentityCredential",
            claims: [:],
            disclosableClaims: [],
            issuedAt: 1_700_000_000
        )
        mockVault.storedMDocs = [
            StoredMDoc(
                id: "mdoc-1",
                docType: "org.iso.18013.5.1.mDL",
                issuerSignedCbor: Data([0xa0]),
                deviceKeyId: "key-1",
                issuedAt: 1_700_000_000
            )
        ]

        let matches = await provider.matchCredentials(sdJwtVcs: [vc])

        XCTAssertEqual(matches.count, 2)

        if case .sdJwt(let stored) = matches[0].credentialRef {
            XCTAssertEqual(stored.id, "vc-1")
        } else {
            XCTFail("Expected first match to be sdJwt")
        }

        if case .mdoc(let stored) = matches[1].credentialRef {
            XCTAssertEqual(stored.id, "mdoc-1")
        } else {
            XCTFail("Expected second match to be mdoc")
        }
    }

    // MARK: - Empty vault

    func testMatchReturnsEmptyWhenNoCredentials() async {
        let matches = await provider.matchCredentials()
        XCTAssertTrue(matches.isEmpty)
    }

    // MARK: - formatDocType

    func testFormatDocTypeMDL() {
        XCTAssertEqual(
            provider.formatDocType("org.iso.18013.5.1.mDL"),
            "Mobile Driver's License"
        )
    }

    func testFormatDocTypeMID() {
        XCTAssertEqual(
            provider.formatDocType("org.iso.23220.2.1.mID"),
            "Mobile Identity Document"
        )
    }

    func testFormatDocTypeUnknownUsesLastComponent() {
        XCTAssertEqual(
            provider.formatDocType("com.example.custom.MyDoc"),
            "MyDoc"
        )
    }

    func testFormatDocTypeSingleComponent() {
        XCTAssertEqual(
            provider.formatDocType("SimpleDoc"),
            "SimpleDoc"
        )
    }
}
