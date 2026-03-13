import Foundation

/// Persists in-app notifications to a JSON file via FileManager.
/// Published properties drive SwiftUI reactivity.
@MainActor
final class LocalNotificationStorage: ObservableObject {

    @Published private(set) var notifications: [LocalNotification] = []

    var unreadCount: Int {
        notifications.filter { !$0.isRead }.count
    }

    private let fileURL: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.fileURL = docs.appendingPathComponent("local_notifications.json")
        self.notifications = Self.load(from: fileURL)
    }

    func save(_ notification: LocalNotification) {
        notifications.removeAll { $0.id == notification.id }
        notifications.insert(notification, at: 0)
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
        do {
            let data = try JSONEncoder().encode(notifications)
            try data.write(to: fileURL, options: .atomic)
        } catch {
            print("LocalNotificationStorage: failed to persist — \(error)")
        }
    }

    private static func load(from url: URL) -> [LocalNotification] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([LocalNotification].self, from: data)) ?? []
    }
}
