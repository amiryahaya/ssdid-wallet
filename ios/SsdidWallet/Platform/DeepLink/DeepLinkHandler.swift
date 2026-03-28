import Foundation
import SsdidCore

/// Represents a parsed deep link action from an ssdid:// URL.
enum DeepLinkAction: Equatable {
    case register(serverUrl: String, serverDid: String?)
    case authenticate(serverUrl: String, callbackUrl: String, sessionId: String?, requestedClaims: String?, acceptedAlgorithms: String?, state: String?)
    case sign(serverUrl: String, sessionToken: String)
    case credentialOffer(issuerUrl: String, offerId: String)
    case login(serverUrl: String, serviceName: String?, challengeId: String?, callbackUrl: String, requestedClaims: String?, inviteCode: String?, state: String?)
    case invite(serverUrl: String, token: String, callbackUrl: String, state: String?)
    case openid4vp(rawUri: String)
    case openidCredentialOffer(offerData: String)
}

/// Errors specific to deep link parsing.
enum DeepLinkError: Error, LocalizedError {
    case invalidScheme(String)
    case invalidHost(String)
    case missingRequiredParameter(String)
    case invalidURL(String)
    case unsafeURL(String)

    var errorDescription: String? {
        switch self {
        case .invalidScheme(let scheme):
            return "Invalid URL scheme: \(scheme). Expected 'ssdid', 'openid4vp', or 'openid-credential-offer'"
        case .invalidHost(let host):
            return "Unknown deep link action: \(host)"
        case .missingRequiredParameter(let param):
            return "Missing required parameter: \(param)"
        case .invalidURL(let url):
            return "Invalid deep link URL: \(url)"
        case .unsafeURL(let reason):
            return "Unsafe URL: \(reason)"
        }
    }
}

/// Parses ssdid:// deep link URLs into typed actions.
final class DeepLinkHandler {

    private let urlValidator: UrlValidator

    init(urlValidator: UrlValidator = UrlValidator()) {
        self.urlValidator = urlValidator
    }

    /// Parses a URL into a DeepLinkAction.
    func parse(url: URL) throws -> DeepLinkAction {
        guard let scheme = url.scheme?.lowercased() else {
            throw DeepLinkError.invalidScheme("nil")
        }

        switch scheme {
        case "ssdid":
            return try parseSsdid(url: url)
        case "openid4vp":
            return .openid4vp(rawUri: url.absoluteString)
        case "openid-credential-offer":
            return try parseCredentialOffer(url: url)
        default:
            throw DeepLinkError.invalidScheme(scheme)
        }
    }

    // MARK: - Scheme Parsers

