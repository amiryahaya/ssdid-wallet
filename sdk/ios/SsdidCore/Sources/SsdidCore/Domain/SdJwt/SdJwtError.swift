import Foundation

public enum SdJwtError: Error, LocalizedError {
    case emptyInput
    case invalidDisclosure
    case invalidJwt
    case verificationFailed(String)
    case unsupportedAlgorithm(String)
    case serializationFailed

    public var errorDescription: String? {
        switch self {
        case .emptyInput: return "Empty SD-JWT input"
        case .invalidDisclosure: return "Invalid disclosure format"
        case .invalidJwt: return "Invalid JWT structure"
        case .verificationFailed(let reason): return "Verification failed: \(reason)"
        case .unsupportedAlgorithm(let alg): return "Unsupported hash algorithm: \(alg)"
        case .serializationFailed: return "JSON serialization failed"
        }
    }
}
