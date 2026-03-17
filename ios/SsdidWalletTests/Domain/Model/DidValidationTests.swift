import XCTest
@testable import SsdidWallet

final class DidValidationTests: XCTestCase {

    func testValidateAcceptsValidDid() throws {
        let did = try Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAx")
        XCTAssertEqual(did.value, "did:ssdid:dGVzdDEyMzQ1Njc4OTAx")
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
        let did = try Did.validate("did:ssdid:abc-def_ghi-jkl_mno")
        XCTAssertEqual(did.value, "did:ssdid:abc-def_ghi-jkl_mno")
    }

    func testValidateRejectsNullLikeStrings() {
        XCTAssertThrowsError(try Did.validate("null"))
        XCTAssertThrowsError(try Did.validate("undefined"))
    }
}
