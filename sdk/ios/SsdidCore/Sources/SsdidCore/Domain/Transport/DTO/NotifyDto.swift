import Foundation

/// Request/response DTOs for SSDID Notify service interactions.

// MARK: - Device

public struct NotifyDevice: Codable {
    public let platform: String
    public let token: String
}

// MARK: - Inbox

public struct InboxRegisterRequest: Codable {
    public let devices: [NotifyDevice]
}

public struct InboxRegisterResponse: Codable {
    public let inboxId: String
    public let inboxSecret: String

    enum CodingKeys: String, CodingKey {
        case inboxId = "inbox_id"
        case inboxSecret = "inbox_secret"
    }
}

public struct InboxUpdateDevicesRequest: Codable {
    public let devices: [NotifyDevice]
}

// MARK: - Mailbox

public struct MailboxCreateRequest: Codable {
    public let inboxId: String
    public let mailboxId: String

    enum CodingKeys: String, CodingKey {
        case inboxId = "inbox_id"
        case mailboxId = "mailbox_id"
    }
}

// MARK: - Pending Notifications

public struct NotifyNotification: Codable {
    public let notificationId: String
    public let mailboxId: String
    public let payload: String
    public let priority: String?
    public let receivedAt: String?

    enum CodingKeys: String, CodingKey {
        case notificationId = "notification_id"
        case mailboxId = "mailbox_id"
        case payload
        case priority
        case receivedAt = "received_at"
    }
}

public struct PendingNotificationsResponse: Codable {
    public let notifications: [NotifyNotification]
}
