@testable import SsdidCore
import XCTest
@testable import SsdidWallet

/// Stub VC store that returns pre-configured credentials.
private final class StubVcStore: SdJwtVcStore, @unchecked Sendable {
    var storedVcs: [StoredSdJwtVc] = []

    func listSdJwtVcs() async -> [StoredSdJwtVc] {
        storedVcs
    }
}

final class OpenId4VpIntegrationTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in "test-sig".data(using: .utf8)! }

    // MARK: - Helpers

    /// Issue a real SD-JWT VC and return both the SdJwtVc and a StoredSdJwtVc built from it.
    private func issueAndStore(
        type: String = "IdentityCredential",
        claims: [String: Any] = ["given_name": "Alice", "family_name": "Smith", "email": "alice@example.com"],
        disclosable: Set<String> = ["given_name", "family_name", "email"]
    ) throws -> (sdJwtVc: SdJwtVc, stored: StoredSdJwtVc) {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let vc = try issuer.issue(
            issuer: "https://issuer.example.com",
            subject: "did:ssdid:holder123",
            type: ["VerifiableCredential", type],
            claims: claims,
            disclosable: disclosable
        )

        let compact = try vc.present(selectedDisclosures: vc.disclosures)

        // Extract claim values as [String: String] for StoredSdJwtVc
        var claimStrings: [String: String] = [:]
        for d in vc.disclosures {
            claimStrings[d.claimName] = "\(d.claimValue)"
        }

        let stored = StoredSdJwtVc(
            id: "vc-integration-1",
            compact: compact,
            issuer: "https://issuer.example.com",
            subject: "did:ssdid:holder123",
            type: type,
            claims: claimStrings,
            disclosableClaims: Array(disclosable),
            issuedAt: 1700000000
        )

        return (vc, stored)
    }

    private func makeHandler(storedVcs: [StoredSdJwtVc]) -> OpenId4VpHandler {
        let store = StubVcStore()
        store.storedVcs = storedVcs
        return OpenId4VpHandler(
            transport: OpenId4VpTransport(),
            peMatcher: PresentationDefinitionMatcher(),
            dcqlMatcher: DcqlMatcher(),
            vcStore: store
        )
    }

    // MARK: - Test: VP Token building

    func testVpTokenBuildingWithKbJwt() throws {
        let (issuedVc, stored) = try issueAndStore()

        // Round-trip: present then parse back (simulates what the wallet stores and retrieves)
        let compact = try issuedVc.present(selectedDisclosures: issuedVc.disclosures)
        let parsedVc = try SdJwtParser.parse(compact)

        // Build VP token
        let selectedClaims = ["given_name", "family_name"]
        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: stored,
            selectedClaims: selectedClaims,
            audience: "https://verifier.example.com",
            nonce: "test-nonce-789",
            algorithm: "EdDSA",
            signer: testSigner
        )

        // Verify token structure: issuerJwt ~ disclosures ~ KB-JWT
        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: false).map(String.init)

        // First part is the issuer JWT
        let issuerJwt = parts[0]
        XCTAssertEqual(issuerJwt.filter { $0 == "." }.count, 2, "Issuer JWT should have 3 dot-separated parts")

        // Middle parts are selected disclosures (should be exactly 2 for given_name and family_name)
        let disclosureParts = parts.dropFirst().dropLast()
        let nonEmptyDisclosures = disclosureParts.filter { !$0.isEmpty }
        XCTAssertEqual(nonEmptyDisclosures.count, 2, "Should have exactly 2 selected disclosures")

        // Verify disclosures decode to the correct claim names
        var disclosedClaimNames: Set<String> = []
        for d in nonEmptyDisclosures {
            let disclosure = try Disclosure.decode(d)
            disclosedClaimNames.insert(disclosure.claimName)
        }
        XCTAssertEqual(disclosedClaimNames, Set(selectedClaims))

        // Last part is the KB-JWT
        let kbJwtString = parts.last!
        XCTAssertFalse(kbJwtString.isEmpty, "KB-JWT should not be empty")
        XCTAssertEqual(kbJwtString.filter { $0 == "." }.count, 2, "KB-JWT should have 3 dot-separated parts")

        // Verify KB-JWT header
        let kbParts = kbJwtString.split(separator: ".").map(String.init)
        let kbHeaderData = Data(base64URLEncoded: kbParts[0])!
        let kbHeader = try JSONSerialization.jsonObject(with: kbHeaderData) as! [String: Any]
        XCTAssertEqual(kbHeader["typ"] as? String, "kb+jwt")
        XCTAssertEqual(kbHeader["alg"] as? String, "EdDSA")

        // Verify KB-JWT payload has correct aud and nonce
        let kbPayloadData = Data(base64URLEncoded: kbParts[1])!
        let kbPayload = try JSONSerialization.jsonObject(with: kbPayloadData) as! [String: Any]
        XCTAssertEqual(kbPayload["aud"] as? String, "https://verifier.example.com")
        XCTAssertEqual(kbPayload["nonce"] as? String, "test-nonce-789")
        XCTAssertNotNil(kbPayload["sd_hash"], "KB-JWT payload should contain sd_hash")
        XCTAssertNotNil(kbPayload["iat"], "KB-JWT payload should contain iat")
    }
}
