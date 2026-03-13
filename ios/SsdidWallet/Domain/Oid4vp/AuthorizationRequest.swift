import Foundation

struct AuthorizationRequest {
    let clientId: String
    let responseType: String?
    let responseMode: String?
    let responseUri: String?
    let nonce: String?
    let state: String?
    let presentationDefinition: String?
    let dcqlQuery: String?
    let requestUri: String?

    static func parse(_ uriString: String) -> Result<AuthorizationRequest, Error> {
        Result {
            guard let components = URLComponents(string: uriString) else {
                throw OpenId4VpError.missingClientId
            }

            let queryItems = components.queryItems ?? []

            func param(_ name: String) -> String? {
                queryItems.first(where: { $0.name == name })?.value
            }

            guard let clientId = param("client_id") else {
                throw OpenId4VpError.missingClientId
            }

            let requestUri = param("request_uri")

            // By-reference: only client_id and request_uri needed
            if let requestUri = requestUri {
                return AuthorizationRequest(
                    clientId: clientId,
                    responseType: nil,
                    responseMode: nil,
                    responseUri: nil,
                    nonce: nil,
                    state: nil,
                    presentationDefinition: nil,
                    dcqlQuery: nil,
                    requestUri: requestUri
                )
            }

            // By-value: validate all required parameters
            guard let responseType = param("response_type") else {
                throw OpenId4VpError.missingResponseType
            }
            guard responseType == "vp_token" else {
                throw OpenId4VpError.unsupportedResponseType(responseType)
            }

            guard let nonce = param("nonce") else {
                throw OpenId4VpError.missingNonce
            }

            let responseMode = param("response_mode") ?? "direct_post"
            guard responseMode == "direct_post" else {
                throw OpenId4VpError.unsupportedResponseMode(responseMode)
            }

            guard let responseUri = param("response_uri") else {
                throw OpenId4VpError.missingResponseUri
            }
            guard responseUri.hasPrefix("https://") else {
                throw OpenId4VpError.nonHttpsResponseUri(responseUri)
            }

            let presentationDefinition = param("presentation_definition")
            let dcqlQuery = param("dcql_query")

            if presentationDefinition != nil && dcqlQuery != nil {
                throw OpenId4VpError.ambiguousQuery
            }

            let state = param("state")

            // Validate client_id is HTTPS URL or DID
            try validateClientId(clientId)

            return AuthorizationRequest(
                clientId: clientId,
                responseType: responseType,
                responseMode: responseMode,
                responseUri: responseUri,
                nonce: nonce,
                state: state,
                presentationDefinition: presentationDefinition,
                dcqlQuery: dcqlQuery,
                requestUri: nil
            )
        }
    }

    private static func validateClientId(_ clientId: String) throws {
        let isHttps = clientId.hasPrefix("https://")
        let isDid = clientId.hasPrefix("did:")
        guard isHttps || isDid else {
            throw OpenId4VpError.invalidClientId(clientId)
        }
    }
}
