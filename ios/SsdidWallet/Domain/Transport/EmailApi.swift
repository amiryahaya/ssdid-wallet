import Foundation

/// Email verification API client for `wallet.ssdid.my`.
/// Uses its own encoder/decoder (camelCase) instead of SsdidHttpClient's (snake_case).
final class EmailApi: @unchecked Sendable {

    private let baseURL: String
    private let session: URLSession

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        return e
    }()

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()

    init(baseURL: String = "https://wallet.ssdid.my", session: URLSession = .shared) {
        self.baseURL = baseURL.hasSuffix("/") ? String(baseURL.dropLast()) : baseURL
        self.session = session
    }

    /// Convenience init that ignores the shared client (kept for call-site compatibility).
    init(client: SsdidHttpClient, baseURL: String = "https://wallet.ssdid.my") {
        self.baseURL = baseURL.hasSuffix("/") ? String(baseURL.dropLast()) : baseURL
        self.session = .shared
    }

    /// Send a verification code to the given email address.
    func sendCode(email: String, deviceId: String) async throws -> SendCodeResponse {
        return try await post(
            url: "\(baseURL)/api/email/verify/send",
            body: SendCodeRequest(email: email, deviceId: deviceId),
            responseType: SendCodeResponse.self
        )
    }

    /// Confirm the verification code.
    func confirmCode(email: String, code: String, deviceId: String) async throws -> ConfirmCodeResponse {
        return try await post(
            url: "\(baseURL)/api/email/verify/confirm",
            body: ConfirmCodeRequest(email: email, code: code, deviceId: deviceId),
            responseType: ConfirmCodeResponse.self
        )
    }

    // MARK: - Private

    private func post<Req: Encodable, Res: Decodable>(
        url: String, body: Req, responseType: Res.Type
    ) async throws -> Res {
        guard let requestURL = URL(string: url) else {
            throw HttpError.invalidURL(url)
        }

        var request = URLRequest(url: requestURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("SsdidWallet-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.httpBody = try encoder.encode(body)

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .timedOut {
            throw HttpError.timeout
        } catch {
            throw HttpError.networkError(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw HttpError.networkError(NSError(domain: "EmailApi", code: -1))
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw HttpError.requestFailed(statusCode: httpResponse.statusCode, message: message)
        }

        return try decoder.decode(responseType, from: data)
    }
}

// MARK: - Request / Response Models

struct SendCodeRequest: Encodable {
    let email: String
    let deviceId: String
}

struct SendCodeResponse: Decodable {
    let expiresIn: Int
}

struct ConfirmCodeRequest: Encodable {
    let email: String
    let code: String
    let deviceId: String
}

struct ConfirmCodeResponse: Decodable {
    let verified: Bool
}
