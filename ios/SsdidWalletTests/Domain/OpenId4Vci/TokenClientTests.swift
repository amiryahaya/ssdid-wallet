import XCTest
@testable import SsdidWallet

final class TokenClientTests: XCTestCase {

    func testExchangePreAuthorizedCode() async throws {
        let mockTransport = MockTokenTransport(responseJson: """
        {"access_token":"at-123","token_type":"Bearer","c_nonce":"nonce-1","c_nonce_expires_in":300}
        """)

        let client = TokenClient(transport: mockTransport)
        let response = try await client.exchangePreAuthorizedCode(
            tokenEndpoint: "https://issuer.example.com/token",
            preAuthorizedCode: "pre-code-1"
        )

        XCTAssertEqual(response.accessToken, "at-123")
        XCTAssertEqual(response.tokenType, "Bearer")
        XCTAssertEqual(response.cNonce, "nonce-1")
        XCTAssertEqual(response.cNonceExpiresIn, 300)

        // Verify request parameters
        let body = mockTransport.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("grant_type=urn"))
        XCTAssertTrue(body.contains("pre-authorized_code=pre-code-1"))
    }

    func testExchangePreAuthorizedCodeWithTxCode() async throws {
        let mockTransport = MockTokenTransport(responseJson: """
        {"access_token":"at-456","token_type":"Bearer"}
        """)

        let client = TokenClient(transport: mockTransport)
        let response = try await client.exchangePreAuthorizedCode(
            tokenEndpoint: "https://issuer.example.com/token",
            preAuthorizedCode: "pre-code-2",
            txCode: "123456"
        )

        XCTAssertEqual(response.accessToken, "at-456")
        XCTAssertNil(response.cNonce)
        XCTAssertNil(response.cNonceExpiresIn)

        let body = mockTransport.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("tx_code=123456"))
    }

    func testExchangeAuthorizationCode() async throws {
        let mockTransport = MockTokenTransport(responseJson: """
        {"access_token":"at-789","token_type":"Bearer","c_nonce":"n-2"}
        """)

        let client = TokenClient(transport: mockTransport)
        let response = try await client.exchangeAuthorizationCode(
            tokenEndpoint: "https://issuer.example.com/token",
            code: "auth-code-1",
            codeVerifier: "verifier-1",
            redirectUri: "https://wallet.example.com/cb"
        )

        XCTAssertEqual(response.accessToken, "at-789")
        XCTAssertEqual(response.cNonce, "n-2")

        let body = mockTransport.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("grant_type=authorization_code"))
        XCTAssertTrue(body.contains("code=auth-code-1"))
        XCTAssertTrue(body.contains("code_verifier=verifier-1"))
        XCTAssertTrue(body.contains("redirect_uri="))
    }

    func testFailsOnHttpError() async {
        let mockTransport = MockTokenTransport(
            responseJson: """
            {"error":"invalid_grant"}
            """,
            statusCode: 400
        )

        let client = TokenClient(transport: mockTransport)
        do {
            _ = try await client.exchangePreAuthorizedCode(
                tokenEndpoint: "https://issuer.example.com/token",
                preAuthorizedCode: "bad-code"
            )
            XCTFail("Should have thrown on HTTP 400")
        } catch {
            XCTAssertTrue("\(error)".contains("400") || "\(error)".contains("invalid"))
        }
    }

    func testNilCNonceWhenNotInResponse() async throws {
        let mockTransport = MockTokenTransport(responseJson: """
        {"access_token":"at-simple","token_type":"DPoP"}
        """)

        let client = TokenClient(transport: mockTransport)
        let response = try await client.exchangePreAuthorizedCode(
            tokenEndpoint: "https://issuer.example.com/token",
            preAuthorizedCode: "code-1"
        )

        XCTAssertEqual(response.accessToken, "at-simple")
        XCTAssertEqual(response.tokenType, "DPoP")
        XCTAssertNil(response.cNonce)
        XCTAssertNil(response.cNonceExpiresIn)
    }
}

// MARK: - Mock

private final class MockTokenTransport: TokenTransport, @unchecked Sendable {
    let responseJson: String
    let statusCode: Int
    private(set) var lastRequestBody: String?
    private let lock = NSLock()

    init(responseJson: String, statusCode: Int = 200) {
        self.responseJson = responseJson
        self.statusCode = statusCode
    }

    func postForm(url: String, body: String) async throws -> String {
        lock.lock()
        lastRequestBody = body
        lock.unlock()

        if statusCode != 200 {
            throw NSError(
                domain: "TokenClient",
                code: statusCode,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(statusCode): \(responseJson)"]
            )
        }
        return responseJson
    }
}
