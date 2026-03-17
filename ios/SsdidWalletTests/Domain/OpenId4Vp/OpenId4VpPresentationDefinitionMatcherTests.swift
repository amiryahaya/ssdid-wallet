import XCTest
@testable import SsdidWallet

final class OpenId4VpPresentationDefinitionMatcherTests: XCTestCase {

    private let matcher = PresentationDefinitionMatcher()

    private let credential = StoredSdJwtVc(
        id: "vc-1",
        compact: "eyJ...~disc1~disc2~",
        issuer: "did:ssdid:issuer1",
        subject: "did:ssdid:holder1",
        type: "IdentityCredential",
        claims: ["name": "Ahmad", "email": "ahmad@example.com"],
        disclosableClaims: ["name", "email"],
        issuedAt: 1_700_000_000
    )

    func testMatchByVct() throws {
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].credential.id, "vc-1")
        XCTAssertEqual(results[0].descriptorId, "id-1")
    }

    func testNoMatchWhenVctDiffers() throws {
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testMatchWithClaimFields() throws {
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}},{"path":["$.name"]},{"path":["$.email"],"optional":true}]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential])
        XCTAssertEqual(results.count, 1)
        XCTAssertTrue(results[0].requiredClaims.contains("name"))
        XCTAssertTrue(results[0].optionalClaims.contains("email"))
    }

    func testSkipNonSdJwtFormat() throws {
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"jwt_vp":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testMissingDescriptorId() throws {
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"format":{"vc+sd-jwt":{}},"constraints":{"fields":[]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential])
        XCTAssertTrue(results.isEmpty)
    }

    func testMultipleCredentials() throws {
        let cred2 = StoredSdJwtVc(
            id: "vc-2",
            compact: "eyJ2...~",
            issuer: "did:ssdid:i2",
            subject: "did:ssdid:h",
            type: "DriverLicense",
            claims: ["license_number": "DL123"],
            disclosableClaims: ["license_number"],
            issuedAt: 1_700_000_000
        )
        let pd = try parseJson(#"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}"#)
        let results = matcher.match(pd: pd, credentials: [credential, cred2])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].credential.id, "vc-2")
    }

    // MARK: - Helpers

    private func parseJson(_ string: String) throws -> [String: Any] {
        let data = string.data(using: .utf8)!
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }
}
