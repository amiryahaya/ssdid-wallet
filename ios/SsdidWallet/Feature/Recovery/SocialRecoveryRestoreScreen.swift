import SwiftUI

struct SocialRecoveryRestoreScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject var coordinator: AppCoordinator

    struct ShareEntry: Identifiable {
        let id = UUID()
        var index: String = ""
        var data: String = ""
    }

    enum SocialRestoreState {
        case idle
        case restoring
        case success
        case error(String)
    }

    @State private var state: SocialRestoreState = .idle
    @State private var shares: [ShareEntry] = [ShareEntry(), ShareEntry()]
    @State private var did = ""
    @State private var name = ""
    @State private var selectedAlgorithm: Algorithm = .KAZ_SIGN_192

    private var isRestoring: Bool {
        if case .restoring = state { return true }
        return false
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

                Text("Social Recovery")
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
                    Text("Identity Recovered")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 8)
                    Text("Your identity has been recovered from guardian shares.")
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
                        Text("Enter guardian shares to recover your identity. You need the threshold number of shares.")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)

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

                        // Shares
                        Text("GUARDIAN SHARES")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)

                        ForEach(Array(shares.enumerated()), id: \.element.id) { index, _ in
                            shareCard(index: index)
                        }

                        Button {
                            shares.append(ShareEntry())
                        } label: {
                            Text("Add Share")
                                .font(.system(size: 14))
                                .foregroundStyle(Color.ssdidAccent)
                        }
                    }
                    .padding(.horizontal, 20)
                }

                // Error
                if case .error(let message) = state {
                    Text(message)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.danger)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.dangerDim)
                        .cornerRadius(12)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 4)
                }

                // Recover button
                Button {
                    recover()
                } label: {
                    if isRestoring {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Recovering...")
                        }
                    } else {
                        Text("Recover Identity")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: canRecover))
                .disabled(!canRecover)
                .padding(20)
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func shareCard(index: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Share \(index + 1)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                if shares.count > 2 {
                    Button {
                        shares.remove(at: index)
                    } label: {
                        Text("Remove")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.danger)
                    }
                }
            }

            TextField("", text: shareBinding(index: index, keyPath: \.index),
                      prompt: Text("Share index (e.g. 1)").foregroundStyle(Color.textTertiary))
                .textFieldStyle(.plain)
                .font(.ssdidBody)
                .foregroundStyle(Color.textPrimary)
                .padding(12)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))

            TextField("", text: shareBinding(index: index, keyPath: \.data),
                      prompt: Text("Share data (Base64)").foregroundStyle(Color.textTertiary), axis: .vertical)
                .textFieldStyle(.plain)
                .font(.ssdidMono)
                .foregroundStyle(Color.textPrimary)
                .lineLimit(2...4)
                .padding(12)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))
        }
        .ssdidCard()
    }

    private func shareBinding(index: Int, keyPath: WritableKeyPath<ShareEntry, String>) -> Binding<String> {
        Binding(
            get: { shares[index][keyPath: keyPath] },
            set: { shares[index][keyPath: keyPath] = $0 }
        )
    }

    private var canRecover: Bool {
        !did.isEmpty && !name.isEmpty &&
        shares.allSatisfy({ !$0.index.isEmpty && !$0.data.isEmpty }) &&
        !isRestoring
    }

    private func recover() {
        guard did.hasPrefix("did:") else {
            state = .error("Invalid DID format")
            return
        }
        state = .restoring
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            state = .success
        }
    }
}
