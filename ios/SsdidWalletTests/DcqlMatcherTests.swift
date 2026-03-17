import XCTest
@testable import SsdidWallet

final class DcqlMatcherTests: XCTestCase {

    private let matcher = DcqlMatcher()

    private let employeeVc = StoredSdJwtVc(
        id: "vc-1",
        compact: "eyJ...",
        issuer: "did:ssdid:issuer1",
        subject: "did:ssdid:holder1",
        type: "VerifiedEmployee",
        claims: ["name": "Ahmad", "department": "Engineering"],
        disclosableClaims: ["name", "department"],
        issuedAt: 1719792000
    )

    private func parseDcql(_ jsonString: String) throws -> [String: Any] {
        let data = jsonString.data(using: .utf8)!
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    func testMatchesCredentialByVctValuesAndReturnsClaims() throws {
        let dcql = try parseDcql("""
        {
          "credentials": [{
            "id": "emp-cred",
            "format": "vc+sd-jwt",
            "meta": { "vct_values": ["VerifiedEmployee"] },
            "claims": [
              { "path": ["name"] },
              { "path": ["department"], "optional": true }
            ]
          }]
        }
        """)

        let results = matcher.match(dcql: dcql, credentials: [employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].descriptorId, "emp-cred")
        XCTAssertTrue(results[0].requiredClaims.contains("name"))
        XCTAssertTrue(results[0].optionalClaims.contains("department"))
    }

    func testReturnsEmptyWhenRequiredClaimNotAvailable() throws {
        let dcql = try parseDcql("""
        {
          "credentials": [{
            "id": "id-cred",
            "format": "vc+sd-jwt",
            "meta": { "vct_values": ["VerifiedEmployee"] },
            "claims": [
              { "path": ["national_id"] }
            ]
          }]
        }
        """)

        let results = matcher.match(dcql: dcql, credentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }
}
