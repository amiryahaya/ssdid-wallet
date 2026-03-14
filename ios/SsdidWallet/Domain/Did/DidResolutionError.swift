import Foundation

enum DidResolutionError: Error, LocalizedError {
    case unsupportedMethod(String)
    case invalidMultibase
    case dataTooShort
    case unsupportedCodec(Int)
    case invalidJwk
    case invalidDid(String)

    var errorDescription: String? {
        switch self {
        case .unsupportedMethod(let did): return "Unsupported DID method: \(did)"
        case .invalidMultibase: return "Expected multibase 'z' (base58btc) prefix"
        case .dataTooShort: return "Data too short for multicodec decoding"
        case .unsupportedCodec(let c): return "Unsupported multicodec: 0x\(String(c, radix: 16))"
        case .invalidJwk: return "Invalid Base64url-encoded JWK"
        case .invalidDid(let d): return "Invalid DID: \(d)"
        }
    }
}
