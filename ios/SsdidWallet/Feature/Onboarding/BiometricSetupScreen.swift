import SwiftUI

struct BiometricSetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject var coordinator: AppCoordinator

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            // Biometric icon
            ZStack {
                RoundedRectangle(cornerRadius: 32)
                    .fill(Color.accentDim)
                    .frame(width: 120, height: 120)
                Image(systemName: "faceid")
                    .font(.system(size: 52))
                    .foregroundStyle(Color.ssdidAccent)
            }

            Spacer().frame(height: 40)

            Text("Biometric Authentication")
                .font(.ssdidTitle)
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer().frame(height: 12)

            Text("Secure your wallet with Face ID or Touch ID.\nThis adds an extra layer of protection\nfor signing transactions and accessing keys.")
                .font(.ssdidBody)
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, 32)

            Spacer().frame(height: 16)

            // Security info card
            VStack(alignment: .leading, spacing: 8) {
                securityRow("Biometric data never leaves your device")
                securityRow("Keys remain in hardware-backed Secure Enclave")
                securityRow("Required for transaction signing")
            }
            .ssdidCard()
            .padding(.horizontal, 32)

            Spacer()

            // Enable button
            Button {
                completeSetup()
            } label: {
                Text("Enable Biometric Authentication")
            }
            .buttonStyle(.ssdidPrimary)
            .padding(.horizontal, 20)

            // Skip button
            Button {
                completeSetup()
            } label: {
                Text("Skip for now")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textTertiary)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
            .padding(.top, 8)
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func securityRow(_ text: String) -> some View {
        HStack(alignment: .center, spacing: 10) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color.success)
                .frame(width: 8, height: 8)
            Text(text)
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)
        }
    }

    private func completeSetup() {
        coordinator.completeOnboarding()
        router.reset(to: .walletHome)
    }
}
