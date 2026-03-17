import XCTest
@testable import SsdidWallet

final class AuthorizationRequestParsingTests: XCTestCase {

    func testParseByReference() throws {
        let uri = "openid4vp://?client_id=https://verifier.example.com&request_uri=https://verifier.example.com/request/123"
        let req = try AuthorizationRequest.parse(uri)
        XCTAssertEqual(req.clientId, "https://verifier.example.com")
        XCTAssertEqual(req.requestUri, "https://verifier.example.com/request/123")
        XCTAssertNil(req.responseUri)
    }

    func testParseByValue() throws {
        let pd = #"{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"#
        let encodedPd = pd.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-123&response_mode=direct_post&presentation_definition=\(encodedPd)"
        let req = try AuthorizationRequest.parse(uri)
        XCTAssertEqual(req.clientId, "https://v.example.com")
        XCTAssertEqual(req.responseUri, "https://v.example.com/cb")
        XCTAssertEqual(req.nonce, "n-123")
        XCTAssertNotNil(req.presentationDefinition)
    }

    func testRejectHttpRequestUri() {
        let uri = "openid4vp://?client_id=https://v.example.com&request_uri=http://v.example.com/request"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue("\(error)".contains("HTTPS"))
        }
    }

    func testRejectMissingClientId() {
        let uri = "openid4vp://?request_uri=https://v.example.com/request"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri))
    }

    func testRejectNonDirectPostResponseMode() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=fragment&presentation_definition=%7B%7D"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue("\(error)".contains("direct_post"))
        }
    }

    func testRejectMissingQuery() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri))
    }

    func testRejectHttpResponseUri() {
        let pd = #"{"id":"pd-1","input_descriptors":[]}"#
        let encodedPd = pd.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=http://v.example.com/cb&nonce=n&response_mode=direct_post&presentation_definition=\(encodedPd)"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue("\(error)".contains("HTTPS"))
        }
    }

    func testParseDcqlQuery() throws {
        let dcql = #"{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}"#
        let encodedDcql = dcql.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post&dcql_query=\(encodedDcql)"
        let req = try AuthorizationRequest.parse(uri)
        XCTAssertNotNil(req.dcqlQuery)
        XCTAssertNil(req.presentationDefinition)
    }

    func testRejectBothPdAndDcql() {
        let pd = #"{"id":"pd-1","input_descriptors":[]}"#
        let dcql = #"{"credentials":[]}"#
        let encodedPd = pd.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let encodedDcql = dcql.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post&presentation_definition=\(encodedPd)&dcql_query=\(encodedDcql)"
        XCTAssertThrowsError(try AuthorizationRequest.parse(uri))
    }

    func testParseJsonRequestObject() throws {
        let json = #"{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n-1","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[]}}"#
        let req = try AuthorizationRequest.parseJson(json)
        XCTAssertEqual(req.clientId, "https://v.example.com")
        XCTAssertEqual(req.nonce, "n-1")
    }
}
