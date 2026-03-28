import SwiftUI
import SsdidCore

struct WalletHomeScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    @State private var identities: [Identity] = []
    @State private var isLoading = true
    @State private var credentialCounts: [String: Int] = [:]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("IDENTITY WALLET")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textSecondary)
                    Text("Self-Sovereign Digital ID")
                        .font(.ssdidTitle)
                        .foregroundStyle(Color.textPrimary)
                }

                Spacer()

                // Notifications bell with badge
                Button { router.push(.notifications) } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell")
                            .font(.system(size: 22))
                            .foregroundStyle(Color.textSecondary)
                        if services.localNotificationStorage.unreadCount > 0 {
                            Text(services.localNotificationStorage.unreadCount > 99
                                 ? "99+"
                                 : "\(services.localNotificationStorage.unreadCount)")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Color.bgPrimary)
                                .frame(minWidth: 16, minHeight: 16)
                                .background(Color.danger)
                                .clipShape(Circle())
                                .offset(x: 6, y: -6)
                        }
                    }
                }

                Button { router.push(.settings) } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 22))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .padding(20)

            if isLoading {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                Spacer()
            } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    // Section: My Identities
                    HStack {
                        Text("MY IDENTITIES")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textSecondary)
                        Spacer()
                        Button { router.push(.createIdentity()) } label: {
                            Text("+ New")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.ssdidAccent)
                        }
                    }

                    if identities.isEmpty {
                        Button { router.push(.createIdentity()) } label: {
                            VStack(spacing: 0) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 20)
                                        .fill(Color.accentDim)
                                        .frame(width: 72, height: 72)
                                    Image(systemName: "person.crop.circle.badge.plus")
                                        .font(.system(size: 32))
                                        .foregroundStyle(Color.ssdidAccent)
                                }
                                Spacer().frame(height: 16)
                                Text("No identities yet")
                                    .font(.ssdidHeadline)
                                    .foregroundStyle(Color.textPrimary)
                                Spacer().frame(height: 4)
                                Text("Create your first identity to get started")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.textSecondary)
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(32)
                            .background(Color.bgCard)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                    } else {
                        ForEach(identities) { identity in
                            Button {
                                router.push(.identityDetail(keyId: identity.keyId))
                            } label: {
                                identityCard(identity)
                            }
                        }
                    }

                    // Quick Actions
                    Spacer().frame(height: 8)

                    Text("QUICK ACTIONS")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textSecondary)

                    HStack(spacing: 10) {
                        quickActionCard(label: "Scan QR", icon: "qrcode.viewfinder") {
                            router.push(.scanQr)
                        }
                        quickActionCard(label: "Credentials", icon: "doc.text") {
                            router.push(.credentials)
                        }
                        quickActionCard(label: "History", icon: "clock.arrow.circlepath") {
                            router.push(.txHistory)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            .refreshable {
                identities = await services.vault.listIdentities()
                var counts: [String: Int] = [:]
                for identity in identities {
                    let creds = await services.vault.getCredentialsForDid(identity.did)
                    counts[identity.did] = creds.count
                }
                credentialCounts = counts
            }
            } // end else (not loading)
        }
        .background(Color.bgPrimary)
        .task {
            identities = await services.vault.listIdentities().filter { $0.isActive }
            var counts: [String: Int] = [:]
            for identity in identities {
                counts[identity.did] = await services.vault.getCredentialsForDid(identity.did).count
            }
            credentialCounts = counts
            isLoading = false
        }
    }

    @ViewBuilder
    private func identityCard(_ identity: Identity) -> some View {
        let algColor: Color = identity.algorithm.isPostQuantum ? .pqc : .classical
        let algBgColor: Color = identity.algorithm.isPostQuantum ? .pqcDim : .classicalDim

        VStack(alignment: .leading, spacing: 0) {
            // Top accent line
            Rectangle()
                .fill(algColor)
                .frame(height: 2)

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(identity.algorithm.rawValue.replacingOccurrences(of: "_", with: "-"))
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(algColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(algBgColor)
                        .clipShape(RoundedRectangle(cornerRadius: 4))

                    Spacer()

                    Text("Active")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Color.success)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.successDim)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }

                Spacer().frame(height: 10)

                Text(identity.name)
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer().frame(height: 4)

                Text(identity.did.truncatedDid)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.textSecondary)
                    .lineLimit(1)

                if let email = identity.email {
                    Text(email)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textTertiary)
                }

                if let count = credentialCounts[identity.did], count > 0 {
                    Text("\(count) service\(count == 1 ? "" : "s") connected")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                }

                Spacer().frame(height: 12)

                Divider()
                    .background(Color.ssdidBorder)

                Spacer().frame(height: 8)

                Text("Created \(identity.createdAt.prefix(10))")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(16)
        }
        .background(Color.bgCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func quickActionCard(label: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.accentDim)
                        .frame(width: 42, height: 42)
                    Image(systemName: icon)
                        .font(.system(size: 18))
                        .foregroundStyle(Color.ssdidAccent)
                }
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .background(Color.bgCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
