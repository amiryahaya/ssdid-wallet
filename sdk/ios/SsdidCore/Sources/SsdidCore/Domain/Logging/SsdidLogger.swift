import Foundation

/// Logging protocol for the SSDID SDK.
/// Consumers can provide their own implementation (e.g., Sentry, os.log, print).
public protocol SsdidLogger {
    func info(_ category: String, _ message: String, data: [String: String])
    func warning(_ category: String, _ message: String, data: [String: String])
    func error(_ category: String, _ message: String, error: Error?, data: [String: String])
}

public extension SsdidLogger {
    func info(_ category: String, _ message: String, data: [String: String] = [:]) {
        info(category, message, data: data)
    }
    func warning(_ category: String, _ message: String, data: [String: String] = [:]) {
        warning(category, message, data: data)
    }
    func error(_ category: String, _ message: String, error: Error? = nil, data: [String: String] = [:]) {
        self.error(category, message, error: error, data: data)
    }
}

/// No-op logger that discards all messages. Used as the default.
public struct NoOpLogger: SsdidLogger {
    public init() {}
    public func info(_ category: String, _ message: String, data: [String: String]) {}
    public func warning(_ category: String, _ message: String, data: [String: String]) {}
    public func error(_ category: String, _ message: String, error: Error?, data: [String: String]) {}
}
