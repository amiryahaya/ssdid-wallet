import Foundation

struct LocalNotification: Codable, Identifiable, Equatable {
    let id: String
    let mailboxId: String
    let identityName: String?
    let payload: String
    let priority: String
    let receivedAt: String
    var isRead: Bool

    init(
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
