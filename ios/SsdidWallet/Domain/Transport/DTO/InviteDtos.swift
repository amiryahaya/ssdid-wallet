import Foundation

/// Response from GET /api/invitations/token/{token}
struct InvitationDetailsResponse: Codable {
    let tenantName: String
    let inviterName: String?
    let email: String
    let role: String
    let status: String
    let shortCode: String
    let message: String?
    let expiresAt: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case tenantName = "tenant_name"
        case inviterName = "inviter_name"
        case email, role, status
        case shortCode = "short_code"
        case message
        case expiresAt = "expires_at"
        case createdAt = "created_at"
    }
}

/// Request body for POST /api/invitations/token/{token}/accept-with-wallet
struct AcceptWithWalletRequest: Codable {
    let credential: VerifiableCredential
    let email: String
}

/// Response from accept-with-wallet
struct AcceptWithWalletResponse: Codable {
    let sessionToken: String
    let did: String
    let serverDid: String
    let serverKeyId: String
    let serverSignature: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}
