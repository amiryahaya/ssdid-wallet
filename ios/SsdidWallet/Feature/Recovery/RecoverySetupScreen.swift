import SwiftUI

struct RecoverySetupScreen: View {
    @Environment(AppRouter.self) private var router

    let keyId: String

    enum RecoverySetupState {
        case idle
        case generating
        case success(String) // recovery key base64
        case error(String)
    }

    @State private var identity: Identity?
    @State private var state: RecoverySetupState = .idle
    @State private var hasSocialRecovery = false
    @State private var hasInstitutionalRecovery = false

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

                Text("Recovery Setup")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(spacing: 12) {
                    // Tier 1: Recovery Key
                    recoveryTierCard(
                        icon: "key.fill",
                        title: "Recovery Key",
                        description: "Generate an offline recovery key. Store it securely as a backup.",
                        badgeText: "Offline",
                        badgeColor: .success,
                        badgeBgColor: .successDim,
                        isConfigured: identity?.hasRecoveryKey == true,
                        buttonText: "Generate Recovery Key",
                        buttonEnabled: identity != nil && !isGenerating,
                        isLoading: isGenerating,
                        action: generateRecoveryKey
                    )

                    // Recovery key output
                    if case .success(let keyBase64) = state {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("IMPORTANT")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(Color.warning)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 3)
                                .background(Color.warningDim)
                                .cornerRadius(4)

                            Spacer().frame(height: 12)

                            Text("Store this recovery key offline in a secure location. It cannot be retrieved later.")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.warning)

                            Spacer().frame(height: 12)

                            Text("RECOVERY KEY")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(Color.textTertiary)

                            Spacer().frame(height: 4)

                            Text(keyBase64)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(Color.textPrimary)

                            Spacer().frame(height: 12)

                            Button {
                                UIPasteboard.general.string = keyBase64
                                HapticManager.notification(.success)
                            } label: {
                                Text("Copy to Clipboard")
                            }
                            .buttonStyle(.ssdidPrimary)
                        }
                        .ssdidCard()
                    }

                    // Error
                    if case .error(let message) = state {
                        Text(message)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.danger)
                            .padding(18)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.dangerDim)
                            .cornerRadius(16)
                    }

                    // Tier 2: Social Recovery
                    recoveryTierCard(
                        icon: "person.2.fill",
                        title: "Social Recovery",
                        description: hasSocialRecovery
                            ? "Social recovery is configured."
                            : "Split your recovery key among trusted guardians using Shamir's Secret Sharing.",
                        badgeText: "Social",
                        badgeColor: .ssdidAccent,
                        badgeBgColor: .accentDim,
                        isConfigured: hasSocialRecovery,
                        buttonText: "Configure Social Recovery",
                        buttonEnabled: identity?.hasRecoveryKey == true,
                        isLoading: false,
                        action: { router.push(.socialRecoverySetup(keyId: keyId)) }
                    )

                    // Tier 3: Institutional
                    recoveryTierCard(
                        icon: "building.2.fill",
                        title: "Institutional Recovery",
                        description: hasInstitutionalRecovery
                            ? "Institutional recovery is configured."
                            : "Enroll with an organization that can help recover your identity.",
                        badgeText: "Institutional",
                        badgeColor: .pqc,
                        badgeBgColor: .pqcDim,
                        isConfigured: hasInstitutionalRecovery,
                        buttonText: "Enroll Organization",
                        buttonEnabled: identity?.hasRecoveryKey == true,
                        isLoading: false,
                        action: { router.push(.institutionalSetup(keyId: keyId)) }
                    )

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
    }

    private var isGenerating: Bool {
        if case .generating = state { return true }
        return false
    }

    @ViewBuilder
    private func recoveryTierCard(
        icon: String,
        title: String,
        description: String,
        badgeText: String,
        badgeColor: Color,
        badgeBgColor: Color,
        isConfigured: Bool,
        buttonText: String,
        buttonEnabled: Bool,
        isLoading: Bool,
        action: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 12) {
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .foregroundStyle(Color.textPrimary)
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.textPrimary)
                }
                Spacer()
                Text(badgeText)
                    .font(.system(size: 11))
                    .foregroundStyle(badgeColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(badgeBgColor)
                    .cornerRadius(4)
            }

            Spacer().frame(height: 8)

            Text(description)
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)

            Spacer().frame(height: 14)

            if isConfigured {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.success)
                    Text("Configured")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.success)
                }
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(Color.successDim)
                .cornerRadius(12)
            } else {
                Button(action: action) {
                    if isLoading {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Generating...")
                        }
                    } else {
                        Text(buttonText)
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: buttonEnabled))
                .disabled(!buttonEnabled)
            }
        }
        .ssdidCard()
    }

    private func generateRecoveryKey() {
        state = .generating
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            // Mock recovery key
            state = .success("dGhpc19pc19hX21vY2tfcmVjb3Zlcnlfa2V5X2Jhc2U2NA==")
        }
    }
}
