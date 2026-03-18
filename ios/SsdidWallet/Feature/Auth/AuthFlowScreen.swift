import SwiftUI

struct AuthFlowScreen: View {
    @Environment(AppRouter.self) private var router

    let serverUrl: String
    let callbackUrl: String
    let csrfState: String?

    enum AuthState {
        case idle
        case loading
        case success(sessionToken: String)
        case error(message: String)
    }

    @State private var state: AuthState = .idle
    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .accessibilityLabel("Back")
                .padding(.leading, 8)

                Text("Authentication")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            // Service info
            VStack(alignment: .leading, spacing: 8) {
                Text("SERVICE REQUEST")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)
                Text("Server URL")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text(serverUrl)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                Text("Action")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text("Authentication")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.ssdidAccent)
            }
            .ssdidCard()
            .padding(.horizontal, 20)

            Spacer().frame(height: 12)

            switch state {
            case .idle, .loading:
                let isLoading = { if case .loading = state { return true }; return false }()

                VStack(alignment: .leading, spacing: 8) {
                    Text("SELECT IDENTITY")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                }
                .padding(.horizontal, 20)

                ScrollView {
                    LazyVStack(spacing: 6) {
                        if identities.isEmpty {
                            Text("No identities available")
                                .font(.ssdidBody)
                                .foregroundStyle(Color.textSecondary)
                                .frame(maxWidth: .infinity)
                                .padding(24)
                                .background(Color.bgCard)
                                .cornerRadius(12)
                        } else {
                            ForEach(identities) { identity in
                                identityRow(identity, isSelected: selectedIdentity?.keyId == identity.keyId, enabled: !isLoading)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }

                // Biometric card
                biometricCard()
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                Button {
                    authenticate()
                } label: {
                    if isLoading {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Authenticating...")
                        }
                    } else {
                        Text("Authenticate")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: selectedIdentity != nil && !isLoading))
                .disabled(selectedIdentity == nil || isLoading)
                .padding(20)

            case .success:
                Spacer()
                statusView(icon: "checkmark", iconColor: .success, iconBg: .successDim,
                           title: "Authentication Successful",
                           message: "You have been authenticated with the service.")
                Spacer()
                Button { router.pop() } label: { Text("Done") }
                    .buttonStyle(.ssdidPrimary)
                    .padding(20)

            case .error(let message):
                Spacer()
                statusView(icon: "xmark", iconColor: .danger, iconBg: .dangerDim,
                           title: "Authentication Failed", message: message)
                Spacer()
                Button { router.pop() } label: { Text("Go Back") }
                    .buttonStyle(.ssdidDanger)
                    .padding(20)
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func identityRow(_ identity: Identity, isSelected: Bool, enabled: Bool) -> some View {
        Button {
            selectedIdentity = identity
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                    .font(.system(size: 20))
                    .accessibilityLabel(isSelected ? "Selected" : "Not selected")
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
        .disabled(!enabled)
    }

    @ViewBuilder
    private func biometricCard() -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.accentDim)
                    .frame(width: 36, height: 36)
                Image(systemName: "faceid")
                    .font(.system(size: 18))
                    .foregroundStyle(Color.ssdidAccent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Biometric Confirmation")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Text("Touch sensor to confirm authentication")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .ssdidCard()
    }

    @ViewBuilder
    private func statusView(icon: String, iconColor: Color, iconBg: Color, title: String, message: String) -> some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(iconBg)
                    .frame(width: 72, height: 72)
                Image(systemName: icon)
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(iconColor)
            }
            Spacer().frame(height: 20)
            Text(title)
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
    }

    private func authenticate() {
        state = .loading
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            HapticManager.notification(.success)
            state = .success(sessionToken: "mock-session-token")
        }
    }
}
