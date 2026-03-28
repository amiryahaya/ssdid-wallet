import XCTest
@testable import SsdidCore

final class DidValidationTests: XCTestCase {

    func testValidateAcceptsValidDid() throws {
        let did = try Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM")
        XCTAssertEqual(did.value, "did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM")
    }

    func testValidateAcceptsGeneratedDid() throws {
        let generated = Did.generate()
        let did = try Did.validate(generated.value)
        XCTAssertEqual(did.value, generated.value)
    }

    func testValidateRejectsEmptyString() {
        XCTAssertThrowsError(try Did.validate(""))
    }

    func testValidateRejectsWrongPrefix() {
        XCTAssertThrowsError(try Did.validate("did:key:z6MkhaXgBZD"))
    }

    func testValidateRejectsDidSsdidWithNoId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:"))
    }

    func testValidateRejectsShortId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:abc"))
    }

    func testValidateRejectsInvalidBase64urlChars() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:invalid+chars/here=="))
    }

    func testValidateRejectsSpacesInId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:has spaces here!"))
    }

    func testValidateAcceptsBase64urlWithHyphensAndUnderscores() throws {
        let did = try Did.validate("did:ssdid:abc-def_ghi-jkl_mno_pqr")
        XCTAssertEqual(did.value, "did:ssdid:abc-def_ghi-jkl_mno_pqr")
    }

    func testValidateRejectsNullLikeStrings() {
        XCTAssertThrowsError(try Did.validate("null"))
        XCTAssertThrowsError(try Did.validate("undefined"))
    }

    // D1: Minimum length should be 22 for 128-bit entropy
    func testValidateRejects21CharId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:abcdefghijklmnopqrstu"))
    }

    func testValidateAcceptsExactly22CharId() throws {
        let did = try Did.validate("did:ssdid:abcdefghijklmnopqrstuv")
        XCTAssertEqual(did.value, "did:ssdid:abcdefghijklmnopqrstuv")
    }

    // D2: Unicode alphanumerics must be rejected
    func testValidateRejectsUnicodeAlphanumerics() {
        // Full-width Unicode 'A' and 'a' — pass CharacterSet.alphanumerics but should fail
        XCTAssertThrowsError(try Did.validate("did:ssdid:\u{FF21}\u{FF41}bcdefghijklmnopqrstuv"))
    }

    // D5: Max length to prevent DoS
    func testValidateRejectsExcessivelyLongId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:" + String(repeating: "a", count: 200)))
    }

    // D6: Additional attack vectors
    func testValidateRejectsNullByteInId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:abcdefghijklmnopqrst\u{0000}v"))
    }

    func testValidateRejectsColonInId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:abc:defghijklmnopqrstuv"))
    }

    func testValidateRejectsPaddingCharInId() {
        XCTAssertThrowsError(try Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM="))
    }
}
