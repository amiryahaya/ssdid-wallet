import Foundation

/// Facade for push notification and mailbox operations.
public struct NotificationsApi {
    private let manager: NotifyManager

    init(manager: NotifyManager) {
        self.manager = manager
    }

    @MainActor
    public func ensureInboxRegistered() async throws {
        try await manager.ensureInboxRegistered()
    }

    @MainActor
    public func createMailbox(identity: Identity) async throws {
        try await manager.createMailbox(for: identity)
    }

    @MainActor
    public func deleteMailbox(identity: Identity) async throws {
        try await manager.deleteMailbox(for: identity)
    }

    @MainActor
    public func fetchAndDemux() async throws {
        try await manager.fetchAndDemux()
    }

    @MainActor
    public func ackPending(notificationId: String) async throws {
        try await manager.ackPending(notificationId: notificationId)
    }
}
