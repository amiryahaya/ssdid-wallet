import Foundation

public enum VerificationStatus: Hashable {
    case verified
    case verifiedOffline
    case failed
    case degraded
}

public enum VerificationSource: Hashable {
    case online
    case offline
}

public enum CheckType: Hashable {
    case signature
    case expiry
    case revocation
    case bundleFreshness
}

public enum CheckStatus: Hashable {
    case pass
    case fail
    case unknown
}

public struct VerificationCheck: Hashable {
    public let type: CheckType
    public let status: CheckStatus
    public let message: String

    public init(type: CheckType, status: CheckStatus, message: String) {
        self.type = type
        self.status = status
        self.message = message
    }
}

public struct UnifiedVerificationResult: Hashable {
    public let status: VerificationStatus
    public let checks: [VerificationCheck]
    public let source: VerificationSource
    public var bundleAge: TimeInterval? = nil

    public init(status: VerificationStatus, checks: [VerificationCheck], source: VerificationSource, bundleAge: TimeInterval? = nil) {
        self.status = status
        self.checks = checks
        self.source = source
        self.bundleAge = bundleAge
    }
}
