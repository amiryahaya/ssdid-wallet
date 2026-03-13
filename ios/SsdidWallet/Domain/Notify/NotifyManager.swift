import Foundation
import CryptoKit
import UserNotifications

/// Orchestrates SSDID Notify service integration: inbox registration, APNs device token
/// updates, per-identity mailbox lifecycle, and pending notification retrieval.
///
/// Inbox credentials (inbox_id / inbox_secret) are stored in the Keychain.
/// Non-sensitive metadata (inbox_id) is also mirrored in UserDefaults for quick reads.
@MainActor
final class NotifyManager {

    // MARK: - Keychain aliases

    private static let inboxSecretAlias = "notify.inbox.secret"
    private static let inboxIdAlias = "notify.inbox.id"
    private static let inboxIdDefaultsKey = "notify.inbox.id"

    // MARK: - Dependencies

    private let api: NotifyApi
    private let keychainManager: KeychainManager

    // MARK: - State

    /// Guards against concurrent calls to ensureInboxRegistered causing a double-registration.
    /// Safe without a lock because this class is @MainActor-isolated.
    private var isRegistering = false

    // MARK: - Identity Resolution

    /// Closure that returns the current list of identities.
    /// Set by the app layer (ServiceContainer or SsdidWalletApp) to enable demux.
    var identityProvider: (() async -> [Identity])?

    // MARK: - Init

    init(api: NotifyApi, keychainManager: KeychainManager) {
        self.api = api
        self.keychainManager = keychainManager
    }

    // MARK: - Inbox

    /// Ensures the device has a registered inbox with the Notify service.
    ///
    /// On first launch this registers a new inbox (without a device token — APNs
    /// registration happens separately). On subsequent launches it returns immediately
    /// because the credentials are already stored in the Keychain.
    func ensureInboxRegistered() async throws {
        guard !isRegistering else { return }
        isRegistering = true
        defer { isRegistering = false }
        guard (try? keychainManager.loadSecret(alias: Self.inboxSecretAlias)) == nil else { return }

        // Register with no devices; APNs token is supplied later via registerAPNsToken(_:).
        let response = try await api.registerInbox(
            request: InboxRegisterRequest(devices: [])
        )

        try keychainManager.saveSecret(alias: Self.inboxSecretAlias, value: response.inboxSecret)
        try keychainManager.saveSecret(alias: Self.inboxIdAlias, value: response.inboxId)
        UserDefaults.standard.set(response.inboxId, forKey: Self.inboxIdDefaultsKey)
    }

    /// Converts the raw APNs device token to a hex string and registers (or updates)
    /// it with the Notify service. Safe to call every time the app receives a new token.
    func registerAPNsToken(_ tokenData: Data) async throws {
        guard let inboxSecret = try keychainManager.loadSecret(alias: Self.inboxSecretAlias) else {
            // Inbox not yet registered; token will be registered after ensureInboxRegistered.
            return
        }

        let hexToken = tokenData.map { String(format: "%02x", $0) }.joined()
        let device = NotifyDevice(platform: "ios", token: hexToken)

        try await api.updateDevices(
            inboxSecret: inboxSecret,
            request: InboxUpdateDevicesRequest(devices: [device])
        )
    }

    // MARK: - Mailboxes

    /// Creates a mailbox for the given identity.
    ///
    /// The mailbox_id is deterministic: `"mbx_"` followed by the first 16 characters of
    /// the URL-safe base64 (no padding) encoding of SHA-256(did), providing
    /// correlation resistance while remaining stable across reinstalls.
    func createMailbox(for identity: Identity) async throws {
        guard let inboxSecret = try keychainManager.loadSecret(alias: Self.inboxSecretAlias) else {
            throw NotifyError.inboxNotRegistered
        }

        let inboxId = try loadInboxId()

        let mailboxId = mailboxIdForDid(identity.did)

        try await api.createMailbox(
            inboxSecret: inboxSecret,
            request: MailboxCreateRequest(inboxId: inboxId, mailboxId: mailboxId)
        )
    }

