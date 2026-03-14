import XCTest
@testable import SsdidWallet

final class OpenId4VpHandlerTests: XCTestCase {

    private func makeHandler() -> OpenId4VpHandler {
        OpenId4VpHandler(
            transport: OpenId4VpTransport(),
            peMatcher: PresentationDefinitionMatcher(),
            dcqlMatcher: DcqlMatcher(),
            vpTokenBuilder: VpTokenBuilder()
        )
    }

    private func makePresentationDefinitionJson(vctFilter: String, requiredClaims: [String]) -> String {
        let fields = requiredClaims.map { claim in
            "{\"path\":[\"$.\(claim)\"]}"
        }
        let vctField = "{\"path\":[\"$.vct\"],\"filter\":{\"const\":\"\(vctFilter)\"}}"
        let allFields = [vctField] + fields
        return """
        {"id":"test-pd","input_descriptors":[{"id":"desc-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[\(allFields.joined(separator: ","))]}}]}
        """
    }

    private func makeStoredVc(
        id: String = "vc-1",
        type: String = "IdentityCredential",
        claims: [String: String] = ["given_name": "Alice", "family_name": "Smith"]
    ) -> StoredSdJwtVc {
        StoredSdJwtVc(
            id: id,
            compact: "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsInZjdCI6IklkZW50aXR5Q3JlZGVudGlhbCJ9.sig~",
            issuer: "https://issuer.example.com",
            subject: "did:ssdid:test",
            type: type,
            claims: claims,
            disclosableClaims: Array(claims.keys),
            issuedAt: 1700000000
        )
    }

    // MARK: - Tests

    func testProcessRequestParsesAndMatchesPeCredential() throws {
        let handler = makeHandler()

        let pdJson = makePresentationDefinitionJson(
            vctFilter: "IdentityCredential",
            requiredClaims: ["given_name"]
        )
        let encodedPd = pdJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!

        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&presentation_definition=\(encodedPd)"

        let storedVc = makeStoredVc()
        let result = try handler.processRequest(uri: uri, storedVcs: [storedVc])

        XCTAssertEqual(result.authRequest.clientId, "https://verifier.example.com")
        XCTAssertEqual(result.authRequest.nonce, "test-nonce")
        XCTAssertFalse(result.matchResults.isEmpty, "Should have at least one match")
        XCTAssertEqual(result.matchResults[0].credentialId, "vc-1")
        XCTAssertEqual(result.matchResults[0].descriptorId, "desc-1")
        XCTAssertFalse(result.query.descriptors.isEmpty)
    }

    func testProcessRequestThrowsWhenNoMatch() throws {
        let handler = makeHandler()

        let pdJson = makePresentationDefinitionJson(
            vctFilter: "DriverLicenseCredential",
            requiredClaims: ["license_number"]
        )
        let encodedPd = pdJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!

        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&presentation_definition=\(encodedPd)"

        let storedVc = makeStoredVc() // type is IdentityCredential, not DriverLicenseCredential
        let result = try handler.processRequest(uri: uri, storedVcs: [storedVc])

        XCTAssertTrue(result.matchResults.isEmpty, "Should have no matches for mismatched credential type")
    }

    func testProcessRequestThrowsWhenNoQuery() {
        let handler = makeHandler()

        // URI with no presentation_definition and no dcql_query
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"

        XCTAssertThrowsError(try handler.processRequest(uri: uri, storedVcs: [])) { error in
            XCTAssertTrue(
                error.localizedDescription.contains("No query"),
                "Error should indicate no query: \(error.localizedDescription)"
            )
        }
    }

    func testDeclineRequestDoesNotThrow() {
        let handler = makeHandler()

        // A by-value request without response_uri should not throw (just returns)
        let request = AuthorizationRequest(
            clientId: "https://verifier.example.com",
            responseType: "vp_token",
            responseMode: "direct_post",
            responseUri: nil,
            nonce: "nonce",
            state: nil,
            presentationDefinition: nil,
            dcqlQuery: nil,
            requestUri: nil
        )

        XCTAssertNoThrow(try handler.declineRequest(authRequest: request))
    }

    func testProcessRequestWithDcqlQuery() throws {
        let handler = makeHandler()

        let dcqlJson = """
        {"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]},"claims":[{"path":["given_name"]}]}]}
        """
        let encodedDcql = dcqlJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!

        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&dcql_query=\(encodedDcql)"

        let storedVc = makeStoredVc()
        let result = try handler.processRequest(uri: uri, storedVcs: [storedVc])

        XCTAssertFalse(result.matchResults.isEmpty, "Should match via DCQL")
        XCTAssertEqual(result.matchResults[0].credentialId, "vc-1")
        XCTAssertNil(result.authRequest.presentationDefinition)
        XCTAssertNotNil(result.authRequest.dcqlQuery)
    }
}
