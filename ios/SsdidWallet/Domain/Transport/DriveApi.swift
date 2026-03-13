import Foundation

/// Drive API client for SSDID Drive registration, verification, and authentication.
final class DriveApi {

    private let client: SsdidHttpClient
    private let baseURL: String

    init(client: SsdidHttpClient, baseURL: String) {
        self.client = client
        self.baseURL = baseURL
    }

    // MARK: - Registration

    func register(request: RegisterStartRequest) async throws -> RegisterStartResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/register",
            body: request,
            responseType: RegisterStartResponse.self
        )
    }

    func registerVerify(request: RegisterVerifyRequest) async throws -> RegisterVerifyResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/register/verify",
            body: request,
            responseType: RegisterVerifyResponse.self
        )
    }

    // MARK: - Authentication

    func authenticate(request: DriveAuthenticateRequest) async throws -> DriveAuthenticateResponse {
        return try await client.post(
            url: "\(baseURL)/api/auth/ssdid/authenticate",
            body: request,
            responseType: DriveAuthenticateResponse.self
        )
    }

    // MARK: - Invitations

    func getInvitationByToken(_ token: String) async throws -> InvitationDetailsResponse {
        return try await client.get(
            url: "\(baseURL)/api/invitations/token/\(token)",
            responseType: InvitationDetailsResponse.self
        )
    }

    func acceptWithWallet(token: String, request: AcceptWithWalletRequest) async throws -> AcceptWithWalletResponse {
        return try await client.post(
            url: "\(baseURL)/api/invitations/token/\(token)/accept-with-wallet",
            body: request,
            responseType: AcceptWithWalletResponse.self
        )
    }
}
