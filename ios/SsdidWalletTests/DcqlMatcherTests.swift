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

    func testMatchesCredentialByVctValuesAndReturnsClaims() {
        let dcql = """
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
        """

        let results = matcher.match(dcql, storedCredentials: [employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].descriptorId, "emp-cred")
        XCTAssertEqual(results[0].availableClaims["name"]?.required, true)
        XCTAssertEqual(results[0].availableClaims["department"]?.required, false)
    }

    func testReturnsEmptyWhenRequiredClaimNotAvailable() {
        let dcql = """
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
        """

        let results = matcher.match(dcql, storedCredentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }

    func testConvertsToCredentialQuery() {
        let dcql = """
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
        """

        let query = matcher.toCredentialQuery(dcql)
        XCTAssertEqual(query.descriptors.count, 1)
        XCTAssertEqual(query.descriptors[0].vctFilter, "VerifiedEmployee")
        XCTAssertEqual(query.descriptors[0].requiredClaims, ["name"])
        XCTAssertEqual(query.descriptors[0].optionalClaims, ["department"])
    }
}
