import Foundation

/// API client for the SSDID Notify push notification relay service.
/// Mirrors the pattern of ServerApi and RegistryApi — takes SsdidHttpClient and a base URL.
final class NotifyApi {

    private let client: SsdidHttpClient
    private let baseURL: String

    init(client: SsdidHttpClient, baseURL: String) {
        self.client = client
        self.baseURL = baseURL
    }

    // MARK: - Inbox

    /// POST /api/v1/inbox/register
    /// Registers a new inbox with the given APNs devices.
    /// Returns the inbox_id and inbox_secret that must be stored securely.
    func registerInbox(request: InboxRegisterRequest) async throws -> InboxRegisterResponse {
        return try await client.post(
            url: "\(baseURL)/api/v1/inbox/register",
            body: request,
            responseType: InboxRegisterResponse.self
        )
    }

    /// PUT /api/v1/inbox/devices
    /// Updates the device tokens associated with the inbox.
    func updateDevices(inboxSecret: String, request: InboxUpdateDevicesRequest) async throws {
        try await client.authorizedRequestVoid(
            method: "PUT",
            url: "\(baseURL)/api/v1/inbox/devices",
            authToken: inboxSecret,
            body: request
        )
    }

    // MARK: - Mailbox

    /// POST /api/v1/mailbox/create
    /// Creates a per-identity mailbox for correlation-free notifications.
    func createMailbox(inboxSecret: String, request: MailboxCreateRequest) async throws {
        try await client.authorizedRequestVoid(
            method: "POST",
            url: "\(baseURL)/api/v1/mailbox/create",
            authToken: inboxSecret,
            body: request
        )
    }

    /// DELETE /api/v1/mailbox/{mailbox_id}
    /// Deletes a mailbox when an identity is deactivated.
    func deleteMailbox(inboxSecret: String, mailboxId: String) async throws {
        let encodedMailboxId = mailboxId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? mailboxId
        try await client.authorizedRequestVoid(
            method: "DELETE",
            url: "\(baseURL)/api/v1/mailbox/\(encodedMailboxId)",
            authToken: inboxSecret,
            body: nil as Empty?
        )
    }

    // MARK: - Pending Notifications

    /// GET /api/v1/pending
    /// Fetches all pending notifications for the inbox.
    func fetchPending(inboxSecret: String) async throws -> PendingNotificationsResponse {
        return try await client.authorizedGet(
            url: "\(baseURL)/api/v1/pending",
            authToken: inboxSecret,
            responseType: PendingNotificationsResponse.self
        )
    }

    /// DELETE /api/v1/pending/{notification_id}
    /// Acknowledges (deletes) a notification after processing.
    func ackNotification(inboxSecret: String, notificationId: String) async throws {
        let encodedNotificationId = notificationId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? notificationId
        try await client.authorizedRequestVoid(
            method: "DELETE",
            url: "\(baseURL)/api/v1/pending/\(encodedNotificationId)",
            authToken: inboxSecret,
            body: nil as Empty?
        )
    }
}
