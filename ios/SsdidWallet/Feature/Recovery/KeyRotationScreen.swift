import SwiftUI

struct KeyRotationScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let keyId: String

    enum RotationState {
        case idle
        case preparing
        case rotating
        case success(String)
        case error(String)
    }

    @State private var identity: Identity?
    @State private var state: RotationState = .idle
    @State private var hasPreCommitment = false
    @State private var nextKeyHash: String?

    struct RotationHistoryEntry: Identifiable {
        let id = UUID()
        let oldKeyFragment: String
        let newKeyFragment: String
        let timestamp: String
    }

    @State private var rotationHistory: [RotationHistoryEntry] = []

    private var inProgress: Bool {
        if case .preparing = state { return true }
        if case .rotating = state { return true }
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

                Text("Key Rotation")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(spacing: 10) {
                    // Current key info
                    Text("CURRENT KEY")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let id = identity {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text("KEY ID")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.textTertiary)
                                Spacer()
                                AlgorithmBadge(
                                    name: id.algorithm.rawValue.replacingOccurrences(of: "_", with: "-"),
                                    isPostQuantum: id.algorithm.isPostQuantum
                                )
                            }
                            Text(id.keyId)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(Color.textPrimary)
                                .lineLimit(1)
                            Spacer().frame(height: 8)
                            Text("CREATED")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(String(id.createdAt.prefix(10)))
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textSecondary)
                        }
                        .ssdidCard()
                    }

                    // Pre-rotation status
                    Spacer().frame(height: 4)
                    Text("PRE-ROTATION STATUS")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    VStack(alignment: .leading, spacing: 0) {
                        if hasPreCommitment {
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundStyle(Color.success)
                                Text("Next key pre-committed")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.success)
                            }
                            if let hash = nextKeyHash {
                                Spacer().frame(height: 8)
                                Text("HASH")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.textTertiary)
                                Text(String(hash.prefix(24)) + "...")
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundStyle(Color.textSecondary)
                            }
                        } else {
                            HStack(spacing: 8) {
                                Image(systemName: "exclamationmark.triangle")
                                    .font(.system(size: 16))
                                    .foregroundStyle(Color.warning)
                                Text("No pre-commitment")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.warning)
                            }
                            Spacer().frame(height: 8)
                            Text("Generate a pre-committed next key for safe rotation.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textTertiary)
                        }
                    }
                    .ssdidCard()

                    // Action buttons
                    Spacer().frame(height: 8)

                    if !hasPreCommitment {
                        Button {
                            prepareRotation()
                        } label: {
                            if case .preparing = state {
                                HStack(spacing: 10) {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    Text("Preparing...")
                                }
                            } else {
                                Text("Prepare Pre-Commitment")
                            }
                        }
                        .buttonStyle(.ssdidPrimary(enabled: !inProgress))
                        .disabled(inProgress)
                    } else {
                        Button {
                            executeRotation()
                        } label: {
                            if case .rotating = state {
                                HStack(spacing: 10) {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: Color.bgPrimary))
                                    Text("Rotating...")
                                        .foregroundStyle(Color.bgPrimary)
                                }
                            } else {
                                HStack(spacing: 8) {
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                    Text("Rotate Now")
                                }
                                .foregroundStyle(Color.bgPrimary)
                            }
                        }
                        .font(.ssdidBody.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(!inProgress ? Color.warning : Color.bgElevated)
                        .cornerRadius(12)
                        .disabled(inProgress)
                    }

                    // Warning
                    Text("Current key remains valid for 5 minutes after rotation (grace period).")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.warning)
                        .lineSpacing(4)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.warningDim)
                        .cornerRadius(12)

                    // Status messages
                    if case .success(let message) = state {
                        Text(message)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.success)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.successDim)
                            .cornerRadius(12)
                    }
                    if case .error(let message) = state {
                        Text(message)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.danger)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.dangerDim)
                            .cornerRadius(12)
                    }

                    // Rotation history
                    if !rotationHistory.isEmpty {
                        Spacer().frame(height: 8)
                        Text("RECENT ROTATIONS")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        ForEach(rotationHistory) { entry in
                            HStack(spacing: 12) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Color.pqcDim)
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .font(.system(size: 14))
                                        .foregroundStyle(Color.pqc)
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("#\(entry.oldKeyFragment) -> #\(entry.newKeyFragment)")
                                        .font(.system(size: 13, design: .monospaced))
                                        .foregroundStyle(Color.textPrimary)
                                    Text(String(entry.timestamp.prefix(10)))
                                        .font(.system(size: 11))
                                        .foregroundStyle(Color.textTertiary)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                        }
                    }

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
        .task {
            identity = await services.vault.getIdentity(keyId: keyId)
            if let id = identity {
                let manager = makeRotationManager(for: id)
                let status = await manager.getRotationStatus(identity: id)
                hasPreCommitment = status.hasPreCommitment
                nextKeyHash = status.nextKeyHash
                rotationHistory = status.rotationHistory.map { entry in
                    RotationHistoryEntry(
                        oldKeyFragment: entry.oldKeyIdFragment,
                        newKeyFragment: entry.newKeyIdFragment,
                        timestamp: entry.timestamp
                    )
                }
            }
        }
    }

    private func makeRotationManager(for identity: Identity) -> KeyRotationManager {
        let provider: CryptoProvider = identity.algorithm.isPostQuantum
            ? services.pqcProvider
            : services.classicalProvider
        return KeyRotationManager(
            vault: services.vault,
            storage: services.storage,
            cryptoProvider: provider,
            keychainManager: services.keychainManager,
            ssdidClient: services.ssdidClient
        )
    }

    private func prepareRotation() {
        guard let id = identity else { return }
        state = .preparing
        Task {
            do {
                let manager = makeRotationManager(for: id)
                let hash = try await manager.prepareRotation(identity: id)
                identity = await services.vault.getIdentity(keyId: keyId)
                hasPreCommitment = true
                nextKeyHash = hash
                state = .success("Pre-commitment created and published to registry")
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    private func executeRotation() {
        guard let id = identity else { return }
        state = .rotating
        Task {
            do {
                let manager = makeRotationManager(for: id)
                let newIdentity = try await manager.executeRotation(identity: id)
                identity = newIdentity
                hasPreCommitment = false
                nextKeyHash = nil
                state = .success("Key rotated successfully. New key: \(newIdentity.keyId)")
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
}
