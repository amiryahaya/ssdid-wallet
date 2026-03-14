import XCTest
@testable import SsdidWallet

final class AuthorizationRequestTests: XCTestCase {

    func testParseValidByValueRequest() {
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=n-0S6_WzA2Mj"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&state=af0ifjsldkj"
            + "&presentation_definition=%7B%22id%22%3A%22req-1%22%2C%22input_descriptors%22%3A%5B%5D%7D"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success(let req):
            XCTAssertEqual(req.responseType, "vp_token")
            XCTAssertEqual(req.clientId, "https://verifier.example.com")
            XCTAssertEqual(req.nonce, "n-0S6_WzA2Mj")
            XCTAssertEqual(req.responseMode, "direct_post")
            XCTAssertEqual(req.responseUri, "https://verifier.example.com/response")
            XCTAssertEqual(req.state, "af0ifjsldkj")
            XCTAssertNotNil(req.presentationDefinition)
            XCTAssertNil(req.dcqlQuery)
            XCTAssertNil(req.requestUri)
        case .failure(let error):
            XCTFail("Expected success but got error: \(error)")
        }
    }

    func testParseByReferenceRequest() {
        let uri = "openid4vp://?client_id=https://verifier.example.com"
            + "&request_uri=https://verifier.example.com/request/abc123"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success(let req):
            XCTAssertEqual(req.clientId, "https://verifier.example.com")
            XCTAssertEqual(req.requestUri, "https://verifier.example.com/request/abc123")
        case .failure(let error):
            XCTFail("Expected success but got error: \(error)")
        }
    }

    func testRejectsMissingResponseType() {
        let uri = "openid4vp://?client_id=https://v.example.com&nonce=abc"
            + "&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for missing response_type")
        case .failure(let error):
            XCTAssertTrue(
                error.localizedDescription.contains("response_type"),
                "Error should mention response_type: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsMissingNonce() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&response_mode=direct_post&response_uri=https://v.example.com/r"
            + "&presentation_definition=%7B%7D"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for missing nonce")
        case .failure(let error):
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

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for missing client_id")
        case .failure(let error):
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

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for non-HTTPS response_uri")
        case .failure(let error):
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

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for ambiguous query")
        case .failure(let error):
            XCTAssertTrue(
                error.localizedDescription.contains("ambiguous"),
                "Error should mention ambiguous: \(error.localizedDescription)"
            )
        }
    }

    func testRejectsNeitherPresentationDefinitionNorDcqlQuery() {
        let uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com"
            + "&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success:
            XCTFail("Expected failure for missing query")
        case .failure(let error):
            XCTAssertTrue(
                error.localizedDescription.contains("No query"),
                "Error should mention missing query: \(error.localizedDescription)"
            )
        }
    }

    func testAcceptsDidAsClientId() {
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=did:web:verifier.example.com"
            + "&nonce=abc&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/r"
            + "&presentation_definition=%7B%7D"

        let result = AuthorizationRequest.parse(uri)

        switch result {
        case .success(let req):
            XCTAssertEqual(req.clientId, "did:web:verifier.example.com")
        case .failure(let error):
            XCTFail("Expected success but got error: \(error)")
        }
    }
}
