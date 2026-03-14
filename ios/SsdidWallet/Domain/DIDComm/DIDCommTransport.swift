import Foundation

/// Error types for DIDComm transport operations.
enum DIDCommTransportError: Error, LocalizedError {
    case invalidURL(String)
    case httpError(statusCode: Int, message: String?)
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL(let url):
            return "Invalid DIDComm service endpoint URL: \(url)"
        case .httpError(let code, let message):
            return "DIDComm transport HTTP \(code): \(message ?? "Unknown error")"
        case .networkError(let error):
            return "DIDComm transport network error: \(error.localizedDescription)"
        }
    }
}

/// URLSession-based HTTP transport for DIDComm v2 encrypted messages.
final class DIDCommTransport {

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Sends a packed (encrypted) DIDComm message to the given service endpoint via HTTP POST.
    ///
    /// - Parameters:
    ///   - packed: The encrypted message bytes.
    ///   - serviceEndpoint: The recipient's DIDComm service endpoint URL.
    func send(packed: Data, serviceEndpoint: String) async throws {
        guard let url = URL(string: serviceEndpoint) else {
            throw DIDCommTransportError.invalidURL(serviceEndpoint)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/didcomm-encrypted+json", forHTTPHeaderField: "Content-Type")
        request.setValue("SsdidWallet-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.httpBody = packed

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw DIDCommTransportError.networkError(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw DIDCommTransportError.networkError(
                NSError(domain: "DIDCommTransport", code: -1, userInfo: [
                    NSLocalizedDescriptionKey: "Response is not an HTTP response"
                ])
            )
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw DIDCommTransportError.httpError(statusCode: httpResponse.statusCode, message: message)
        }
    }
}
