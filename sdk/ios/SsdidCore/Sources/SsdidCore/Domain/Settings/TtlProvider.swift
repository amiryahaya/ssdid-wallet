import Foundation

/// Provides the configured TTL for verification bundles, read directly
/// from UserDefaults so it can be used synchronously from any context.
/// Conforms to `BundleTtlProvider` for use with `VerificationOrchestrator`.
public final class TtlProvider: BundleTtlProvider {
    private let userDefaults: UserDefaults

    private static let key = "ssdid_bundle_ttl_days"
    private static let defaultDays = 7

    public     init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    /// Number of days a bundle is considered fresh. Falls back to 7 if unset.
    public     var ttlDays: Int {
        let days = userDefaults.integer(forKey: Self.key)
        return days > 0 ? days : Self.defaultDays
    }

    /// TTL expressed as a TimeInterval (seconds). Satisfies `BundleTtlProvider`.
    public     var bundleTtlSeconds: TimeInterval {
        TimeInterval(ttlDays) * 86400
    }

    /// TTL expressed as a TimeInterval (seconds). Convenience alias.
    public     var ttl: TimeInterval { bundleTtlSeconds }

    /// Returns true if the bundle identified by an ISO8601 `fetchedAt` timestamp has exceeded its TTL.
    public     func isExpired(fetchedAt: String) -> Bool {
        guard let fetched = ISO8601DateFormatter().date(from: fetchedAt) else { return true }
        return Date().timeIntervalSince(fetched) > ttl
    }

    /// Returns a ratio of age/TTL in [0, ∞). Values ≥ 1.0 indicate the bundle is expired.
    public     func freshnessRatio(fetchedAt: String) -> Double {
        guard let fetched = ISO8601DateFormatter().date(from: fetchedAt) else { return 1.0 }
        let age = Date().timeIntervalSince(fetched)
        guard ttl > 0 else { return 1.0 }
        return max(0, age / ttl)
    }
}
