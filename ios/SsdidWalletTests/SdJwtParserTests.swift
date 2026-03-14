import XCTest
@testable import SsdidWallet

final class SdJwtParserTests: XCTestCase {

    func testParseWithDisclosures() throws {
        let compact = "eyJhbGciOiJFZDI1NTE5In0.eyJfc2QiOlsiaGFzaDEiXX0.c2ln~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~"
        let parsed = try SdJwtParser.parse(compact)
        XCTAssertFalse(parsed.issuerJwt.isEmpty)
        XCTAssertEqual(parsed.disclosures.count, 1)
        XCTAssertNil(parsed.keyBindingJwt)
    }

    func testDisclosureDecode() throws {
        let encoded = "WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd"
        let disclosure = try Disclosure.decode(encoded)
        XCTAssertEqual(disclosure.salt, "salt1")
        XCTAssertEqual(disclosure.claimName, "name")
        XCTAssertEqual(disclosure.claimValue as? String, "Ahmad")
    }

    func testDisclosureHashDeterministic() throws {
        let d = Disclosure(salt: "salt1", claimName: "name", claimValue: "Ahmad")
        XCTAssertEqual(try d.hash(), try d.hash())
        XCTAssertFalse(try d.hash().isEmpty)
    }

    func testDisclosureEncodeRoundTrip() throws {
        let original = Disclosure(salt: "salt1", claimName: "name", claimValue: "Ahmad")
        let encoded = try original.encode()
        let decoded = try Disclosure.decode(encoded)
        XCTAssertEqual(decoded.salt, "salt1")
        XCTAssertEqual(decoded.claimName, "name")
        XCTAssertEqual(decoded.claimValue as? String, "Ahmad")
    }

    func testPresentReconstructsCompactForm() throws {
        let d1 = Disclosure(salt: "salt1", claimName: "name", claimValue: "Ahmad")
        let d2 = Disclosure(salt: "salt2", claimName: "dept", claimValue: "Engineering")
        let vc = SdJwtVc(issuerJwt: "header.payload.sig", disclosures: [d1, d2], keyBindingJwt: nil)

        // Present with one disclosure
        let compact = try vc.present(selectedDisclosures: [d1])
        XCTAssertTrue(compact.hasPrefix("header.payload.sig~"))
        XCTAssertTrue(compact.hasSuffix("~"), "Trailing ~ expected when no KB-JWT")

        // Round-trip parse
        let parsed = try SdJwtParser.parse(compact)
        XCTAssertEqual(parsed.disclosures.count, 1)
        XCTAssertEqual(parsed.disclosures[0].claimName, "name")
        XCTAssertNil(parsed.keyBindingJwt)
    }

    func testDisclosureWithNonStringValue() throws {
        let d = Disclosure(salt: "salt1", claimName: "age", claimValue: 42)
        let encoded = try d.encode()
        let decoded = try Disclosure.decode(encoded)
        XCTAssertEqual(decoded.claimName, "age")
        XCTAssertEqual(decoded.claimValue as? Int, 42)
    }

    func testEmptyInputParsesWithEmptyIssuerJwt() throws {
        // An empty string splits into [""], so the parser returns an SdJwtVc
        // with an empty issuerJwt rather than throwing.
        let result = try SdJwtParser.parse("")
        XCTAssertEqual(result.issuerJwt, "")
        XCTAssertTrue(result.disclosures.isEmpty)
        XCTAssertNil(result.keyBindingJwt)
    }
}
