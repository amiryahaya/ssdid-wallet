import Foundation

public struct LocalNotification: Codable, Identifiable, Equatable {
    public let id: String
    public let mailboxId: String
    public let identityName: String?
    public let payload: String
    public let priority: String
    public let receivedAt: String
    public var isRead: Bool

    public init(
        id: String,
        mailboxId: String,
        identityName: String?,
        payload: String,
        priority: String,
        receivedAt: String,
        isRead: Bool = false
    ) {
        self.id = id
        self.mailboxId = mailboxId
        self.identityName = identityName
        self.payload = payload
        self.priority = priority
        self.receivedAt = receivedAt
        self.isRead = isRead
    }
}
