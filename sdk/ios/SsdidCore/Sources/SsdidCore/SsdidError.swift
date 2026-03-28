import Foundation

/// Unified error type for the SSDID SDK.
public enum SsdidError: Error {
    case networkError(Error)
    case timeout(String)
    case serverError(statusCode: Int, body: String?)
    case unsupportedAlgorithm(String)
    case signingFailed(String)
    case verificationFailed(String)
    case storageError(Error)
    case identityNotFound(String)
    case credentialNotFound(String)
    case didResolutionFailed(did: String, reason: String)
    case issuanceFailed(String)
    case presentationFailed(String)
    case noMatchingCredentials(String)
    case recoveryFailed(String)
    case rotationFailed(String)
}
