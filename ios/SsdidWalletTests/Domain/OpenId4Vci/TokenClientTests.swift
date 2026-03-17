import XCTest
@testable import SsdidWallet

final class TokenClientTests: XCTestCase {

    private var session: URLSession!

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        session = URLSession(configuration: config)
    }

    override func tearDown() {
        session = nil
        MockURLProtocol.reset()
        super.tearDown()
    }

    func testExchangePreAuthorizedCode() async throws {
        MockURLProtocol.mockResponseJson = """
        {"access_token":"at-123","token_type":"Bearer","c_nonce":"nonce-1","c_nonce_expires_in":300}
        """
        MockURLProtocol.mockStatusCode = 200

        let client = TokenClient(session: session)
        let response = try await client.exchangePreAuthorizedCode(
            tokenEndpoint: "https://issuer.example.com/token",
            preAuthorizedCode: "pre-code-1"
        )

        XCTAssertEqual(response.accessToken, "at-123")
        XCTAssertEqual(response.tokenType, "Bearer")
        XCTAssertEqual(response.cNonce, "nonce-1")
        XCTAssertEqual(response.cNonceExpiresIn, 300)

        // Verify request parameters
        let body = MockURLProtocol.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("grant_type=urn"))
        XCTAssertTrue(body.contains("pre-authorized_code=pre-code-1"))
    }

    func testExchangePreAuthorizedCodeWithTxCode() async throws {
        MockURLProtocol.mockResponseJson = """
        {"access_token":"at-456","token_type":"Bearer"}
        """
        MockURLProtocol.mockStatusCode = 200

        let client = TokenClient(session: session)
        let response = try await client.exchangePreAuthorizedCode(
            tokenEndpoint: "https://issuer.example.com/token",
            preAuthorizedCode: "pre-code-2",
            txCode: "123456"
        )

        XCTAssertEqual(response.accessToken, "at-456")
        XCTAssertNil(response.cNonce)
        XCTAssertNil(response.cNonceExpiresIn)

        let body = MockURLProtocol.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("tx_code=123456"))
    }

    func testExchangeAuthorizationCode() async throws {
        MockURLProtocol.mockResponseJson = """
        {"access_token":"at-789","token_type":"Bearer","c_nonce":"n-2"}
        """
        MockURLProtocol.mockStatusCode = 200

        let client = TokenClient(session: session)
        let response = try await client.exchangeAuthorizationCode(
            tokenEndpoint: "https://issuer.example.com/token",
            code: "auth-code-1",
            codeVerifier: "verifier-1",
            redirectUri: "https://wallet.example.com/cb"
        )

        XCTAssertEqual(response.accessToken, "at-789")
        XCTAssertEqual(response.cNonce, "n-2")

        let body = MockURLProtocol.lastRequestBody ?? ""
        XCTAssertTrue(body.contains("grant_type=authorization_code"))
        XCTAssertTrue(body.contains("code=auth-code-1"))
        XCTAssertTrue(body.contains("code_verifier=verifier-1"))
        XCTAssertTrue(body.contains("redirect_uri="))
    }

    func testFailsOnHttpError() async {
        MockURLProtocol.mockResponseJson = """
        {"error":"invalid_grant"}
        """
        MockURLProtocol.mockStatusCode = 400

        let client = TokenClient(session: session)
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
        MockURLProtocol.mockResponseJson = """
        {"access_token":"at-simple","token_type":"DPoP"}
        """
        MockURLProtocol.mockStatusCode = 200

        let client = TokenClient(session: session)
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

// MARK: - Mock URLProtocol

private final class MockURLProtocol: URLProtocol {
    static var mockResponseJson: String = "{}"
    static var mockStatusCode: Int = 200
    static var lastRequestBody: String?

    static func reset() {
        mockResponseJson = "{}"
        mockStatusCode = 200
        lastRequestBody = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        // Capture request body
        if let bodyData = request.httpBody ?? request.httpBodyStream?.readAll() {
            MockURLProtocol.lastRequestBody = String(data: bodyData, encoding: .utf8)
        }

        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: MockURLProtocol.mockStatusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        if let data = MockURLProtocol.mockResponseJson.data(using: .utf8) {
            client?.urlProtocol(self, didLoad: data)
        }
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private extension InputStream {
    func readAll() -> Data {
        open()
        defer { close() }
        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        while hasBytesAvailable {
            let bytesRead = read(buffer, maxLength: bufferSize)
            if bytesRead <= 0 { break }
            data.append(buffer, count: bytesRead)
        }
        return data
    }
}
