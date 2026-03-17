import XCTest
@testable import SsdidWallet

final class OpenId4VpTransportTests: XCTestCase {

    func testParseJsonRequestExtractsFields() {
        let json = """
        {
            "client_id": "https://verifier.example.com",
            "response_type": "vp_token",
            "nonce": "test-nonce",
            "response_mode": "direct_post",
            "response_uri": "https://verifier.example.com/response",
            "state": "state-123",
            "presentation_definition": {"id": "req-1", "input_descriptors": []}
        }
        """

        let result = Result { try AuthorizationRequest.parseJson(json) }
        switch result {
        case .success(let req):
            XCTAssertEqual(req.clientId, "https://verifier.example.com")
            XCTAssertEqual(req.nonce, "test-nonce")
            XCTAssertEqual(req.responseType, "vp_token")
            XCTAssertNotNil(req.presentationDefinition)
            XCTAssertEqual(req.state, "state-123")
        case .failure(let error):
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testParseJsonRequestFailsWithMissingClientId() {
        let json = #"{"response_type": "vp_token"}"#
        let result = Result { try AuthorizationRequest.parseJson(json) }
        switch result {
        case .success:
            XCTFail("Should have failed")
        case .failure:
            break // expected
        }
    }

    func testParseJsonRequestHandlesDcqlQuery() {
        let json = """
        {
            "client_id": "https://verifier.example.com",
            "response_type": "vp_token",
            "nonce": "abc",
            "response_mode": "direct_post",
            "response_uri": "https://verifier.example.com/r",
            "dcql_query": {"credentials": []}
        }
        """

        let result = Result { try AuthorizationRequest.parseJson(json) }
        switch result {
        case .success(let req):
            XCTAssertNotNil(req.dcqlQuery)
            XCTAssertNil(req.presentationDefinition)
        case .failure(let error):
            XCTFail("Unexpected error: \(error)")
        }
    }
}
