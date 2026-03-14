import XCTest
@testable import SsdidWallet

final class IssuerMetadataResolverTests: XCTestCase {

    func testResolveIssuerMetadata() async throws {
        let issuerMeta = """
        {
            "credential_endpoint": "https://issuer.example.com/credential",
            "credential_configurations_supported": {
                "UnivDegree": {"format": "vc+sd-jwt"}
            }
        }
        """
        let authMeta = """
        {
            "token_endpoint": "https://issuer.example.com/token",
            "authorization_endpoint": "https://issuer.example.com/authorize"
        }
        """

        let resolver = IssuerMetadataResolver(
            fetcher: MockMetadataFetcher(responses: [
                ".well-known/openid-credential-issuer": issuerMeta,
                ".well-known/oauth-authorization-server": authMeta
            ])
        )

        let meta = try await resolver.resolve(issuerUrl: "https://issuer.example.com")
        XCTAssertEqual(meta.credentialEndpoint, "https://issuer.example.com/credential")
        XCTAssertEqual(meta.tokenEndpoint, "https://issuer.example.com/token")
        XCTAssertEqual(meta.authorizationEndpoint, "https://issuer.example.com/authorize")
        XCTAssertTrue(meta.credentialConfigurationsSupported.keys.contains("UnivDegree"))
    }

    func testCachesResolvedMetadata() async throws {
        let fetcher = MockMetadataFetcher(responses: [
            ".well-known/openid-credential-issuer": """
            {"credential_endpoint":"https://iss.example.com/credential","credential_configurations_supported":{}}
            """,
            ".well-known/oauth-authorization-server": """
            {"token_endpoint":"https://iss.example.com/token"}
            """
        ])

        let resolver = IssuerMetadataResolver(fetcher: fetcher)
        _ = try await resolver.resolve(issuerUrl: "https://iss.example.com")
        let fetchCountAfterFirst = fetcher.fetchCount

        // Second call should use cache
        _ = try await resolver.resolve(issuerUrl: "https://iss.example.com")
        XCTAssertEqual(fetcher.fetchCount, fetchCountAfterFirst, "Second resolve should use cache")
    }

    func testFailsOnMissingCredentialEndpoint() async {
        let fetcher = MockMetadataFetcher(responses: [
            ".well-known/openid-credential-issuer": """
            {"credential_configurations_supported":{}}
            """,
            ".well-known/oauth-authorization-server": """
            {"token_endpoint":"https://iss.example.com/token"}
            """
        ])

        let resolver = IssuerMetadataResolver(fetcher: fetcher)
        do {
            _ = try await resolver.resolve(issuerUrl: "https://iss.example.com")
            XCTFail("Should have thrown for missing credential_endpoint")
        } catch {
            XCTAssertTrue("\(error)".contains("credential_endpoint"))
        }
    }

    func testRejectsLoopbackAuthorizationServer() async {
        let fetcher = MockMetadataFetcher(responses: [
            ".well-known/openid-credential-issuer": """
            {
                "credential_endpoint": "https://issuer.example.com/credential",
                "credential_configurations_supported": {},
                "authorization_server": "https://localhost/evil"
            }
            """,
            ".well-known/oauth-authorization-server": """
            {"token_endpoint": "https://localhost/token"}
            """
        ])

        let resolver = IssuerMetadataResolver(fetcher: fetcher)
        do {
            _ = try await resolver.resolve(issuerUrl: "https://issuer.example.com")
            XCTFail("Should have rejected loopback authorization_server")
        } catch {
            XCTAssertTrue("\(error)".lowercased().contains("loopback") || "\(error)".lowercased().contains("not allowed"))
        }
    }

    func testFailsOnFetchError() async {
        let fetcher = MockMetadataFetcher(responses: [:])
        let resolver = IssuerMetadataResolver(fetcher: fetcher)
        do {
            _ = try await resolver.resolve(issuerUrl: "https://iss.example.com")
            XCTFail("Should have thrown on fetch error")
        } catch {
            // Expected
        }
    }
}

// MARK: - Mock

private final class MockMetadataFetcher: MetadataFetcher, @unchecked Sendable {
    let responses: [String: String]
    private(set) var fetchCount = 0
    private let lock = NSLock()

    init(responses: [String: String]) {
        self.responses = responses
    }

    func fetchJson(url: String) async throws -> [String: Any] {
        lock.lock()
        fetchCount += 1
        lock.unlock()

        // Match the URL suffix against response keys
        for (key, value) in responses {
            if url.contains(key) {
                guard let data = value.data(using: .utf8),
                      let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw NSError(domain: "MockFetcher", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"])
                }
                return json
            }
        }

        throw NSError(domain: "MockFetcher", code: 404, userInfo: [NSLocalizedDescriptionKey: "No mock response for \(url)"])
    }
}