    /// Parses an ssdid:// URL into a DeepLinkAction.
    private func parseSsdid(url: URL) throws -> DeepLinkAction {
        guard let host = url.host else {
            throw DeepLinkError.invalidURL(url.absoluteString)
        }

        let params = parseQueryParameters(url: url)

        switch host {
        case "register":
            guard let serverUrl = params["server_url"] else {
                throw DeepLinkError.missingRequiredParameter("server_url")
            }
            try urlValidator.validate(urlString: serverUrl)
            let serverDid: String? = if let rawDid = params["server_did"] {
                (try? Did.validate(rawDid))?.value
            } else {
                nil
            }
            return .register(serverUrl: serverUrl, serverDid: serverDid)

        case "authenticate":
            guard let serverUrl = params["server_url"] else {
                throw DeepLinkError.missingRequiredParameter("server_url")
            }
            try urlValidator.validate(urlString: serverUrl)
            let callbackUrl = params["callback_url"] ?? ""
            if !callbackUrl.isEmpty {
                try Self.validateCallbackUrl(callbackUrl)
            }
            return .authenticate(
                serverUrl: serverUrl,
                callbackUrl: callbackUrl,
                sessionId: params["session_id"],
                requestedClaims: params["requested_claims"],
                acceptedAlgorithms: params["accepted_algorithms"],
                state: params["state"]
            )

        case "sign":
            guard let serverUrl = params["server_url"] else {
                throw DeepLinkError.missingRequiredParameter("server_url")
            }
            guard let sessionToken = params["session_token"] else {
                throw DeepLinkError.missingRequiredParameter("session_token")
            }
            try urlValidator.validate(urlString: serverUrl)
            return .sign(serverUrl: serverUrl, sessionToken: sessionToken)

        case "credential-offer":
            guard let issuerUrl = params["issuer_url"] else {
                throw DeepLinkError.missingRequiredParameter("issuer_url")
            }
            guard let offerId = params["offer_id"] else {
                throw DeepLinkError.missingRequiredParameter("offer_id")
            }
            try urlValidator.validate(urlString: issuerUrl)
            return .credentialOffer(issuerUrl: issuerUrl, offerId: offerId)

        case "login":
            guard let serverUrl = params["server_url"] else {
                throw DeepLinkError.missingRequiredParameter("server_url")
            }
            try urlValidator.validate(urlString: serverUrl)
            let callbackUrl = params["callback_url"] ?? ""
            if !callbackUrl.isEmpty {
                try Self.validateCallbackUrl(callbackUrl)
            }
            return .login(
                serverUrl: serverUrl,
                serviceName: params["service_name"],
                challengeId: params["challenge_id"],
                callbackUrl: callbackUrl,
                requestedClaims: params["requested_claims"],
                inviteCode: params["invite_code"],
                state: params["state"]
            )

        case "invite":
            guard let serverUrl = params["server_url"] else {
                throw DeepLinkError.missingRequiredParameter("server_url")
            }
            guard let token = params["token"] else {
                throw DeepLinkError.missingRequiredParameter("token")
            }
            try urlValidator.validate(urlString: serverUrl)
            let callbackUrl = params["callback_url"] ?? ""
            if !callbackUrl.isEmpty {
                try Self.validateCallbackUrl(callbackUrl)
            }
            return .invite(serverUrl: serverUrl, token: token, callbackUrl: callbackUrl, state: params["state"])

        default:
            throw DeepLinkError.invalidHost(host)
        }
    }

    /// Parses an openid-credential-offer:// URL into a DeepLinkAction.
    private func parseCredentialOffer(url: URL) throws -> DeepLinkAction {
        let params = parseQueryParameters(url: url)
        if let offerJson = params["credential_offer"] {
            return .openidCredentialOffer(offerData: offerJson)
        }
        if let offerUri = params["credential_offer_uri"] {
            guard offerUri.hasPrefix("https://") else {
                throw DeepLinkError.unsafeURL("credential_offer_uri must be HTTPS")
            }
            return .openidCredentialOffer(offerData: offerUri)
        }
        throw DeepLinkError.missingRequiredParameter("credential_offer or credential_offer_uri")
    }

    /// Parses a URL string into a DeepLinkAction.
    func parse(urlString: String) throws -> DeepLinkAction {
        guard let url = URL(string: urlString) else {
            throw DeepLinkError.invalidURL(urlString)
        }
        return try parse(url: url)
    }

    // MARK: - Callback Validation

    /// Validates that a callback URL uses a safe scheme (no javascript:, data:, file:, etc.).
    static func validateCallbackUrl(_ urlString: String) throws {
        guard let url = URL(string: urlString), let scheme = url.scheme?.lowercased() else {
            throw DeepLinkError.unsafeURL("Invalid callback URL: \(urlString)")
        }
        let dangerousSchemes: Set<String> = ["javascript", "data", "file", "blob", "vbscript"]
        if dangerousSchemes.contains(scheme) {
            throw DeepLinkError.unsafeURL("Dangerous scheme in callback URL: \(scheme)")
        }
        if scheme == "http" {
            throw DeepLinkError.unsafeURL("Plain HTTP not allowed in callback URL: \(urlString)")
        }
        // HTTPS must have a host
        if scheme == "https" {
            guard let host = url.host, !host.isEmpty else {
                throw DeepLinkError.unsafeURL("HTTPS callback URL must have a host")
            }
        }
        // Custom schemes must match pattern (e.g., ssdid-drive)
        guard scheme.range(of: "^[a-z][a-z0-9+\\-.]*$", options: .regularExpression) != nil else {
            throw DeepLinkError.unsafeURL("Invalid scheme in callback URL: \(scheme)")
        }
    }

    // MARK: - Private

    private func parseQueryParameters(url: URL) -> [String: String] {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems else {
            return [:]
        }

        var params: [String: String] = [:]
        for item in queryItems {
            if let value = item.value {
                params[item.name] = value
            }
        }
        return params
    }
}