    /// Deletes the mailbox associated with the given identity.
    /// Call this when an identity is deactivated or removed.
    func deleteMailbox(for identity: Identity) async throws {
        guard let inboxSecret = try keychainManager.loadSecret(alias: Self.inboxSecretAlias) else {
            throw NotifyError.inboxNotRegistered
        }

        let mailboxId = mailboxIdForDid(identity.did)

        try await api.deleteMailbox(inboxSecret: inboxSecret, mailboxId: mailboxId)
    }

    // MARK: - Pending Notifications

    /// Fetches all pending notifications from the Notify service.
    /// Call this on reconnect or when the app returns to foreground.
    func fetchPending() async throws -> [NotifyNotification] {
        guard let inboxSecret = try keychainManager.loadSecret(alias: Self.inboxSecretAlias) else {
            throw NotifyError.inboxNotRegistered
        }

        let response = try await api.fetchPending(inboxSecret: inboxSecret)
        return response.notifications
    }

    /// Acknowledges a notification by deleting it from the Notify service.
    /// Call this after the notification has been fully processed.
    func ackPending(notificationId: String) async throws {
        guard let inboxSecret = try keychainManager.loadSecret(alias: Self.inboxSecretAlias) else {
            throw NotifyError.inboxNotRegistered
        }

        try await api.ackNotification(inboxSecret: inboxSecret, notificationId: notificationId)
    }

    // MARK: - Fetch & Demux

    /// Fetches all pending notifications, resolves each to its owning identity,
    /// posts a local notification with identity context, and acknowledges.
    func fetchAndDemux() async throws {
        let pending = try await fetchPending()
        guard !pending.isEmpty else { return }

        // Build reverse map: mailboxId -> identity name
        let identities = await identityProvider?() ?? []
        var mailboxToName: [String: String] = [:]
        for identity in identities {
            let mbxId = mailboxIdForDid(identity.did)
            mailboxToName[mbxId] = identity.name
        }

        let center = UNUserNotificationCenter.current()

        for notification in pending {
            let identityName = mailboxToName[notification.mailboxId]

            let content = UNMutableNotificationContent()
            content.title = identityName.map { "SSDID — \($0)" } ?? "SSDID"
            content.body = notification.payload
            content.sound = .default

            let request = UNNotificationRequest(
                identifier: notification.notificationId,
                content: content,
                trigger: nil // Deliver immediately
            )
            try? await center.add(request)

            // Acknowledge — failure means re-fetch next time, acceptable
            try? await ackPending(notificationId: notification.notificationId)
        }
    }

    // MARK: - Private Helpers

    /// Reads the inbox ID with fallback: UserDefaults first, then Keychain.
    private func loadInboxId() throws -> String {
        if let id = UserDefaults.standard.string(forKey: Self.inboxIdDefaultsKey) {
            return id
        }
        if let id = try keychainManager.loadSecret(alias: Self.inboxIdAlias) {
            // Repair UserDefaults from Keychain (e.g. after a restore).
            UserDefaults.standard.set(id, forKey: Self.inboxIdDefaultsKey)
            return id
        }
        throw NotifyError.inboxNotRegistered
    }

    /// Derives a stable mailbox ID from a DID string.
    /// Format: "mbx_" + base64url-nopad(SHA-256(did))[0..<16]
    private func mailboxIdForDid(_ did: String) -> String {
        let digest = SHA256.hash(data: Data(did.utf8))
        let hashData = Data(digest)
        let encoded = hashData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let prefix = String(encoded.prefix(16))
        return "mbx_\(prefix)"
    }
}

// MARK: - NotifyError

enum NotifyError: Error, LocalizedError {
    case inboxNotRegistered

    var errorDescription: String? {
        switch self {
        case .inboxNotRegistered:
            return "Notify inbox has not been registered yet. Call ensureInboxRegistered() first."
        }
    }
}
