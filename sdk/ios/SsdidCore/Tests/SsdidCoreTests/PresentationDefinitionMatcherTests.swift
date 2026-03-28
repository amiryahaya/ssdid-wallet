import XCTest
@testable import SsdidCore

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

    private func parsePd(_ jsonString: String) throws -> [String: Any] {
        let data = jsonString.data(using: .utf8)!
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    func testMatchesCredentialByVctAndReturnsRequiredAndOptionalClaims() throws {
        let pd = try parsePd("""
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
        """)

        let results = matcher.match(pd: pd, credentials: [employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].descriptorId, "emp-cred")
        XCTAssertEqual(results[0].credential.id, "vc-1")
        XCTAssertTrue(results[0].requiredClaims.contains("name"))
        XCTAssertTrue(results[0].optionalClaims.contains("department"))
    }

    func testReturnsEmptyWhenNoCredentialMatchesVctFilter() throws {
        let pd = try parsePd("""
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
        """)

        let results = matcher.match(pd: pd, credentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }

    func testReturnsEmptyWhenRequiredClaimNotAvailable() throws {
        let pd = try parsePd("""
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
        """)

        let results = matcher.match(pd: pd, credentials: [employeeVc])
        XCTAssertTrue(results.isEmpty)
    }

    func testSelectsCorrectCredentialFromMultipleStored() throws {
        let degreeVc = StoredSdJwtVc(
            id: "vc-2", compact: "eyJ...", issuer: "did:ssdid:uni",
            subject: "did:ssdid:holder1", type: "UniversityDegree",
            claims: ["degree": "BSc"], disclosableClaims: ["degree"],
            issuedAt: 1719792000
        )

        let pd = try parsePd("""
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
        """)

        let results = matcher.match(pd: pd, credentials: [degreeVc, employeeVc])
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].credential.id, "vc-1")
    }
}
