import SwiftUI
import SsdidCore

struct InviteAcceptScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let serverUrl: String
    let token: String
    let callbackUrl: String
    let state: String?

    @State private var viewModel: InviteAcceptViewModel?

    var body: some View {
        VStack(spacing: 0) {
            if let viewModel {
                content(viewModel: viewModel)
            } else {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle())
                Spacer()
            }
        }
        .background(Color.bgPrimary)
        .task {
            let vm: InviteAcceptViewModel
            if let existing = viewModel {
                vm = existing
            } else {
                let created = InviteAcceptViewModel(
                    serverUrl: serverUrl,
                    token: token,
                    callbackUrl: callbackUrl,
                    state: state,
                    services: services
                )
                viewModel = created
                vm = created
            }
            await vm.loadData()
        }
        .onDisappear { viewModel?.cancelAccept() }
        .onChange(of: viewModel?.shouldAutoReturn) { _, newValue in
            if newValue == true, let token = viewModel?.sessionToken {
                viewModel?.returnToDrive(sessionToken: token) { router.pop() }
            }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private func content(viewModel: InviteAcceptViewModel) -> some View {
        // Header
        HStack(spacing: 4) {
            Button { viewModel.decline { router.pop() } } label: {
                Image(systemName: "chevron.left")
                    .foregroundStyle(Color.textPrimary)
                    .font(.system(size: 20))
            }
            .padding(.leading, 8)

            Text("Invitation")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)

            Spacer()
        }
        .padding(.vertical, 12)
        .padding(.trailing, 20)

        // Error banner
        if let errorMessage = viewModel.errorMessage {
            ErrorBanner(message: errorMessage, onDismiss: { viewModel.errorMessage = nil })
                .padding(.horizontal, 20)
                .padding(.bottom, 4)
        }

        if viewModel.isLoading {
            Spacer()
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle())
            Text("Loading invitation...")
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .padding(.top, 8)
            Spacer()
        } else if viewModel.isSuccess, let token = viewModel.sessionToken {
            successView(viewModel: viewModel, token: token)
        } else if let inv = viewModel.invitation {
            invitationDetailsView(viewModel: viewModel, inv: inv)
        }
    }

    @ViewBuilder
    private func successView(viewModel: InviteAcceptViewModel, token: String) -> some View {
        Spacer()
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Color.green)
            Text("Invitation Accepted")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.textPrimary)
            if let inv = viewModel.invitation {
                Text("You've joined \(inv.tenantName).")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 40)
        Spacer()

        VStack(spacing: 8) {
            Button {
                viewModel.returnToDrive(sessionToken: token) { router.pop() }
            } label: {
                Text(callbackUrl.isEmpty ? "Done" : "Return to SSDID Drive")
            }
            .buttonStyle(.ssdidPrimary(enabled: true))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func invitationDetailsView(viewModel: InviteAcceptViewModel, inv: InvitationDetailsResponse) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                // Invitation card
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "building.2")
                            .foregroundStyle(Color.ssdidAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Organization")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(inv.tenantName)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(Color.textPrimary)
                        }
                    }

                    if let inviter = inv.inviterName {
                        HStack(spacing: 8) {
                            Image(systemName: "person")
                                .foregroundStyle(Color.ssdidAccent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Invited by")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.textTertiary)
                                Text(inviter)
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.textPrimary)
                            }
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "envelope")
                            .foregroundStyle(Color.ssdidAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Invitation email")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(inv.email)
                                .font(.system(size: 14))
                                .foregroundStyle(Color.textPrimary)
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "person.badge.shield.checkmark")
                            .foregroundStyle(Color.ssdidAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Role")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(inv.role.capitalized)
                                .font(.system(size: 14))
                                .foregroundStyle(Color.textPrimary)
                        }
                    }

                    if let msg = inv.message, !msg.isEmpty {
                        Divider()
                        Text("\"\(msg)\"")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)
                            .italic()
                    }
                }
                .ssdidCard()

                // Email verification status
                if !viewModel.walletEmail.isEmpty {
                    if viewModel.emailMatch {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.green)
                            Text("Email verified: \(viewModel.walletEmail)")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                        }
                        .padding(.horizontal, 4)
                    } else {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(Color.orange)
                            Text("Email mismatch: invitation is for \(inv.email) but your wallet email is \(viewModel.walletEmail)")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.orange)
                        }
                        .padding(.horizontal, 4)
                    }
                }

                // Identity section
                Spacer().frame(height: 4)
                Text("IDENTITY")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                if viewModel.identities.isEmpty {
                    VStack(spacing: 12) {
                        Text("No identities found")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)
                        Button {
                            router.push(.createIdentity())
                        } label: {
                            Text("Create New Identity")
                        }
                        .buttonStyle(.ssdidPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .ssdidCard()
                } else {
                    ForEach(viewModel.identities) { identity in
                        let isSelected = viewModel.selectedIdentity?.keyId == identity.keyId
                        Button { viewModel.selectedIdentity = identity } label: {
                            HStack(spacing: 8) {
                                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(identity.name)
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundStyle(Color.textPrimary)
                                    Text(identity.did)
                                        .font(.system(size: 11, design: .monospaced))
                                        .foregroundStyle(Color.textTertiary)
                                        .lineLimit(1)
                                }
                                Spacer()
                                AlgorithmBadge(
                                    name: identity.algorithm.rawValue.replacingOccurrences(of: "_", with: "-"),
                                    isPostQuantum: identity.algorithm.isPostQuantum
                                )
                            }
                            .padding(14)
                            .background(isSelected ? Color.accentDim : Color.bgCard)
                            .cornerRadius(12)
                        }
                        .disabled(viewModel.isAccepting)
                    }
                }
            }
            .padding(.horizontal, 20)
        }

        // Footer buttons
        VStack(spacing: 8) {
            Button {
                viewModel.accept()
            } label: {
                if viewModel.isAccepting {
                    HStack(spacing: 10) {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("Accepting...")
                    }
                } else {
                    Text("Accept Invitation")
                }
            }
            .buttonStyle(.ssdidPrimary(enabled: viewModel.selectedIdentity != nil && viewModel.emailMatch && !viewModel.isAccepting))
            .disabled(viewModel.selectedIdentity == nil || !viewModel.emailMatch || viewModel.isAccepting)

            Button { viewModel.decline { router.pop() } } label: {
                Text("Decline")
            }
            .buttonStyle(.ssdidSecondary)
            .disabled(viewModel.isAccepting)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
}
