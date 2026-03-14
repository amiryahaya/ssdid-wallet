import Foundation

/// Resolves and caches issuer metadata from well-known endpoints.
final class IssuerMetadataResolver: @unchecked Sendable {

    private let session: URLSession
    private let lock = NSLock()
    private var cache: [String: IssuerMetadata] = [:]

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Resolves issuer metadata for the given issuer URL, using a cache.
    func resolve(issuerUrl: String) async throws -> IssuerMetadata {
        lock.lock()
        if let cached = cache[issuerUrl] {
            lock.unlock()
            return cached
        }
        lock.unlock()

        // Fetch credential issuer metadata
        let issuerMetaUrl = "\(issuerUrl.trimmingTrailingSlash())/.well-known/openid-credential-issuer"
        let issuerMetaJson = try await fetchJson(issuerMetaUrl)

        guard let credentialEndpoint = issuerMetaJson["credential_endpoint"] as? String else {
            throw OpenId4VciError.metadataError("Missing credential_endpoint")
        }

        var configs: [String: [String: Any]] = [:]
        if let supported = issuerMetaJson["credential_configurations_supported"] as? [String: [String: Any]] {
            configs = supported
        }

        // Determine authorization server
        let authServer = issuerMetaJson["authorization_server"] as? String ?? issuerUrl

        // Fetch OAuth authorization server metadata
        let authMetaUrl = "\(authServer.trimmingTrailingSlash())/.well-known/oauth-authorization-server"
        let authMetaJson = try await fetchJson(authMetaUrl)

        guard let tokenEndpoint = authMetaJson["token_endpoint"] as? String else {
            throw OpenId4VciError.metadataError("Missing token_endpoint")
        }

        let authorizationEndpoint = authMetaJson["authorization_endpoint"] as? String

        let metadata = IssuerMetadata(
            credentialIssuer: issuerUrl,
            credentialEndpoint: credentialEndpoint,
            credentialConfigurationsSupported: configs,
            tokenEndpoint: tokenEndpoint,
            authorizationEndpoint: authorizationEndpoint
        )

        lock.lock()
        cache[issuerUrl] = metadata
        lock.unlock()

        return metadata
    }

    /// Clears the metadata cache.
    func clearCache() {
        lock.lock()
        cache.removeAll()
        lock.unlock()
    }

    // MARK: - Private

    private func fetchJson(_ urlString: String) async throws -> [String: Any] {
        guard let url = URL(string: urlString) else {
            throw OpenId4VciError.transportError("Invalid URL: \(urlString)")
        }
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VciError.transportError("HTTP \(code) fetching \(urlString)")
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw OpenId4VciError.transportError("Invalid JSON from \(urlString)")
        }
        return json
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
