import SwiftUI
import SsdidCore

struct TxHistoryScreen: View {
    @Environment(AppRouter.self) private var router

    @State private var activities: [ActivityRecord] = []

    private var groupedActivities: [(date: String, records: [ActivityRecord])] {
        let grouped = Dictionary(grouping: activities) { String($0.timestamp.prefix(10)) }
        return grouped.sorted { $0.key > $1.key }.map { (date: $0.key, records: $0.value) }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Activity History")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            if activities.isEmpty {
                Spacer()
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.bgCard)
                            .frame(width: 72, height: 72)
                        Image(systemName: "list.clipboard")
                            .font(.system(size: 32))
                            .foregroundStyle(Color.textTertiary)
                    }
                    Spacer().frame(height: 16)
                    Text("No activity yet")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 4)
                    Text("Your identity and transaction history will appear here.")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 32)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 6) {
                        ForEach(groupedActivities, id: \.date) { group in
                            Text(group.date.uppercased())
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 8)
                                .padding(.bottom, 6)

                            ForEach(group.records) { record in
                                activityRow(record)
                            }
                        }

                        Spacer().frame(height: 20)
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func activityRow(_ record: ActivityRecord) -> some View {
        let style = activityStyle(record.type)
        let title = record.type.rawValue
            .replacingOccurrences(of: "_", with: " ")
            .lowercased()
            .prefix(1).uppercased() + record.type.rawValue
            .replacingOccurrences(of: "_", with: " ")
            .lowercased()
            .dropFirst()
        let subtitle = record.serviceUrl ?? record.did
        let time: String = {
            if record.timestamp.count > 11 {
                return String(record.timestamp.dropFirst(11).prefix(5))
            }
            return ""
        }()

        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(style.dimColor)
                    .frame(width: 40, height: 40)
                Image(systemName: style.icon)
                    .font(.system(size: 16))
                    .foregroundStyle(style.color)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
                    .lineLimit(1)
            }
            Spacer()
            Text(time)
                .font(.system(size: 12))
                .foregroundStyle(Color.textTertiary)
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
    }

    private struct ActivityStyle {
        let icon: String
        let color: Color
        let dimColor: Color
    }

    private func activityStyle(_ type: ActivityType) -> ActivityStyle {
        switch type {
        case .IDENTITY_CREATED:
            return ActivityStyle(icon: "hexagon", color: .ssdidAccent, dimColor: .accentDim)
        case .IDENTITY_DEACTIVATED:
            return ActivityStyle(icon: "xmark.hexagon", color: .danger, dimColor: .dangerDim)
        case .SERVICE_REGISTERED:
            return ActivityStyle(icon: "link", color: .success, dimColor: .successDim)
        case .AUTHENTICATED:
            return ActivityStyle(icon: "checkmark.shield", color: .classical, dimColor: .classicalDim)
        case .TX_SIGNED:
            return ActivityStyle(icon: "pencil", color: .warning, dimColor: .warningDim)
        case .KEY_ROTATED:
            return ActivityStyle(icon: "arrow.triangle.2.circlepath", color: .pqc, dimColor: .pqcDim)
        case .CREDENTIAL_RECEIVED:
            return ActivityStyle(icon: "doc.text", color: .success, dimColor: .successDim)
        case .BACKUP_CREATED:
            return ActivityStyle(icon: "externaldrive", color: .ssdidAccent, dimColor: .accentDim)
        case .DEVICE_ENROLLED:
            return ActivityStyle(icon: "iphone", color: .ssdidAccent, dimColor: .accentDim)
        case .DEVICE_REMOVED:
            return ActivityStyle(icon: "iphone.slash", color: .danger, dimColor: .dangerDim)
        case .CREDENTIAL_PRESENTED:
            return ActivityStyle(icon: "doc.badge.arrow.up", color: .classical, dimColor: .classicalDim)
        }
    }
}
