import Foundation

enum OpenId4VpError: Error, LocalizedError {
    case missingClientId
    case missingResponseType
    case missingNonce
    case missingResponseUri
    case unsupportedResponseType(String)
    case unsupportedResponseMode(String)
    case nonHttpsResponseUri(String)
    case invalidClientId(String)
    case ambiguousQuery
    case noQuery

    var errorDescription: String? {
        switch self {
        case .missingClientId:
            return "Missing required parameter: client_id"
        case .missingResponseType:
            return "Missing required parameter: response_type"
        case .missingNonce:
            return "Missing required parameter: nonce"
        case .missingResponseUri:
            return "Missing required parameter: response_uri"
        case .unsupportedResponseType(let v):
            return "Unsupported response_type: \(v)"
        case .unsupportedResponseMode(let v):
            return "Unsupported response_mode: \(v)"
        case .nonHttpsResponseUri(let v):
            return "response_uri must be HTTPS: \(v)"
        case .invalidClientId(let v):
            return "client_id must be HTTPS URL or DID: \(v)"
        case .ambiguousQuery:
            return "Request is ambiguous: both presentation_definition and dcql_query present"
        case .noQuery:
            return "No query in authorization request"
        }
    }
}
