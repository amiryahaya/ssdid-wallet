import Foundation

/// Service/Server API client for registration, authentication, and transaction signing.
public final class ServerApi: @unchecked Sendable {

    private let client: SsdidHttpClient
    private let baseURL: String

    public     init(client: SsdidHttpClient, baseURL: String) {
        self.client = client
        self.baseURL = baseURL
    }

    // MARK: - Registration

    public     func registerStart(request: RegisterStartRequest) async throws -> RegisterStartResponse {
        return try await client.post(
            url: "\(baseURL)/api/register",
            body: request,
            responseType: RegisterStartResponse.self
        )
    }

    public     func registerVerify(request: RegisterVerifyRequest) async throws -> RegisterVerifyResponse {
        return try await client.post(
            url: "\(baseURL)/api/register/verify",
            body: request,
            responseType: RegisterVerifyResponse.self
        )
    }

    // MARK: - Authentication

    public     func authenticate(request: AuthenticateRequest) async throws -> AuthenticateResponse {
        return try await client.post(
            url: "\(baseURL)/api/authenticate",
            body: request,
            responseType: AuthenticateResponse.self
        )
    }

    public     func getAuthChallenge() async throws -> AuthChallengeResponse {
        return try await client.get(
            url: "\(baseURL)/api/auth/challenge",
            responseType: AuthChallengeResponse.self
        )
    }

    public     func verifyAuth(request: AuthVerifyRequest) async throws -> AuthVerifyResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/verify",
            body: request,
            responseType: AuthVerifyResponse.self
        )
    }

    // MARK: - Transaction

    public     func requestChallenge(request: TxChallengeRequest) async throws -> TxChallengeResponse {
        return try await client.post(
            url: "\(baseURL)/api/transaction/challenge",
            body: request,
            responseType: TxChallengeResponse.self
        )
    }

    public     func submitTransaction(request: TxSubmitRequest) async throws -> TxSubmitResponse {
        return try await client.post(
            url: "\(baseURL)/api/transaction/submit",
            body: request,
            responseType: TxSubmitResponse.self
        )
    }
}
