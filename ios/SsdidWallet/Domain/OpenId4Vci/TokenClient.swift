import Foundation

/// Response from a token endpoint exchange.
struct TokenResponse: CustomStringConvertible {
    let accessToken: String
    let tokenType: String
    let cNonce: String?
    let cNonceExpiresIn: Int?

    var description: String {
        "TokenResponse(accessToken=REDACTED, tokenType=\(tokenType), cNonce=\(cNonce != nil ? "present" : "nil"))"
    }
}

/// Handles OAuth 2.0 token exchange for OpenID4VCI flows.
final class TokenClient: @unchecked Sendable {

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Exchanges a pre-authorized code for an access token.
    func exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = nil
    ) async throws -> TokenResponse {
        var params: [(String, String)] = [
            ("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code"),
            ("pre-authorized_code", preAuthorizedCode)
        ]
        if let txCode = txCode {
            params.append(("tx_code", txCode))
        }
        return try await postTokenRequest(tokenEndpoint: tokenEndpoint, params: params)
    }

    /// Exchanges an authorization code for an access token.
    func exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ) async throws -> TokenResponse {
        let params: [(String, String)] = [
            ("grant_type", "authorization_code"),
            ("code", code),
            ("code_verifier", codeVerifier),
            ("redirect_uri", redirectUri)
        ]
        return try await postTokenRequest(tokenEndpoint: tokenEndpoint, params: params)
    }

    // MARK: - Private

    private func postTokenRequest(
        tokenEndpoint: String,
        params: [(String, String)]
    ) async throws -> TokenResponse {
        guard let url = URL(string: tokenEndpoint) else {
            throw OpenId4VciError.transportError("Invalid token endpoint URL: \(tokenEndpoint)")
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

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OpenId4VciError.tokenError("Token request failed: HTTP \(code)")
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw OpenId4VciError.tokenError("Invalid token response JSON")
        }

        guard let accessToken = json["access_token"] as? String else {
            throw OpenId4VciError.tokenError("Missing access_token")
        }

        return TokenResponse(
            accessToken: accessToken,
            tokenType: json["token_type"] as? String ?? "Bearer",
            cNonce: json["c_nonce"] as? String,
            cNonceExpiresIn: json["c_nonce_expires_in"] as? Int
        )
    }
}
