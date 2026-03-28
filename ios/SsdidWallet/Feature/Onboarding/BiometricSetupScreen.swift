import SwiftUI
import SsdidCore

struct BiometricSetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject var coordinator: AppCoordinator
    @Environment(\.scenePhase) private var scenePhase

    private let biometricAuth = BiometricAuthenticator()
    @State private var biometricState: BiometricState = .available

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            // Biometric icon
            ZStack {
                RoundedRectangle(cornerRadius: 32)
                    .fill(Color.accentDim)
                    .frame(width: 120, height: 120)
                iconImage
            }

            Spacer().frame(height: 40)

            Text(titleText)
                .font(.ssdidTitle)
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer().frame(height: 12)

            Text(subtitleText)
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

            // No-hardware warning
            if biometricState == .noHardware {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle")
                        .foregroundStyle(Color.warning)
                    Text("This device has no biometric hardware. A device passcode will be used instead.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(12)
                .background(Color.bgCard)
                .cornerRadius(10)
                .padding(.horizontal, 32)
                .padding(.top, 8)
            }

            // Not-enrolled guidance
            if biometricState == .notEnrolled {
                Text("No biometric is enrolled. Please go to Settings and set up Face ID or Touch ID, or use your device passcode below.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .padding(.top, 8)
            }

            Spacer()

            actionButton

            Spacer().frame(height: 32)
        }
        .background(Color.bgPrimary)
        .onAppear {
            biometricState = biometricAuth.getBiometricState()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                biometricState = biometricAuth.getBiometricState()
            }
        }
    }

    @ViewBuilder
    private var iconImage: some View {
        switch biometricState {
        case .available:
            Image(systemName: "faceid")
                .font(.system(size: 52))
                .foregroundStyle(Color.ssdidAccent)
        case .notEnrolled:
            Image(systemName: "faceid")
                .font(.system(size: 52))
                .foregroundStyle(Color.textTertiary)
        case .noHardware:
            Image(systemName: "lock.shield")
                .font(.system(size: 52))
                .foregroundStyle(Color.textTertiary)
        }
    }

    private var titleText: String {
        switch biometricState {
        case .available:    return "Biometric Authentication"
        case .notEnrolled:  return "Set Up Biometric"
        case .noHardware:   return "Device Passcode Required"
        }
    }

    private var subtitleText: String {
        switch biometricState {
        case .available:
            return "Secure your wallet with Face ID or Touch ID.\nThis adds an extra layer of protection\nfor signing transactions and accessing keys."
        case .notEnrolled:
            return "Your device supports biometrics but none are enrolled.\nYou can use your device passcode for now and\nenroll Face ID or Touch ID later in Settings."
        case .noHardware:
            return "Your device does not support biometric authentication.\nYour device passcode will secure the wallet\nfor every access."
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        switch biometricState {
        case .available:
            Button {
                Task {
                    let result = await biometricAuth.authenticateWithPasscodeFallback(
                        reason: "Enable biometric authentication for SSDID Wallet"
                    )
                    if case .success = result {
                        await MainActor.run { completeSetup() }
                    }
                }
            } label: {
                Text("Enable Biometric Authentication")
            }
            .buttonStyle(.ssdidPrimary)
            .padding(.horizontal, 20)
        case .notEnrolled:
            Button {
                Task {
                    let result = await biometricAuth.authenticateWithPasscodeFallback(
                        reason: "Authenticate to set up SSDID Wallet"
                    )
                    if case .success = result {
                        await MainActor.run { completeSetup() }
                    }
                }
            } label: {
                Text("Use Device Passcode for Now")
            }
            .buttonStyle(.ssdidPrimary)
            .padding(.horizontal, 20)
        case .noHardware:
            Button {
                Task {
                    let result = await biometricAuth.authenticateWithPasscodeFallback(
                        reason: "Authenticate to set up SSDID Wallet"
                    )
                    if case .success = result {
                        await MainActor.run { completeSetup() }
                    }
                }
            } label: {
                Text("Continue with Device Passcode")
            }
            .buttonStyle(.ssdidPrimary)
            .padding(.horizontal, 20)
        }
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
