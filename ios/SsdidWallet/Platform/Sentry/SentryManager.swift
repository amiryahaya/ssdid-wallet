import Foundation
import Sentry

/// Configures and manages Sentry crash reporting, performance monitoring,
/// and breadcrumbs with PII scrubbing for SSDID Wallet.
enum SentryManager {

    private static let dsn = "https://514c7a9677b4ee79f9986e4a9649bd32@o4507469191380992.ingest.de.sentry.io/4511026471632976"

    /// Call once at app launch, before any other initialization.
    static func start() {
        SentrySDK.start { options in
            options.dsn = dsn
            options.environment = Self.environment

            // Performance monitoring
            options.tracesSampleRate = 1.0
            options.enableAutoPerformanceTracing = true
            options.enableUIViewControllerTracing = true
            options.enableNetworkTracking = true
            options.enableFileIOTracing = true
            options.enableCoreDataTracing = true
            options.enableAppHangTracking = true
            options.appHangTimeoutInterval = 2

            // Continuous profiling (Sentry 9.x API)
            options.configureProfiling = { profiling in
                profiling.lifecycle = .trace
            }

            // Crash & session tracking
            options.enableCrashHandler = true
            options.enableAutoSessionTracking = true
            options.sessionTrackingIntervalMillis = 30_000

            // Breadcrumbs
            options.enableAutoBreadcrumbTracking = true
            options.enableNetworkBreadcrumbs = true
            options.enableSwizzling = true
            options.maxBreadcrumbs = 100

            // Screenshots & view hierarchy on crash
            options.attachScreenshot = true
            options.attachViewHierarchy = true

            // PII scrubbing — strip DID, keys, tokens from all events
            options.beforeSend = { event in
                return Self.scrubPII(from: event)
            }

            // Also scrub breadcrumb data
            options.beforeBreadcrumb = { breadcrumb in
                return Self.scrubBreadcrumb(breadcrumb)
            }

            #if DEBUG
            options.debug = true
            options.diagnosticLevel = .warning
            #endif
        }
    }

    // MARK: - PII Scrubbing

    /// Patterns that indicate PII in SSDID context.
    private static let piiPatterns: [(label: String, regex: NSRegularExpression)] = {
        let patterns: [(String, String)] = [
            ("did", #"did:ssdid:[A-Za-z0-9_-]+"#),
            ("base64key", #"[A-Za-z0-9+/]{40,}={0,2}"#),
            ("hexkey", #"(?:0x)?[0-9a-fA-F]{64,}"#),
            ("bearer", #"Bearer\s+[A-Za-z0-9._~+/=-]+"#),
            ("session_token", #"session[_-]?[Tt]oken[\"':\s]*[A-Za-z0-9._~+/=-]+"#),
            ("multibase", #"u[A-Za-z0-9_-]{20,}"#),
        ]
        return patterns.compactMap { label, pattern in
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
            return (label, regex)
        }
    }()

    /// Keys whose values should always be redacted.
    private static let sensitiveKeys: Set<String> = [
        "publicKey", "public_key", "publickey",
        "privateKey", "private_key", "privatekey",
        "secretKey", "secret_key", "secretkey",
        "signature", "sig",
        "did", "keyId", "key_id",
        "passphrase", "password", "token",
        "sessionToken", "session_token",
        "authorization", "Authorization",
        "mnemonic", "seed", "backup_data",
    ]

    private static func scrubPII(from event: Event) -> Event? {
        // Scrub exception values
        if let exceptions = event.exceptions {
            for exception in exceptions {
                if let value = exception.value {
                    exception.value = redactString(value)
                }
            }
        }

        // Scrub message
        if let message = event.message?.formatted {
            event.message = SentryMessage(formatted: redactString(message))
        }

        // Scrub extra context
        if let extra = event.extra {
            event.extra = scrubDictionary(extra)
        }

        // Scrub tags
        if var tags = event.tags {
            for (key, value) in tags {
                if sensitiveKeys.contains(key) {
                    tags[key] = "[Redacted]"
                } else {
                    tags[key] = redactString(value)
                }
            }
            event.tags = tags
        }

        // Scrub user — keep ID for issue grouping but strip PII
        if let user = event.user {
            user.email = nil
            user.username = nil
            user.ipAddress = "{{auto}}"
            if let userId = user.userId {
                user.userId = redactString(userId)
            }
        }

        // Scrub breadcrumbs
        event.breadcrumbs = event.breadcrumbs?.map { scrubBreadcrumb($0) }

        return event
    }

    private static func scrubBreadcrumb(_ breadcrumb: Breadcrumb) -> Breadcrumb {
        if let data = breadcrumb.data {
            breadcrumb.data = scrubDictionary(data)
        }
        if let message = breadcrumb.message {
            breadcrumb.message = redactString(message)
        }
        return breadcrumb
    }

    private static func scrubDictionary(_ dict: [String: Any]) -> [String: Any] {
        var result = [String: Any]()
        for (key, value) in dict {
            if sensitiveKeys.contains(key) {
                result[key] = "[Redacted]"
            } else if let str = value as? String {
                result[key] = redactString(str)
            } else if let nested = value as? [String: Any] {
                result[key] = scrubDictionary(nested)
            } else {
                result[key] = value
            }
        }
        return result
    }

    /// Replace PII patterns in a string with [Redacted].
    private static func redactString(_ input: String) -> String {
        var result = input
        for (_, regex) in piiPatterns {
            let range = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, range: range, withTemplate: "[Redacted]")
        }
        return result
    }

    // MARK: - Helpers

    private static var environment: String {
        #if DEBUG
        return "debug"
        #else
        return "production"
        #endif
    }
}
