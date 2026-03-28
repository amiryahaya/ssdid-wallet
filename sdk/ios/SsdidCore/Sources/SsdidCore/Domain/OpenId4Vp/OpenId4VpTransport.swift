import Foundation

/// Handles HTTP transport for OpenID4VP flows using URLSession.
final class OpenId4VpTransport: @unchecked Sendable {

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Fetches a request object from the given URI.
    func fetchRequestObject(requestUri: String) async throws -> String {
        guard let url = URL(string: requestUri) else {
            throw OpenId4VpError.transportError("Invalid request_uri: \(requestUri)")
        }
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VpError.transportError("HTTP \(code)")
        }
        guard let body = String(data: data, encoding: .utf8), !body.isEmpty else {
            throw OpenId4VpError.transportError("Empty response body")
        }
        return body
    }

    /// Posts a VP response with form-encoded body to the response_uri.
    func postVpResponse(
        responseUri: String,
        vpToken: String,
        presentationSubmission: String,
        state: String?
    ) async throws {
        var params = [
            "vp_token": vpToken,
            "presentation_submission": presentationSubmission
        ]
        if let state = state {
            params["state"] = state
        }
        try await postForm(url: responseUri, params: params)
    }

    /// Posts an error response to the response_uri.
    func postError(responseUri: String, error: String, state: String?) async throws {
        var params = ["error": error]
        if let state = state {
            params["state"] = state
        }
        try await postForm(url: responseUri, params: params)
    }

    // MARK: - Private

    private func postForm(url urlString: String, params: [String: String]) async throws {
        guard let url = URL(string: urlString) else {
            throw OpenId4VpError.transportError("Invalid URL: \(urlString)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(
            "application/x-www-form-urlencoded",
            forHTTPHeaderField: "Content-Type"
        )

        let body = params.map { key, value in
            let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
            let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
            return "\(encodedKey)=\(encodedValue)"
        }.joined(separator: "&")

        request.httpBody = body.data(using: .utf8)

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VpError.transportError("HTTP \(code)")
        }
    }
}
