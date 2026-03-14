import SwiftUI

struct NotificationsScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    var body: some View {
        let storage = services.localNotificationStorage
        let isoFormatter = ISO8601DateFormatter()
        let sorted = storage.notifications.sorted {
            let d0 = isoFormatter.date(from: $0.receivedAt) ?? .distantPast
            let d1 = isoFormatter.date(from: $1.receivedAt) ?? .distantPast
            return d0 > d1
        }

        VStack(spacing: 0) {
            // Header
            HStack {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                }
                Spacer().frame(width: 12)
                Text("Notifications")
                    .font(.ssdidTitle)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                if storage.unreadCount > 0 {
                    Button {
                        storage.markAllAsRead()
                    } label: {
                        Text("Mark All Read")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.ssdidAccent)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)

            if sorted.isEmpty {
                Spacer()
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.bgCard)
                            .frame(width: 72, height: 72)
                        Image(systemName: "bell")
                            .font(.system(size: 32))
                            .foregroundStyle(Color.textSecondary)
                    }
                    Spacer().frame(height: 16)
                    Text("No notifications")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 4)
                    Text("Notifications from services will appear here")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 6) {
                        ForEach(sorted) { notification in
                            notificationRow(notification, storage: storage)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func notificationRow(_ notification: LocalNotification, storage: LocalNotificationStorage) -> some View {
        HStack(alignment: .top, spacing: 12) {
            // Unread dot
            Circle()
                .fill(notification.isRead ? Color.clear : Color.ssdidAccent)
                .frame(width: 8, height: 8)
                .padding(.top, 6)

            VStack(alignment: .leading, spacing: 2) {
                if let name = notification.identityName {
                    Text(name)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.textTertiary)
                }
                Text(notification.payload)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(relativeTime(notification.receivedAt))
                .font(.system(size: 12))
                .foregroundStyle(Color.textTertiary)
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
        .onTapGesture {
            storage.markAsRead(notification.id)
        }
    }

    private func relativeTime(_ isoTimestamp: String) -> String {
        guard !isoTimestamp.isEmpty else { return "" }
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: isoTimestamp) else { return "" }
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        switch minutes {
        case ..<1: return "now"
        case ..<60: return "\(minutes)m ago"
        case ..<1440: return "\(minutes / 60)h ago"
        case ..<2880: return "Yesterday"
        default: return "\(minutes / 1440)d ago"
        }
    }
}
