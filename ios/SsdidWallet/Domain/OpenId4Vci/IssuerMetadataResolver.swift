import Foundation

/// Protocol for fetching JSON from URLs, enabling test injection.
protocol MetadataFetcher: Sendable {
    func fetchJson(url: String) async throws -> [String: Any]
}

/// Default fetcher using URLSession.
private final class URLSessionMetadataFetcher: MetadataFetcher, @unchecked Sendable {
    private let session: URLSession
    init(session: URLSession) { self.session = session }

    func fetchJson(url urlString: String) async throws -> [String: Any] {
        guard let url = URL(string: urlString) else {
            throw OpenId4VciError.transportError("Invalid metadata URL")
        }
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VciError.transportError("Metadata fetch failed: HTTP \(code)")
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw OpenId4VciError.transportError("Invalid metadata JSON")
        }
        return json
    }
}

/// Resolves and caches issuer metadata from well-known endpoints.
actor IssuerMetadataResolver {

    private let fetcher: MetadataFetcher
    private var cache: [String: IssuerMetadata] = [:]
    private var inFlight: [String: Task<IssuerMetadata, Error>] = [:]

    init(session: URLSession = .shared) {
        self.fetcher = URLSessionMetadataFetcher(session: session)
    }

    init(fetcher: MetadataFetcher) {
        self.fetcher = fetcher
    }

    /// Resolves issuer metadata for the given issuer URL, using a cache.
    /// Uses in-flight deduplication to prevent cache stampede.
    func resolve(issuerUrl: String) async throws -> IssuerMetadata {
        if let cached = cache[issuerUrl] {
            return cached
        }

        // Deduplicate concurrent requests for the same issuer
        if let existing = inFlight[issuerUrl] {
            return try await existing.value
        }

        let task = Task<IssuerMetadata, Error> {
            // Fetch credential issuer metadata
            let issuerMetaUrl = "\(issuerUrl.trimmingTrailingSlash())/.well-known/openid-credential-issuer"
            let issuerMetaJson = try await fetcher.fetchJson(url: issuerMetaUrl)

            guard let credentialEndpoint = issuerMetaJson["credential_endpoint"] as? String else {
                throw OpenId4VciError.metadataError("Missing credential_endpoint")
            }

            var configs: [String: [String: Any]] = [:]
            if let supported = issuerMetaJson["credential_configurations_supported"] as? [String: [String: Any]] {
                configs = supported
            }

            // Determine authorization server — validate to prevent SSRF
            let authServer: String
            if let declaredServer = issuerMetaJson["authorization_server"] as? String {
                try validateUrl(declaredServer)
                authServer = declaredServer
            } else {
                authServer = issuerUrl
            }

            // Fetch OAuth authorization server metadata
            let authMetaUrl = "\(authServer.trimmingTrailingSlash())/.well-known/oauth-authorization-server"
            let authMetaJson = try await fetcher.fetchJson(url: authMetaUrl)

            guard let tokenEndpoint = authMetaJson["token_endpoint"] as? String else {
                throw OpenId4VciError.metadataError("Missing token_endpoint")
            }

            let authorizationEndpoint = authMetaJson["authorization_endpoint"] as? String

            return IssuerMetadata(
                credentialIssuer: issuerUrl,
                credentialEndpoint: credentialEndpoint,
                credentialConfigurationsSupported: configs,
                tokenEndpoint: tokenEndpoint,
                authorizationEndpoint: authorizationEndpoint
            )
        }

        inFlight[issuerUrl] = task
        do {
            let metadata = try await task.value
            cache[issuerUrl] = metadata
            inFlight.removeValue(forKey: issuerUrl)
            return metadata
        } catch {
            inFlight.removeValue(forKey: issuerUrl)
            throw error
        }
    }

    /// Clears the metadata cache.
    func clearCache() {
        cache.removeAll()
        inFlight.removeAll()
    }

    // MARK: - Private

    /// Validates a URL is HTTPS and not a loopback/private address (SSRF protection).
    private nonisolated func validateUrl(_ urlString: String) throws {
        guard let url = URL(string: urlString),
              let scheme = url.scheme?.lowercased(),
              scheme == "https",
              let host = url.host?.lowercased() else {
            throw OpenId4VciError.metadataError("Invalid or non-HTTPS URL")
        }
        let forbidden = ["localhost", "127.0.0.1", "[::1]", "0.0.0.0"]
        if forbidden.contains(host) || host.hasSuffix(".local") {
            throw OpenId4VciError.metadataError("Loopback/private hosts are not allowed")
        }
    }

}

private extension String {
    func trimmingTrailingSlash() -> String {
        if self.hasSuffix("/") {
            return String(self.dropLast())
        }
        return self
    }
}
