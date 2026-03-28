import Foundation

/// Request/response DTOs for SSDID Notify service interactions.

// MARK: - Device

struct NotifyDevice: Codable {
    let platform: String
    let token: String
}

// MARK: - Inbox

struct InboxRegisterRequest: Codable {
    let devices: [NotifyDevice]
}

struct InboxRegisterResponse: Codable {
    let inboxId: String
    let inboxSecret: String

    enum CodingKeys: String, CodingKey {
        case inboxId = "inbox_id"
        case inboxSecret = "inbox_secret"
    }
}

struct InboxUpdateDevicesRequest: Codable {
    let devices: [NotifyDevice]
}

// MARK: - Mailbox

struct MailboxCreateRequest: Codable {
    let inboxId: String
    let mailboxId: String

    enum CodingKeys: String, CodingKey {
        case inboxId = "inbox_id"
        case mailboxId = "mailbox_id"
    }
}

// MARK: - Pending Notifications

struct NotifyNotification: Codable {
    let notificationId: String
    let mailboxId: String
    let payload: String
    let priority: String?
    let receivedAt: String?

    enum CodingKeys: String, CodingKey {
        case notificationId = "notification_id"
        case mailboxId = "mailbox_id"
        case payload
        case priority
        case receivedAt = "received_at"
    }
}

struct PendingNotificationsResponse: Codable {
    let notifications: [NotifyNotification]
}
