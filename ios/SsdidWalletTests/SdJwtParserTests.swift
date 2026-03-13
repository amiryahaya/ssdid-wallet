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
        XCTAssertEqual(disclosure.claimValue, "Ahmad")
    }

    func testDisclosureHashDeterministic() {
        let d = Disclosure(salt: "salt1", claimName: "name", claimValue: "Ahmad")
        XCTAssertEqual(d.hash(), d.hash())
        XCTAssertFalse(d.hash().isEmpty)
    }

    func testDisclosureEncodeRoundTrip() throws {
        let original = Disclosure(salt: "salt1", claimName: "name", claimValue: "Ahmad")
        let encoded = original.encode()
        let decoded = try Disclosure.decode(encoded)
        XCTAssertEqual(decoded.salt, "salt1")
        XCTAssertEqual(decoded.claimName, "name")
        XCTAssertEqual(decoded.claimValue, "Ahmad")
    }
}
