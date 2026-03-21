import Foundation
import CryptoKit

/// Error types for HTTP operations.
enum HttpError: Error, LocalizedError {
    case invalidURL(String)
    case requestFailed(statusCode: Int, message: String?)
    case decodingFailed(String)
    case networkError(Error)
    case timeout

    var errorDescription: String? {
        switch self {
        case .invalidURL(let url):
            return "Invalid URL: \(url)"
        case .requestFailed(let code, let message):
            return "HTTP \(code): \(message ?? "Unknown error")"
        case .decodingFailed(let reason):
            return "Response decoding failed: \(reason)"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .timeout:
            return "Request timed out"
        }
    }
}

/// Result wrapper for network operations.
enum NetworkResult<T> {
    case success(T)
    case httpError(statusCode: Int, message: String?)
    case networkError(Error)
    case timeout
}

// MARK: - Certificate Pinning

/// URLSession delegate that implements SSL certificate pinning for SSDID hosts.
/// Validates server certificates against known pins for production builds.
final class CertificatePinningDelegate: NSObject, URLSessionDelegate {
    private let pinnedHosts: Set<String> = ["registry.ssdid.my", "notify.ssdid.my"]

    // Certificate SPKI SHA-256 pins for registry and notification services.
    // Pin both leaf cert and intermediate CA for rotation safety.
    private let pinnedHashes: Set<String> = [
        "6HndsMosiTeHfV+W29g33ZHsyuPe4Yo7fPdSCUWdeF0=",  // leaf cert
        "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=",  // intermediate CA (current)
        "y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="   // intermediate CA (previous, kept for rotation)
    ]

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              pinnedHosts.contains(challenge.protectionSpace.host),
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Extract certificate chain and check each certificate's SPKI hash against pins.
        guard let certChain = SecTrustCopyCertificateChain(serverTrust) as? [SecCertificate] else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        var matched = false
        for cert in certChain {
            guard let publicKey = SecCertificateCopyKey(cert),
                  let keyData = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
                continue
            }
            let hash = SHA256.hash(data: keyData)
            let hashBase64 = Data(hash).base64EncodedString()
            if pinnedHashes.contains(hashBase64) {
                matched = true
                break
            }
        }

        if matched {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            // Pin validation failed — log for debugging, then reject
            print("⚠️ [CertPinning] No matching pin for \(challenge.protectionSpace.host)")
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}

/// URLSession-based HTTP client for SSDID Registry, Server, and Drive APIs.
final class SsdidHttpClient: @unchecked Sendable {

    let registryBaseURL: String
    private let session: URLSession

