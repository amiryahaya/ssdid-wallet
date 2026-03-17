import SwiftUI

struct RecoveryRestoreScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject var coordinator: AppCoordinator

    enum RestoreState {
        case idle
        case restoring
        case success
        case error(String)
    }

    @State private var state: RestoreState = .idle
    @State private var did = ""
    @State private var recoveryKey = ""
    @State private var name = ""
    @State private var selectedAlgorithm: Algorithm = .KAZ_SIGN_192

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

                Text("Restore Identity")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            switch state {
            case .success:
                Spacer()
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.successDim)
                            .frame(width: 64, height: 64)
                        Image(systemName: "checkmark")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundStyle(Color.success)
                    }
                    Spacer().frame(height: 16)
                    Text("Identity Restored")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 8)
                    Text("Your identity has been restored and is ready to use.")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                    Spacer().frame(height: 24)
                    Button {
                        coordinator.completeOnboarding()
                        router.reset(to: .walletHome)
                    } label: { Text("Done") }
                        .buttonStyle(.ssdidPrimary)
                }
                .padding(.horizontal, 20)
                Spacer()

            default:
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        Text("Restore your identity using a recovery key or social recovery.")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)

                        // Social recovery option
                        Button {
                            router.push(.socialRecoveryRestore)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "person.2.fill")
                                    .font(.system(size: 20))
                                    .foregroundStyle(Color.textPrimary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Social Recovery")
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundStyle(Color.textPrimary)
                                    Text("Recover using guardian shares")
                                        .font(.system(size: 12))
                                        .foregroundStyle(Color.textSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(Color.textTertiary)
                            }
                            .ssdidCard()
                        }

                        // Divider
                        HStack {
                            Rectangle().fill(Color.ssdidBorder).frame(height: 1)
                            Text("OR RECOVERY KEY")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textTertiary)
                            Rectangle().fill(Color.ssdidBorder).frame(height: 1)
                        }

                        // DID field
                        Text("DID")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $did, prompt: Text("did:ssdid:...").foregroundStyle(Color.textTertiary))
                            .textFieldStyle(.plain)
                            .font(.ssdidMono)
                            .foregroundStyle(Color.textPrimary)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                        // Name field
                        Text("IDENTITY NAME")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $name, prompt: Text("e.g. Personal, Work").foregroundStyle(Color.textTertiary))
                            .textFieldStyle(.plain)
                            .font(.ssdidBody)
                            .foregroundStyle(Color.textPrimary)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                        // Recovery key field
                        Text("RECOVERY KEY")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $recoveryKey, prompt: Text("Paste your recovery key").foregroundStyle(Color.textTertiary), axis: .vertical)
                            .textFieldStyle(.plain)
                            .font(.ssdidMono)
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(3...5)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                        // Algorithm picker
                        Text("ALGORITHM")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)

                        ForEach(Algorithm.allCases, id: \.self) { algo in
                            let isSelected = selectedAlgorithm == algo
                            Button { selectedAlgorithm = algo } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                                        .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(algo.rawValue.replacingOccurrences(of: "_", with: " "))
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundStyle(Color.textPrimary)
                                        Text(algo.w3cType)
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.textTertiary)
                                    }
                                    Spacer()
                                }
                                .padding(14)
                                .background(isSelected ? Color.accentDim : Color.bgCard)
                                .cornerRadius(12)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
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
                        .padding(.horizontal, 20)
                        .padding(.vertical, 4)
                }

                // Restore button
                Button {
                    restore()
                } label: {
                    if case .restoring = state {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Restoring...")
                        }
                    } else {
                        Text("Restore Identity")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: canRestore))
                .disabled(!canRestore)
                .padding(20)
            }
        }
        .background(Color.bgPrimary)
    }

    private var canRestore: Bool {
        !did.isEmpty && !recoveryKey.isEmpty && !name.isEmpty && !isRestoring
    }

    private var isRestoring: Bool {
        if case .restoring = state { return true }
        return false
    }

    private func restore() {
        do {
            _ = try Did.validate(did)
        } catch {
            state = .error("Invalid DID format: \(error.localizedDescription)")
            return
        }
        state = .restoring
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            state = .success
        }
    }
}
