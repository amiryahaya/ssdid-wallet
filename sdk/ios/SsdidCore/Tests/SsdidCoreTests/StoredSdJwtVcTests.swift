import XCTest
@testable import SsdidCore

final class StoredSdJwtVcTests: XCTestCase {

    func testSerializationRoundTrip() throws {
        let original = StoredSdJwtVc(
            id: "vc-001",
            compact: "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Onh5eiJ9.c2ln~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~",
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: "VerifiedEmployee",
            claims: ["name": "Ahmad", "dept": "Engineering"],
            disclosableClaims: ["name"],
            issuedAt: 1700000000,
            expiresAt: 1700086400
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)
        let decoder = JSONDecoder()
        let decoded = try decoder.decode(StoredSdJwtVc.self, from: data)

        XCTAssertEqual(decoded, original)
        XCTAssertEqual(decoded.id, "vc-001")
        XCTAssertEqual(decoded.issuer, "did:key:z6MkIssuer")
        XCTAssertEqual(decoded.subject, "did:key:z6MkHolder")
        XCTAssertEqual(decoded.type, "VerifiedEmployee")
        XCTAssertEqual(decoded.claims, ["name": "Ahmad", "dept": "Engineering"])
        XCTAssertEqual(decoded.disclosableClaims, ["name"])
        XCTAssertEqual(decoded.issuedAt, 1700000000)
        XCTAssertEqual(decoded.expiresAt, 1700086400)
    }

    func testNullExpiresAt() throws {
        let vc = StoredSdJwtVc(
            id: "vc-002",
            compact: "header.payload.sig",
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: "VerifiableCredential",
            claims: [:],
            disclosableClaims: [],
            issuedAt: 1700000000,
            expiresAt: nil
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(vc)
        let decoder = JSONDecoder()
        let decoded = try decoder.decode(StoredSdJwtVc.self, from: data)

        XCTAssertEqual(decoded, vc)
        XCTAssertNil(decoded.expiresAt)
    }
}
