@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class AuthorizationRequestTests: XCTestCase {

    func testParseValidByValueRequest() throws {
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=n-0S6_WzA2Mj"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&state=af0ifjsldkj"
            + "&presentation_definition=%7B%22id%22%3A%22req-1%22%2C%22input_descriptors%22%3A%5B%5D%7D"

        let req = try AuthorizationRequest.parse(uri)

        XCTAssertEqual(req.responseType, "vp_token")
        XCTAssertEqual(req.clientId, "https://verifier.example.com")
        XCTAssertEqual(req.nonce, "n-0S6_WzA2Mj")
        XCTAssertEqual(req.responseMode, "direct_post")
        XCTAssertEqual(req.responseUri, "https://verifier.example.com/response")
        XCTAssertEqual(req.state, "af0ifjsldkj")
        XCTAssertNotNil(req.presentationDefinition)
        XCTAssertNil(req.dcqlQuery)
        XCTAssertNil(req.requestUri)
    }

    func testParseByReferenceRequest() throws {
        let uri = "openid4vp://?client_id=https://verifier.example.com"
            + "&request_uri=https://verifier.example.com/request/abc123"

        let req = try AuthorizationRequest.parse(uri)

        XCTAssertEqual(req.clientId, "https://verifier.example.com")
        XCTAssertEqual(req.requestUri, "https://verifier.example.com/request/abc123")
    }

    func testParseSucceedsWithoutResponseType() throws {
        // response_type is optional in by-value requests per current implementation
        let uri = "openid4vp://?client_id=https://v.example.com&nonce=abc"
            + "&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        let req = try AuthorizationRequest.parse(uri)
        XCTAssertNil(req.responseType, "response_type should be nil when not provided")
        XCTAssertEqual(req.clientId, "https://v.example.com")
    }

    func testRejectsMissingNonce() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("nonce"),
                "Error should mention nonce: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsMissingClientId() {
        let uri = "openid4vp://?response_type=vp_token&nonce=abc"
            + "&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("client_id"),
                "Error should mention client_id: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsNonHttpsResponseUri() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&nonce=abc&response_mode=direct_post&response_uri=http://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("response_uri"),
                "Error should mention response_uri: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsBothPresentationDefinitionAndDcqlQuery() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D&dcql_query=%7B%7D"

        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("both"),
                "Error should mention both: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsNeitherPresentationDefinitionNorDcqlQuery() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r"

        XCTAssertThrowsError(try AuthorizationRequest.parse(uri)) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("presentation_definition") || error.localizedDescription.contains("Must provide"),
                "Error should mention missing query: \(error.localizedDescription)"
            )
        }
    }

    func testAcceptsDidAsClientId() throws {
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=did:web:verifier.example.com"
            + "&nonce=abc&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/r"
            + "&presentation_definition=%7B%7D"

        let req = try AuthorizationRequest.parse(uri)

        XCTAssertEqual(req.clientId, "did:web:verifier.example.com")
    }
}
