import Foundation

/// Persists in-app notifications to a JSON file via FileManager.
/// Published properties drive SwiftUI reactivity.
@MainActor
final class LocalNotificationStorage: ObservableObject {

    @Published private(set) var notifications: [LocalNotification] = []

    var unreadCount: Int {
        notifications.filter { !$0.isRead }.count
    }

    /// Maximum stored notifications. Oldest are evicted on save.
    static let maxNotifications = 500

    private let fileURL: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.fileURL = docs.appendingPathComponent("local_notifications.json")
        self.notifications = Self.load(from: fileURL)
    }

    func save(_ notification: LocalNotification) {
        // Preserve isRead if notification already exists locally (avoids resurrecting read notifications on re-fetch)
        let existing = notifications.first { $0.id == notification.id }
        let merged = existing != nil ? LocalNotification(
            id: notification.id,
            mailboxId: notification.mailboxId,
            identityName: notification.identityName,
            payload: notification.payload,
            priority: notification.priority,
            receivedAt: notification.receivedAt,
            isRead: existing!.isRead
        ) : notification
        notifications.removeAll { $0.id == notification.id }
        notifications.insert(merged, at: 0)
        // Trim to capacity
        if notifications.count > Self.maxNotifications {
            notifications = Array(notifications.prefix(Self.maxNotifications))
        }
        persist()
    }

    func markAsRead(_ id: String) {
        guard let index = notifications.firstIndex(where: { $0.id == id }) else { return }
        notifications[index].isRead = true
        persist()
    }

    func markAllAsRead() {
        for i in notifications.indices {
            notifications[i].isRead = true
        }
        persist()
    }

    func delete(_ id: String) {
        notifications.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Private

    private func persist() {
        let snapshot = notifications
        let url = fileURL
        Task.detached(priority: .utility) {
            do {
                let data = try JSONEncoder().encode(snapshot)
                try data.write(to: url, options: .atomic)
            } catch {
                #if DEBUG
                print("LocalNotificationStorage: failed to persist — \(error)")
                #endif
            }
        }
    }

    private static func load(from url: URL) -> [LocalNotification] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([LocalNotification].self, from: data)) ?? []
    }
}
