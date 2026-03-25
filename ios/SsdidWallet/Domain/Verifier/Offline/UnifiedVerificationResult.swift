import Foundation

enum VerificationStatus: Hashable {
    case verified
    case verifiedOffline
    case failed
    case degraded
}

enum VerificationSource: Hashable {
    case online
    case offline
}

enum CheckType: Hashable {
    case signature
    case expiry
    case revocation
    case bundleFreshness
}

enum CheckStatus: Hashable {
    case pass
    case fail
    case unknown
}

struct VerificationCheck: Hashable {
    let type: CheckType
    let status: CheckStatus
    let message: String
}

struct UnifiedVerificationResult: Hashable {
    let status: VerificationStatus
    let checks: [VerificationCheck]
    let source: VerificationSource
    var bundleAge: TimeInterval? = nil
}
