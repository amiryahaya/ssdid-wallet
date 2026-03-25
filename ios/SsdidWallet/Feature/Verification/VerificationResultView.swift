import SwiftUI

struct VerificationResultView: View {
    let result: UnifiedVerificationResult
    @Environment(AppRouter.self) private var router
    @State private var showDetails = false

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

                Text("Verification Result")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                VStack(spacing: 16) {
                    // Traffic light card
                    trafficLightCard

                    // Expandable detail section
                    detailsSection

                    // Bundle age (if offline)
                    if let bundleAge = result.bundleAge {
                        bundleAgeCard(age: bundleAge)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .background(Color.bgPrimary)
    }

    // MARK: - Traffic Light Card

    @ViewBuilder
    private var trafficLightCard: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(trafficLightColor.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: trafficLightIcon)
                    .font(.system(size: 36))
                    .foregroundStyle(trafficLightColor)
            }

            Text(trafficLightTitle)
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)

            if result.source == .offline {
                offlineBadge
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color.bgCard)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(trafficLightColor.opacity(0.3), lineWidth: 1)
        )
    }

    private var trafficLightColor: Color {
        switch result.status {
        case .verified, .verifiedOffline: return Color.success
        case .degraded: return Color.warning
        case .failed: return Color.danger
        }
    }

    private var trafficLightIcon: String {
        switch result.status {
        case .verified, .verifiedOffline: return "checkmark.circle.fill"
        case .degraded: return "exclamationmark.triangle.fill"
        case .failed: return "xmark.circle.fill"
        }
    }

    private var trafficLightTitle: String {
        switch result.status {
        case .verified: return "Credential verified"
        case .verifiedOffline: return "Credential verified offline"
        case .degraded: return "Verified with limitations"
        case .failed: return "Verification failed"
        }
    }

    // MARK: - Offline Badge

    @ViewBuilder
    private var offlineBadge: some View {
        HStack(spacing: 6) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 11))
            Text("Offline")
                .font(.caption2)
        }
        .foregroundStyle(Color.warning)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Color.warning.opacity(0.12))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.warning.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Details Section

    @ViewBuilder
    private var detailsSection: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    showDetails.toggle()
                }
            } label: {
                HStack {
                    Text("Verification Checks")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                    Spacer()
                    Image(systemName: showDetails ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textTertiary)
                }
                .padding(14)
            }
            .background(Color.bgCard)
            .cornerRadius(showDetails ? 0 : 12)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: 12,
                    bottomLeadingRadius: showDetails ? 0 : 12,
                    bottomTrailingRadius: showDetails ? 0 : 12,
                    topTrailingRadius: 12
                )
            )

            if showDetails {
                VStack(spacing: 1) {
                    ForEach(Array(result.checks.enumerated()), id: \.offset) { index, check in
                        checkRow(check: check, isLast: index == result.checks.count - 1)
                    }
                }
                .background(Color.bgCard)
                .clipShape(
                    UnevenRoundedRectangle(
                        topLeadingRadius: 0,
                        bottomLeadingRadius: 12,
                        bottomTrailingRadius: 12,
                        topTrailingRadius: 0
                    )
                )
            }
        }
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.ssdidBorder, lineWidth: 1)
        )
    }

    @ViewBuilder
    private func checkRow(check: VerificationCheck, isLast: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: checkIcon(for: check.status))
                .font(.system(size: 16))
                .foregroundStyle(checkColor(for: check.status))
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(checkTypeName(for: check.type))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Text(check.message)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.bgCard)

        if !isLast {
            Divider()
                .background(Color.ssdidBorder)
                .padding(.leading, 46)
        }
    }

    private func checkIcon(for status: CheckStatus) -> String {
        switch status {
        case .pass: return "checkmark.circle.fill"
        case .fail: return "xmark.circle.fill"
        case .unknown: return "questionmark.circle.fill"
        }
    }

    private func checkColor(for status: CheckStatus) -> Color {
        switch status {
        case .pass: return Color.success
        case .fail: return Color.danger
        case .unknown: return Color.textTertiary
        }
    }

    private func checkTypeName(for type: CheckType) -> String {
        switch type {
        case .signature: return "Signature"
        case .expiry: return "Expiry"
        case .revocation: return "Revocation"
        case .bundleFreshness: return "Bundle Freshness"
        }
    }

    // MARK: - Bundle Age Card

    @ViewBuilder
    private func bundleAgeCard(age: TimeInterval) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "clock")
                .font(.system(size: 16))
                .foregroundStyle(Color.textTertiary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text("Bundle Age")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Text(formatBundleAge(age))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
            }
            Spacer()
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.ssdidBorder, lineWidth: 1)
        )
    }

    private func formatBundleAge(_ age: TimeInterval) -> String {
        let hours = Int(age / 3600)
        if hours < 24 {
            return "\(hours) hour\(hours == 1 ? "" : "s") old"
        }
        let days = hours / 24
        return "\(days) day\(days == 1 ? "" : "s") old"
    }
}
