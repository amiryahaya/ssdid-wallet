import Foundation

enum SdJwtError: Error, LocalizedError {
    case emptyInput
    case invalidDisclosure
    case invalidJwt
    case verificationFailed(String)

    var errorDescription: String? {
        switch self {
        case .emptyInput: return "Empty SD-JWT input"
        case .invalidDisclosure: return "Invalid disclosure format"
        case .invalidJwt: return "Invalid JWT structure"
        case .verificationFailed(let reason): return "Verification failed: \(reason)"
        }
    }
}
