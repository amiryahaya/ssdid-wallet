import Foundation

/// Drive API client for SSDID Drive registration, verification, and authentication.
public final class DriveApi: @unchecked Sendable {

    private let client: SsdidHttpClient
    private let baseURL: String

    public     init(client: SsdidHttpClient, baseURL: String) {
        self.client = client
        self.baseURL = baseURL
    }

    // MARK: - Registration

    public     func register(request: RegisterStartRequest) async throws -> RegisterStartResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/register",
            body: request,
            responseType: RegisterStartResponse.self
        )
    }

    public     func registerVerify(request: RegisterVerifyRequest) async throws -> RegisterVerifyResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/register/verify",
            body: request,
            responseType: RegisterVerifyResponse.self
        )
    }

    // MARK: - Authentication

    public     func authenticate(request: DriveAuthenticateRequest) async throws -> DriveAuthenticateResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/authenticate",
            body: request,
            responseType: DriveAuthenticateResponse.self
        )
    }

    // MARK: - Invitations

    public     func getInvitationByToken(_ token: String) async throws -> InvitationDetailsResponse {
        guard let encodedToken = token.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else {
            throw HttpError.invalidURL("Invalid token")
        }
        return try await client.get(
            url: "\(baseURL)/api/invitations/token/\(encodedToken)",
            responseType: InvitationDetailsResponse.self
        )
    }

    public     func acceptWithWallet(token: String, request: AcceptWithWalletRequest) async throws -> AcceptWithWalletResponse {
        guard let encodedToken = token.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else {
            throw HttpError.invalidURL("Invalid token")
        }
        return try await client.post(
            url: "\(baseURL)/api/invitations/token/\(encodedToken)/accept-with-wallet",
            body: request,
            responseType: AcceptWithWalletResponse.self
        )
    }
}
