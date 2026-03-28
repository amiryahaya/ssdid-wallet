import Foundation

/// Parsed OpenID4VP authorization request parameters.
public struct AuthorizationRequest: Equatable {
    public let clientId: String
    public let requestUri: String?
    public let responseUri: String?
    public let nonce: String?
    public let state: String?
    public let responseType: String?
    public let responseMode: String?
    public let presentationDefinition: [String: Any]?
    public let dcqlQuery: [String: Any]?

    public static func == (lhs: AuthorizationRequest, rhs: AuthorizationRequest) -> Bool {
        lhs.clientId == rhs.clientId
            && lhs.requestUri == rhs.requestUri
            && lhs.responseUri == rhs.responseUri
            && lhs.nonce == rhs.nonce
            && lhs.state == rhs.state
            && lhs.responseType == rhs.responseType
            && lhs.responseMode == rhs.responseMode
    }

    /// Parses an authorization request from a URI string (query-parameter form).
    static func parse(_ uriString: String) throws -> AuthorizationRequest {
        guard let components = URLComponents(string: uriString) else {
            throw OpenId4VpError.invalidRequest("Cannot parse URI: \(uriString)")
        }
        let items = components.queryItems ?? []
        let params = Dictionary(items.compactMap { item in
            item.value.map { (item.name, $0) }
        }, uniquingKeysWith: { _, last in last })

        guard let clientId = params["client_id"] else {
            throw OpenId4VpError.invalidRequest("Missing required parameter: client_id")
        }

        if let requestUri = params["request_uri"] {
            guard requestUri.hasPrefix("https://") else {
                throw OpenId4VpError.invalidRequest("request_uri must be HTTPS: \(requestUri)")
            }
            return AuthorizationRequest(
                clientId: clientId,
                requestUri: requestUri,
                responseUri: nil,
                nonce: nil,
                state: nil,
                responseType: nil,
                responseMode: nil,
                presentationDefinition: nil,
                dcqlQuery: nil
            )
        }

        let responseMode = params["response_mode"]
        guard responseMode == "direct_post" else {
            throw OpenId4VpError.invalidRequest(
                "response_mode must be direct_post, got: \(responseMode ?? "nil")"
            )
        }
        guard let responseUri = params["response_uri"] else {
            throw OpenId4VpError.invalidRequest("Missing required parameter: response_uri")
        }
        guard responseUri.hasPrefix("https://") else {
            throw OpenId4VpError.invalidRequest("response_uri must be HTTPS: \(responseUri)")
        }
        guard let nonce = params["nonce"] else {
            throw OpenId4VpError.invalidRequest("Missing required parameter: nonce")
        }

        let pd = try params["presentation_definition"].flatMap { raw -> [String: Any]? in
            guard let data = raw.data(using: .utf8),
                  let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }
            return obj
        }
        let dcql = try params["dcql_query"].flatMap { raw -> [String: Any]? in
            guard let data = raw.data(using: .utf8),
                  let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }
            return obj
        }

        guard pd != nil || dcql != nil else {
            throw OpenId4VpError.invalidRequest("Must provide presentation_definition or dcql_query")
        }
        guard pd == nil || dcql == nil else {
            throw OpenId4VpError.invalidRequest("Cannot provide both presentation_definition and dcql_query")
        }

        return AuthorizationRequest(
            clientId: clientId,
            requestUri: nil,
            responseUri: responseUri,
            nonce: nonce,
            state: params["state"],
            responseType: params["response_type"],
            responseMode: responseMode,
            presentationDefinition: pd,
            dcqlQuery: dcql
        )
    }

    /// Parses an authorization request from a JSON string (request object form).
    static func parseJson(_ jsonString: String) throws -> AuthorizationRequest {
        guard let data = jsonString.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw OpenId4VpError.invalidRequest("Invalid JSON")
        }

        guard let clientId = obj["client_id"] as? String else {
            throw OpenId4VpError.invalidRequest("Missing client_id")
        }

        let responseMode = obj["response_mode"] as? String
        guard responseMode == "direct_post" else {
            throw OpenId4VpError.invalidRequest(
                "response_mode must be direct_post, got: \(responseMode ?? "nil")"
            )
        }
        guard let responseUri = obj["response_uri"] as? String else {
            throw OpenId4VpError.invalidRequest("Missing response_uri")
        }
        guard responseUri.hasPrefix("https://") else {
            throw OpenId4VpError.invalidRequest("response_uri must be HTTPS: \(responseUri)")
        }
        guard let nonce = obj["nonce"] as? String else {
            throw OpenId4VpError.invalidRequest("Missing nonce")
        }

        let pd = obj["presentation_definition"] as? [String: Any]
        let dcql = obj["dcql_query"] as? [String: Any]

        guard pd != nil || dcql != nil else {
            throw OpenId4VpError.invalidRequest("Must provide presentation_definition or dcql_query")
        }

        return AuthorizationRequest(
            clientId: clientId,
            requestUri: nil,
            responseUri: responseUri,
            nonce: nonce,
            state: obj["state"] as? String,
            responseType: nil,
            responseMode: responseMode,
            presentationDefinition: pd,
            dcqlQuery: dcql
        )
    }
}
