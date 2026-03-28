import SwiftUI
import SsdidCore

struct SocialRecoverySetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let keyId: String

    struct GuardianEntry: Identifiable {
        let id = UUID()
        var name: String = ""
        var did: String = ""
    }

    enum SocialSetupState {
        case idle
        case creating
        case success([(name: String, share: String)])
        case error(String)
    }

    @State private var state: SocialSetupState = .idle
    @State private var guardians: [GuardianEntry] = [GuardianEntry(), GuardianEntry()]
    @State private var threshold = 2
    @State private var showConfirmDialog = false

    private var isCreating: Bool {
        if case .creating = state { return true }
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

                Text("Social Recovery Setup")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            switch state {
            case .success(let shares):
                successContent(shares)

            default:
                setupForm
            }
        }
        .background(Color.bgPrimary)
        .alert("Overwrite Existing Config?", isPresented: $showConfirmDialog) {
            Button("Cancel", role: .cancel) {}
            Button("Confirm") { createShares() }
        } message: {
            Text("This will replace your existing social recovery configuration.")
        }
    }

    private var setupForm: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(spacing: 12) {
                    // Threshold card
                    VStack(spacing: 0) {
                        Text("Recovery requires \(threshold) of \(guardians.count) guardians to reconstruct your key.")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)

                        Spacer().frame(height: 8)

                        HStack(spacing: 16) {
                            Button {
                                if threshold > 2 { threshold -= 1 }
                            } label: {
                                Image(systemName: "minus")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(Color.textPrimary)
                                    .frame(width: 44, height: 36)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))
                            }
                            .disabled(threshold <= 2 || isCreating)

                            Text("\(threshold) of \(guardians.count)")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(Color.textPrimary)

                            Button {
                                if threshold < guardians.count { threshold += 1 }
                            } label: {
                                Image(systemName: "plus")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(Color.textPrimary)
                                    .frame(width: 44, height: 36)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))
                            }
                            .disabled(threshold >= guardians.count || isCreating)
                        }

                        Spacer().frame(height: 8)

                        Text("\(threshold) guardians needed to recover your identity")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.textTertiary)
                    }
                    .ssdidCard()

                    // Guardian cards
                    ForEach(Array(guardians.enumerated()), id: \.element.id) { index, _ in
                        guardianCard(index: index)
                    }

                    // Add guardian
                    Button {
                        guardians.append(GuardianEntry())
                    } label: {
                        Text("Add Guardian")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.ssdidAccent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidAccent.opacity(0.3)))
                    }
                    .disabled(isCreating)

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

                    // Create shares button
                    Button {
                        createShares()
                    } label: {
                        if isCreating {
                            HStack(spacing: 10) {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text("Creating Shares...")
                            }
                        } else {
                            Text("Create Recovery Shares")
                        }
                    }
                    .buttonStyle(.ssdidPrimary(enabled: !isCreating))
                    .disabled(isCreating)

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
    }

    @ViewBuilder
    private func guardianCard(index: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Guardian \(index + 1)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                if guardians.count > 2 {
                    Button {
                        guardians.remove(at: index)
                        if threshold > guardians.count { threshold = guardians.count }
                    } label: {
                        Text("Remove")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.danger)
                    }
                    .disabled(isCreating)
                }
            }

            TextField("", text: binding(for: index, keyPath: \.name),
                      prompt: Text("Guardian name").foregroundStyle(Color.textTertiary))
                .textFieldStyle(.plain)
                .font(.ssdidBody)
                .foregroundStyle(Color.textPrimary)
                .padding(12)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))
                .disabled(isCreating)

            TextField("", text: binding(for: index, keyPath: \.did),
                      prompt: Text("did:ssdid:...").foregroundStyle(Color.textTertiary))
                .textFieldStyle(.plain)
                .font(.ssdidMono)
                .foregroundStyle(Color.textPrimary)
                .padding(12)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.ssdidBorder))
                .disabled(isCreating)
        }
        .ssdidCard()
    }

    @ViewBuilder
    private func successContent(_ shares: [(name: String, share: String)]) -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                // Success banner
                HStack(spacing: 12) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color.success)
                    Text("Social recovery configured successfully!")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.success)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.successDim)
                .cornerRadius(16)

                // Warning
                VStack(alignment: .leading, spacing: 8) {
                    Text("IMPORTANT")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color.warning)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 3)
                        .background(Color.warningDim)
                        .cornerRadius(4)

                    Text("Distribute each share to its guardian securely. Each guardian should only receive their own share.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.warning)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.warningDim)
                .cornerRadius(16)

                // Share cards
                ForEach(Array(shares.enumerated()), id: \.offset) { index, entry in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(entry.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.textPrimary)
                        Spacer().frame(height: 4)
                        Text("Share \(index + 1)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Color.textTertiary)
                        Spacer().frame(height: 4)
                        Text(entry.share)
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(3)
                        Spacer().frame(height: 12)
                        Button {
                            UIPasteboard.general.string = entry.share
                        } label: {
                            Text("Copy Share")
                        }
                        .buttonStyle(.ssdidPrimary)
                    }
                    .ssdidCard()
                }

                Button { router.pop() } label: { Text("Done") }
                    .buttonStyle(.ssdidPrimary)

                Spacer().frame(height: 20)
            }
            .padding(.horizontal, 20)
        }
    }

    private func binding(for index: Int, keyPath: WritableKeyPath<GuardianEntry, String>) -> Binding<String> {
        Binding(
            get: { guardians[index][keyPath: keyPath] },
            set: { guardians[index][keyPath: keyPath] = $0 }
        )
    }

    private func createShares() {
        for g in guardians {
            if g.name.isEmpty || g.did.isEmpty {
                state = .error("All guardians must have a name and DID")
                return
            }
            if !g.did.hasPrefix("did:") {
                state = .error("All guardian DIDs must be valid DID format")
                return
            }
        }
        state = .creating
        Task {
            do {
                guard let identity = await services.vault.getIdentity(keyId: keyId) else {
                    state = .error("Identity not found")
                    return
                }
                let recoveryManager = RecoveryManager(
                    vault: services.vault,
                    storage: services.storage,
                    classicalProvider: services.classicalProvider,
                    pqcProvider: services.pqcProvider,
                    keychainManager: services.keychainManager
                )
                let socialManager = SocialRecoveryManager(
                    recoveryManager: recoveryManager,
                    vault: services.vault
                )
                let shareMap = try await socialManager.setupSocialRecovery(
                    identity: identity,
                    guardianNames: guardians.map { ($0.name, $0.did) },
                    threshold: threshold
                )
                // Map guardian IDs back to names for display
                let config = socialManager.getConfig(did: identity.did)
                let shares: [(name: String, share: String)] = guardians.enumerated().compactMap { index, guardian in
                    if let config = config,
                       index < config.guardians.count,
                       let share = shareMap[config.guardians[index].id] {
                        return (name: guardian.name, share: share)
                    }
                    return nil
                }
                state = .success(shares)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
}
