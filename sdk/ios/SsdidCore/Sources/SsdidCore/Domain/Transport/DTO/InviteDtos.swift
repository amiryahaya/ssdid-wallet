import Foundation

/// Response from GET /api/invitations/token/{token}
public struct InvitationDetailsResponse: Codable {
    public     let tenantName: String
    public     let inviterName: String?
    public     let email: String
    public     let role: String
    public     let status: String
    public     let shortCode: String
    public     let message: String?
    public     let expiresAt: String
    public     let createdAt: String

    public init(
        tenantName: String,
        inviterName: String?,
        email: String,
        role: String,
        status: String,
        shortCode: String,
        message: String?,
        expiresAt: String,
        createdAt: String
    ) {
        self.tenantName = tenantName
        self.inviterName = inviterName
        self.email = email
        self.role = role
        self.status = status
        self.shortCode = shortCode
        self.message = message
        self.expiresAt = expiresAt
        self.createdAt = createdAt
    }


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
public struct AcceptWithWalletRequest: Codable {
    public     let credential: VerifiableCredential
    public     let email: String

    public init(credential: VerifiableCredential, email: String) {
        self.credential = credential
        self.email = email
    }
}

/// Response from accept-with-wallet
public struct AcceptWithWalletResponse: Codable {
    public     let sessionToken: String
    public     let did: String
    public     let serverDid: String
    public     let serverKeyId: String
    public     let serverSignature: String

    enum CodingKeys: String, CodingKey {
        case sessionToken = "session_token"
        case did
        case serverDid = "server_did"
        case serverKeyId = "server_key_id"
        case serverSignature = "server_signature"
    }
}
