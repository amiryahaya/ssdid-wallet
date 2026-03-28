@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class OpenId4VpDcqlMatcherTests: XCTestCase {

    private let matcher = DcqlMatcher()

    private let credential = StoredSdJwtVc(
        id: "vc-1",
        compact: "eyJ...",
        issuer: "did:ssdid:issuer1",
        subject: "did:ssdid:holder1",
        type: "IdentityCredential",
        claims: ["name": "Ahmad", "email": "a@example.com"],
        disclosableClaims: ["name", "email"],
        issuedAt: 1_700_000_000
    )

    func testMatchByVctValues() throws {
        let dcql = try parseJson(#"{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].descriptorId, "cred-1")
    }

    func testNoMatchWhenVctDiffers() throws {
        let dcql = try parseJson(#"{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["DriverLicense"]}}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testMatchWithClaimsPaths() throws {
        let dcql = try parseJson(#"{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]},"claims":[{"path":["name"]},{"path":["email"],"optional":true}]}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertEqual(results.count, 1)
        XCTAssertTrue(results[0].requiredClaims.contains("name"))
        XCTAssertTrue(results[0].optionalClaims.contains("email"))
    }

    func testMissingCredentialId() throws {
        let dcql = try parseJson(#"{"credentials":[{"format":"vc+sd-jwt"}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testSkipNonSdJwtFormat() throws {
        let dcql = try parseJson(#"{"credentials":[{"id":"cred-1","format":"mso_mdoc","meta":{"vct_values":["IdentityCredential"]}}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testMatchWithoutClaimsSpec() throws {
        let dcql = try parseJson(#"{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}"#)
        let results = matcher.match(dcql: dcql, credentials: [credential])
        XCTAssertEqual(results.count, 1)
        // When no claims specified, all disclosable claims should be required
        XCTAssertTrue(results[0].requiredClaims.contains("name"))
        XCTAssertTrue(results[0].requiredClaims.contains("email"))
    }

    // MARK: - Helpers

    private func parseJson(_ string: String) throws -> [String: Any] {
        let data = string.data(using: .utf8)!
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }
}