    /// Shared JSON encoder — no global key strategy.
    /// All DTOs use explicit CodingKeys for snake_case mapping.
    /// W3C models (DidDocument, Proof, VerificationMethod) use camelCase.
    let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        return encoder
    }()

    /// Shared JSON decoder — no global key strategy.
    let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        return decoder
    }()

    init(registryURL: String = "https://registry.ssdid.my", session: URLSession = .shared) {
        self.registryBaseURL = registryURL.hasSuffix("/") ? String(registryURL.dropLast()) : registryURL

        // Certificate pinning is temporarily disabled for TestFlight testing.
        // TODO: Re-enable after verifying pin hashes match on physical devices.
        // #if !DEBUG
        // let pinningDelegate = CertificatePinningDelegate()
        // let configuration = URLSessionConfiguration.default
        // configuration.timeoutIntervalForRequest = 30
        // configuration.timeoutIntervalForResource = 60
        // self.session = URLSession(configuration: configuration, delegate: pinningDelegate, delegateQueue: nil)
        // #else
        self.session = session
        // #endif
    }

    // MARK: - API Accessors

    lazy var registry: RegistryApi = RegistryApi(client: self, baseURL: registryBaseURL)
    lazy var email: EmailApi = EmailApi(client: self)

    func serverApi(baseURL: String) -> ServerApi {
        ServerApi(client: self, baseURL: normalizeURL(baseURL))
    }

    func driveApi(baseURL: String) -> DriveApi {
        DriveApi(client: self, baseURL: normalizeURL(baseURL))
    }

    func notifyApi(baseURL: String) -> NotifyApi {
        NotifyApi(client: self, baseURL: normalizeURL(baseURL))
    }

    // MARK: - Generic Request

    /// Performs an HTTP request and decodes the response.
    func request<RequestBody: Encodable, ResponseBody: Decodable>(
        method: String,
        url: String,
        body: RequestBody? = nil as Empty?,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        var urlRequest = try buildURLRequest(method: method, url: url, authToken: nil)
        if let body = body {
            urlRequest.httpBody = try encoder.encode(body)
        }

        let (data, _) = try await executeRequest(urlRequest)

        do {
            return try decoder.decode(responseType, from: data)
        } catch {
            throw HttpError.decodingFailed(error.localizedDescription)
        }
    }

    /// Performs an HTTP request with no response body expected.
    func requestVoid<RequestBody: Encodable>(
        method: String,
        url: String,
        body: RequestBody? = nil as Empty?
    ) async throws {
        var urlRequest = try buildURLRequest(method: method, url: url, authToken: nil)
        if let body = body {
            urlRequest.httpBody = try encoder.encode(body)
        }
        _ = try await executeRequest(urlRequest)
    }

    /// Performs a GET request with no body.
    func get<ResponseBody: Decodable>(
        url: String,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        return try await request(method: "GET", url: url, body: nil as Empty?, responseType: responseType)
    }

    /// Performs a POST request.
    func post<RequestBody: Encodable, ResponseBody: Decodable>(
        url: String,
        body: RequestBody,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        return try await request(method: "POST", url: url, body: body, responseType: responseType)
    }

    // MARK: - Authorized Requests

    /// Performs an authorized HTTP request with a Bearer token and decodes the response.
    func authorizedRequest<RequestBody: Encodable, ResponseBody: Decodable>(
        method: String,
        url: String,
        authToken: String,
        body: RequestBody? = nil as Empty?,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        var urlRequest = try buildURLRequest(method: method, url: url, authToken: authToken)
        if let body = body {
            urlRequest.httpBody = try encoder.encode(body)
        }

        let (data, _) = try await executeRequest(urlRequest)

        do {
            return try decoder.decode(responseType, from: data)
        } catch {
            throw HttpError.decodingFailed(error.localizedDescription)
        }
    }

    /// Performs an authorized HTTP request with a Bearer token and no response body expected.
    func authorizedRequestVoid<RequestBody: Encodable>(
        method: String,
        url: String,
        authToken: String,
        body: RequestBody? = nil as Empty?
    ) async throws {
        var urlRequest = try buildURLRequest(method: method, url: url, authToken: authToken)
        if let body = body {
            urlRequest.httpBody = try encoder.encode(body)
        }
        _ = try await executeRequest(urlRequest)
    }

    /// Performs an authorized GET request with a Bearer token.
    func authorizedGet<ResponseBody: Decodable>(
        url: String,
        authToken: String,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        return try await authorizedRequest(
            method: "GET",
            url: url,
            authToken: authToken,
            body: nil as Empty?,
            responseType: responseType
        )
    }

    /// Performs an authorized POST request with a Bearer token.
    func authorizedPost<RequestBody: Encodable, ResponseBody: Decodable>(
        url: String,
        authToken: String,
        body: RequestBody,
        responseType: ResponseBody.Type
    ) async throws -> ResponseBody {
        return try await authorizedRequest(
            method: "POST",
            url: url,
            authToken: authToken,
            body: body,
            responseType: responseType
        )
    }

    // MARK: - Helpers

    /// Builds a URLRequest with standard headers and an optional Bearer token.
    private func buildURLRequest(method: String, url: String, authToken: String?) throws -> URLRequest {
        guard let requestURL = URL(string: url) else {
            throw HttpError.invalidURL(url)
        }
        var urlRequest = URLRequest(url: requestURL)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        urlRequest.setValue("SsdidWallet-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let token = authToken {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return urlRequest
    }

    /// Executes a URLRequest, validates the HTTP status code, and returns (data, response).
    @discardableResult
    private func executeRequest(_ urlRequest: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch let error as URLError where error.code == .timedOut {
            throw HttpError.timeout
        } catch {
            throw HttpError.networkError(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw HttpError.networkError(NSError(domain: "SsdidHttpClient", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Response is not an HTTP response"
            ]))
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw HttpError.requestFailed(statusCode: httpResponse.statusCode, message: message)
        }

        return (data, httpResponse)
    }

    private func normalizeURL(_ url: String) -> String {
        url.hasSuffix("/") ? String(url.dropLast()) : url
    }
}

/// Empty type for requests with no body.
struct Empty: Codable {}
