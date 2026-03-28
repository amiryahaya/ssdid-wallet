import XCTest
@testable import SsdidCore

final class SdJwtIssuerTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in Data("test-signature".utf8) }

    func testIssueCreatesDisclosuresForDisclosableClaims() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential", "VerifiedEmployee"],
            claims: ["name": "Ahmad", "dept": "Engineering"],
            disclosable: Set(["name"])
        )
        XCTAssertEqual(vc.disclosures.count, 1)
        XCTAssertEqual(vc.disclosures[0].claimName, "name")
    }

    func testIssueWithEmptyClaims() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential"],
            claims: [:],
            disclosable: Set()
        )
        XCTAssertTrue(vc.disclosures.isEmpty)
        XCTAssertFalse(vc.issuerJwt.isEmpty)
    }

    func testIssueWithAllClaimsDisclosable() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let claims: [String: Any] = ["name": "Ahmad", "dept": "Engineering", "age": 30]
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential"],
            claims: claims,
            disclosable: Set(["name", "dept", "age"])
        )
        XCTAssertEqual(vc.disclosures.count, 3)
        let names = vc.disclosures.map { $0.claimName }.sorted()
        XCTAssertEqual(names, ["age", "dept", "name"])
    }

    func testIssueWithHolderKeyJwk() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let holderJwk = ["kty": "OKP", "crv": "Ed25519", "x": "testKeyX"]
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential"],
            claims: ["name": "Ahmad"],
            disclosable: Set(),
            holderKeyJwk: holderJwk
        )
        // Parse the payload to verify cnf is present
        let jwtParts = vc.issuerJwt.split(separator: ".")
        XCTAssertEqual(jwtParts.count, 3)
        let payloadData = Data(base64URLEncoded: String(jwtParts[1]))!
        let payload = try JSONSerialization.jsonObject(with: payloadData) as! [String: Any]
        let cnf = payload["cnf"] as? [String: Any]
        XCTAssertNotNil(cnf)
        let jwk = cnf?["jwk"] as? [String: String]
        XCTAssertEqual(jwk?["kty"], "OKP")
        XCTAssertEqual(jwk?["crv"], "Ed25519")
    }

    func testCompactFormCanBeParsedBack() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential"],
            claims: ["name": "Ahmad", "dept": "Engineering"],
            disclosable: Set(["name"])
        )
        let compact = try vc.present(selectedDisclosures: vc.disclosures)
        let parsed = try SdJwtParser.parse(compact)
        XCTAssertEqual(parsed.disclosures.count, 1)
        XCTAssertEqual(parsed.disclosures[0].claimName, "name")
        XCTAssertEqual(parsed.disclosures[0].claimValue as? String, "Ahmad")
        XCTAssertNil(parsed.keyBindingJwt)
    }

    func testIssueWithNonStringClaimValue() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let claims: [String: Any] = ["age": 30, "active": true]
        let vc = try issuer.issue(
            issuer: "did:key:z6MkIssuer",
            subject: "did:key:z6MkHolder",
            type: ["VerifiableCredential"],
            claims: claims,
            disclosable: Set(["age", "active"])
        )
        XCTAssertEqual(vc.disclosures.count, 2)
        let ageDisclosure = vc.disclosures.first { $0.claimName == "age" }
        XCTAssertNotNil(ageDisclosure)
        XCTAssertEqual(ageDisclosure?.claimValue as? Int, 30)
    }
}
