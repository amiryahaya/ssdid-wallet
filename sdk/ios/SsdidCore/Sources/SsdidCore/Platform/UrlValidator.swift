import Foundation

/// Errors for URL validation.
public enum UrlValidationError: Error, LocalizedError {
    case invalidURL(String)
    case httpNotAllowed
    case privateIPNotAllowed(String)
    case nonStandardPort(Int)
    case emptyHost

    public     var errorDescription: String? {
        switch self {
        case .invalidURL(let url):
            return "Invalid URL: \(url)"
        case .httpNotAllowed:
            return "Only HTTPS URLs are allowed"
        case .privateIPNotAllowed(let ip):
            return "Private/reserved IP addresses are not allowed: \(ip)"
        case .nonStandardPort(let port):
            return "Non-standard port not allowed: \(port)"
        case .emptyHost:
            return "URL must have a host"
        }
    }
}

/// Validates URLs for safety: HTTPS only, no private IPs, standard ports.
public final class UrlValidator {

    public init() {}

    /// Standard allowed ports for HTTPS.
    private let allowedPorts: Set<Int> = [443, 8443]

    /// Validates that a URL string is safe to connect to.
    public     func validate(urlString: String) throws {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw UrlValidationError.invalidURL(urlString)
        }

        #if DEBUG
        // Allow localhost HTTP in debug builds for local development
        if let host = components.host, (host == "localhost" || host == "127.0.0.1") {
            return
        }
        #endif

        // HTTPS only
        guard components.scheme?.lowercased() == "https" else {
            throw UrlValidationError.httpNotAllowed
        }

        // Must have a host
        guard let host = components.host, !host.isEmpty else {
            throw UrlValidationError.emptyHost
        }

        // No private IP addresses
        if isPrivateIP(host) {
            throw UrlValidationError.privateIPNotAllowed(host)
        }

        // Port validation: allow standard HTTPS ports or no port specified
        if let port = components.port, !allowedPorts.contains(port) {
            throw UrlValidationError.nonStandardPort(port)
        }
    }

    /// Returns true if the host string appears to be a private/reserved IP address.
    public     func isPrivateIP(_ host: String) -> Bool {
        // Loopback
        if host == "localhost" || host == "127.0.0.1" || host == "::1" {
            return true
        }

        // IPv4 private ranges
        let parts = host.split(separator: ".").compactMap { UInt8($0) }
        if parts.count == 4 {
            let a = parts[0], b = parts[1]

            // 10.0.0.0/8
            if a == 10 { return true }

            // 172.16.0.0/12
            if a == 172 && (16...31).contains(b) { return true }

            // 192.168.0.0/16
            if a == 192 && b == 168 { return true }

            // 169.254.0.0/16 (link-local)
            if a == 169 && b == 254 { return true }

            // 0.0.0.0
            if a == 0 && b == 0 && parts[2] == 0 && parts[3] == 0 { return true }

            // 100.64.0.0/10 (CGNAT)
            if a == 100 && (64...127).contains(b) { return true }
        }

        // IPv6 private ranges (simplified check)
        let lowered = host.lowercased()
        if lowered.hasPrefix("fc") || lowered.hasPrefix("fd") { return true } // Unique local
        if lowered.hasPrefix("fe80") { return true } // Link-local

        return false
    }
}
