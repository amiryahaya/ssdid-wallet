import XCTest
@testable import SsdidWallet

final class PresentationDefinitionMatcherTests: XCTestCase {

    private let matcher = PresentationDefinitionMatcher()

    private let employeeVc = StoredSdJwtVc(
        id: "vc-1",
        compact: "eyJ...",
        issuer: "did:ssdid:issuer1",
        subject: "did:ssdid:holder1",
        type: "VerifiedEmployee",
        claims: ["name": "Ahmad", "department": "Engineering", "employeeId": "EMP-1234"],
        disclosableClaims: ["name", "department"],
        issuedAt: 1719792000
    )

    func testMatchesCredentialByVctAndReturnsRequiredAndOptionalClaims() {
        let pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": { "alg": ["EdDSA"] } },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """

        let results = matcher.match(pd, storedCredentials: [employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].descriptorId, "emp-cred")
        XCTAssertEqual(results[0].credentialId, "vc-1")

        let claims = results[0].availableClaims
        XCTAssertEqual(claims["name"]?.required, true)
        XCTAssertEqual(claims["name"]?.available, true)
        XCTAssertEqual(claims["department"]?.required, false)
        XCTAssertEqual(claims["department"]?.available, true)
    }

    func testReturnsEmptyWhenNoCredentialMatchesVctFilter() {
        let pd = """
        {
          "id": "req-2",
          "input_descriptors": [{
            "id": "gov-id",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "GovernmentId" } }
              ]
            }
          }]
        }
        """

        let results = matcher.match(pd, storedCredentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }

    func testReturnsEmptyWhenRequiredClaimNotAvailable() {
        let pd = """
        {
          "id": "req-3",
          "input_descriptors": [{
            "id": "id-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.national_id"] }
              ]
            }
          }]
        }
        """

        let results = matcher.match(pd, storedCredentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }

    func testSelectsCorrectCredentialFromMultipleStored() {
        let degreeVc = StoredSdJwtVc(
            id: "vc-2", compact: "eyJ...", issuer: "did:ssdid:uni",
            subject: "did:ssdid:holder1", type: "UniversityDegree",
            claims: ["degree": "BSc"], disclosableClaims: ["degree"],
            issuedAt: 1719792000
        )

        let pd = """
        {
          "id": "req-4",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] }
              ]
            }
          }]
        }
        """

        let results = matcher.match(pd, storedCredentials: [degreeVc, employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].credentialId, "vc-1")
    }

    func testConvertsToCredentialQuery() {
        let pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """

        let query = matcher.toCredentialQuery(pd)
        XCTAssertEqual(query.descriptors.count, 1)
        XCTAssertEqual(query.descriptors[0].vctFilter, "VerifiedEmployee")
        XCTAssertEqual(query.descriptors[0].requiredClaims, ["name"])
        XCTAssertEqual(query.descriptors[0].optionalClaims, ["department"])
    }
}
