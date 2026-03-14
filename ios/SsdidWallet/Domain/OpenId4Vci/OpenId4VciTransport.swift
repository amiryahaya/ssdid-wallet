import Foundation

/// Handles HTTP transport for OpenID4VCI flows using URLSession.
final class OpenId4VciTransport: @unchecked Sendable {

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Fetches issuer metadata from the well-known endpoint.
    func fetchIssuerMetadata(issuerUrl: String) async throws -> String {
        let url = "\(issuerUrl.trimmingTrailingSlash())/.well-known/openid-credential-issuer"
        return try await get(url)
    }

    /// Fetches OAuth authorization server metadata from the well-known endpoint.
    func fetchAuthServerMetadata(authServerUrl: String) async throws -> String {
        let url = "\(authServerUrl.trimmingTrailingSlash())/.well-known/oauth-authorization-server"
        return try await get(url)
    }

    /// Posts a token request with form-encoded body.
    func postTokenRequest(tokenEndpoint: String, params: [(String, String)]) async throws -> String {
        guard let url = URL(string: tokenEndpoint) else {
            throw OpenId4VciError.transportError("Invalid URL: \(tokenEndpoint)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = params.map { key, value in
            let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
            let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
            return "\(encodedKey)=\(encodedValue)"
        }.joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        return try await execute(request)
    }

    /// Posts a credential request with JSON body and Bearer token authorization.
    func postCredentialRequest(
        credentialEndpoint: String,
        accessToken: String,
        requestBody: String
    ) async throws -> String {
        guard let url = URL(string: credentialEndpoint) else {
            throw OpenId4VciError.transportError("Invalid URL: \(credentialEndpoint)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = requestBody.data(using: .utf8)

        return try await execute(request)
    }

    /// Posts a deferred credential request.
    func postDeferredRequest(
        deferredEndpoint: String,
        accessToken: String,
        transactionId: String
    ) async throws -> String {
        let jsonBody: [String: Any] = ["transaction_id": transactionId]
        let bodyData = try JSONSerialization.data(withJSONObject: jsonBody)

        guard let url = URL(string: deferredEndpoint) else {
            throw OpenId4VciError.transportError("Invalid URL: \(deferredEndpoint)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = bodyData

        return try await execute(request)
    }

    /// Fetches a credential offer from a URI.
    func fetchCredentialOffer(offerUri: String) async throws -> String {
        return try await get(offerUri)
    }

    // MARK: - Private

    private func get(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else {
            throw OpenId4VciError.transportError("Invalid URL: \(urlString)")
        }
        let request = URLRequest(url: url)
        return try await execute(request)
    }

    private func execute(_ request: URLRequest) async throws -> String {
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VciError.transportError("HTTP \(code)")
        }
        guard let body = String(data: data, encoding: .utf8), !body.isEmpty else {
            throw OpenId4VciError.transportError("Empty response body")
        }
        return body
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
