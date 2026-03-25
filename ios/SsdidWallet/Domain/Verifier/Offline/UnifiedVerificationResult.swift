import Foundation

enum VerificationStatus {
    case verified
    case verifiedOffline
    case failed
    case degraded
}

enum VerificationSource {
    case online
    case offline
}

enum CheckType {
    case signature
    case expiry
    case revocation
    case bundleFreshness
}

enum CheckStatus {
    case pass
    case fail
    case unknown
}

struct VerificationCheck {
    let type: CheckType
    let status: CheckStatus
    let message: String
}

struct UnifiedVerificationResult {
    let status: VerificationStatus
    let checks: [VerificationCheck]
    let source: VerificationSource
    var bundleAge: TimeInterval? = nil
}
