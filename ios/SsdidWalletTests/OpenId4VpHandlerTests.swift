@testable import SsdidCore
import XCTest
@testable import SsdidWallet

/// Mock SdJwtVcStore that returns pre-configured VCs for testing.
private final class MockSdJwtVcStore: SdJwtVcStore {
    var storedVcs: [StoredSdJwtVc] = []

    func listSdJwtVcs() async -> [StoredSdJwtVc] {
        storedVcs
    }
}

final class OpenId4VpHandlerTests: XCTestCase {

    private func makeHandler(storedVcs: [StoredSdJwtVc] = []) -> OpenId4VpHandler {
        let store = MockSdJwtVcStore()
        store.storedVcs = storedVcs
        return OpenId4VpHandler(
            transport: OpenId4VpTransport(),
            peMatcher: PresentationDefinitionMatcher(),
            dcqlMatcher: DcqlMatcher(),
            vcStore: store
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

    func testProcessRequestParsesAndMatchesPeCredential() async throws {
        let storedVc = makeStoredVc()
        let handler = makeHandler(storedVcs: [storedVc])

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

        let result = try await handler.processRequest(uri: uri)

        XCTAssertEqual(result.authRequest.clientId, "https://verifier.example.com")
        XCTAssertEqual(result.authRequest.nonce, "test-nonce")
        XCTAssertFalse(result.matches.isEmpty, "Should have at least one match")
        XCTAssertEqual(result.matches[0].credential.id, "vc-1")
        XCTAssertEqual(result.matches[0].descriptorId, "desc-1")
        XCTAssertNotNil(result.authRequest.presentationDefinition)
    }

    func testProcessRequestThrowsWhenNoMatch() async {
        let storedVc = makeStoredVc() // type is IdentityCredential, not DriverLicenseCredential
        let handler = makeHandler(storedVcs: [storedVc])

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

        do {
            _ = try await handler.processRequest(uri: uri)
            XCTFail("Should have thrown noMatchingCredentials")
        } catch let error as OpenId4VpError {
            if case .noMatchingCredentials = error {
                // expected
            } else {
                XCTFail("Expected noMatchingCredentials, got: \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func testProcessRequestThrowsWhenNoQuery() async {
        let handler = makeHandler()

        // URI with no presentation_definition and no dcql_query
        let uri = "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"

        do {
            _ = try await handler.processRequest(uri: uri)
            XCTFail("Should have thrown for missing query")
        } catch {
            XCTAssertTrue(
                error.localizedDescription.contains("presentation_definition")
                    || error.localizedDescription.contains("dcql_query")
                    || error.localizedDescription.contains("No query"),
                "Error should indicate no query: \(error.localizedDescription)"
            )
        }
    }

    func testProcessRequestWithDcqlQuery() async throws {
        let storedVc = makeStoredVc()
        let handler = makeHandler(storedVcs: [storedVc])

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

        let result = try await handler.processRequest(uri: uri)

        XCTAssertFalse(result.matches.isEmpty, "Should match via DCQL")
        XCTAssertEqual(result.matches[0].credential.id, "vc-1")
        XCTAssertNil(result.authRequest.presentationDefinition)
        XCTAssertNotNil(result.authRequest.dcqlQuery)
    }
}
