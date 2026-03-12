import SwiftUI

struct RegistrationScreen: View {
    @Environment(AppRouter.self) private var router

    let serverUrl: String
    let serverDid: String

    enum RegistrationStep: Int, CaseIterable {
        case connecting = 0, registering, verifying, complete

        var label: String {
            switch self {
            case .connecting: return "Connecting"
            case .registering: return "Registering"
            case .verifying: return "Verifying"
            case .complete: return "Complete"
            }
        }
    }

    enum RegistrationState {
        case idle
        case inProgress(RegistrationStep)
        case success
        case error(String)
    }

    @State private var state: RegistrationState = .idle
    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?

    private var currentStep: RegistrationStep? {
        switch state {
        case .inProgress(let step): return step
        case .success: return .complete
        default: return nil
        }
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

                Text("Service Registration")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            // Step indicator
            HStack {
                ForEach(RegistrationStep.allCases, id: \.rawValue) { step in
                    let isActive = currentStep != nil && step.rawValue <= (currentStep?.rawValue ?? -1)
                    let isCurrent = currentStep == step

                    VStack(spacing: 4) {
                        ZStack {
                            Circle()
                                .fill(isCurrent ? Color.ssdidAccent : (isActive ? Color.success : Color.bgCard))
                                .frame(width: 28, height: 28)
                            if isActive && !isCurrent {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(Color.bgPrimary)
                            } else {
                                Text("\(step.rawValue + 1)")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(isActive || isCurrent ? Color.bgPrimary : Color.textTertiary)
                            }
                        }
                        Text(step.label)
                            .font(.system(size: 10))
                            .foregroundStyle(isActive ? Color.textPrimary : Color.textTertiary)
                    }
                    if step != .complete { Spacer() }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)

            // Server info card
            VStack(alignment: .leading, spacing: 6) {
                Text("SERVER INFO")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)
                Spacer().frame(height: 2)
                Text("URL")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text(serverUrl)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                Text("DID")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text(serverDid)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
            }
            .ssdidCard()
            .padding(.horizontal, 20)

            Spacer().frame(height: 12)

            switch state {
            case .idle, .inProgress:
                let inProgress = { if case .inProgress = state { return true }; return false }()

                Text("SELECT IDENTITY")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)

                Spacer().frame(height: 8)

                ScrollView {
                    LazyVStack(spacing: 6) {
                        ForEach(identities) { identity in
                            let isSelected = selectedIdentity?.keyId == identity.keyId
                            Button { selectedIdentity = identity } label: {
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
                                }
                                .padding(14)
                                .background(isSelected ? Color.accentDim : Color.bgCard)
                                .cornerRadius(12)
                            }
                            .disabled(inProgress)
                        }

                        if identities.isEmpty {
                            Text("No identities available")
                                .font(.ssdidBody)
                                .foregroundStyle(Color.textSecondary)
                                .frame(maxWidth: .infinity)
                                .padding(24)
                                .background(Color.bgCard)
                                .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 20)
                }

                Button {
                    register()
                } label: {
                    if inProgress {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Registering...")
                        }
                    } else {
                        Text("Register with Service")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: selectedIdentity != nil && !inProgress))
                .disabled(selectedIdentity == nil || inProgress)
                .padding(20)

            case .success:
                Spacer()
                statusView(success: true, title: "Registration Complete",
                           message: "Your identity has been registered with this service.")
                Spacer()
                Button { router.pop() } label: { Text("Done") }
                    .buttonStyle(.ssdidPrimary)
                    .padding(20)

            case .error(let message):
                Spacer()
                statusView(success: false, title: "Registration Failed", message: message)
                Spacer()
                Button { router.pop() } label: { Text("Go Back") }
                    .buttonStyle(.ssdidDanger)
                    .padding(20)
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func statusView(success: Bool, title: String, message: String) -> some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(success ? Color.successDim : Color.dangerDim)
                    .frame(width: 72, height: 72)
                Image(systemName: success ? "checkmark" : "xmark")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(success ? Color.success : Color.danger)
            }
            Spacer().frame(height: 20)
            Text(title)
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 32)
    }

    private func register() {
        state = .inProgress(.connecting)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            state = .inProgress(.registering)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                state = .inProgress(.verifying)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    state = .success
                }
            }
        }
    }
}
