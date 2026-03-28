import SwiftUI
import SsdidCore

struct DeviceEnrollScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let keyId: String
    let mode: String // "primary" or "secondary"

    enum EnrollState {
        case idle
        case loading
        case waitingForSecondary(pairingId: String, challenge: String)
        case pairingJoined(pairingId: String, deviceName: String)
        case approved
        case joinSuccess
        case error(String)
    }

    @State private var state: EnrollState = .idle
    @State private var pairingIdInput = ""
    @State private var challengeInput = ""
    @State private var deviceNameInput = UIDevice.current.model
    @State private var pollingTask: Task<Void, Never>?

    private var isPrimary: Bool { mode == "primary" }

    private func makeDeviceManager() -> DeviceManager {
        DeviceManager(
            vault: services.vault,
            registryClient: HttpDeviceManagerRegistryClient(registryApi: services.httpClient.registry),
            ssdidClientProvider: SsdidClientDeviceProvider(ssdidClient: services.ssdidClient),
            deviceName: UIDevice.current.name,
            platform: "iOS"
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button {
                    pollingTask?.cancel()
                    router.pop()
                } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text(isPrimary ? "Enroll New Device" : "Join as Device")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(spacing: 12) {
                    if isPrimary {
                        primaryModeContent
                    } else {
                        secondaryModeContent
                    }

                    // Error
                    if case .error(let message) = state {
                        Text(message)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.danger)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.dangerDim)
                            .cornerRadius(12)
                    }

                    // Success states
                    if case .approved = state {
                        Text("Device approved and enrolled successfully")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.success)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.successDim)
                            .cornerRadius(12)
                    }

                    if case .joinSuccess = state {
                        Text("Successfully joined pairing. Waiting for approval from primary device.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.success)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.successDim)
                            .cornerRadius(12)
                    }

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
        .onDisappear {
            pollingTask?.cancel()
        }
    }

    @ViewBuilder
    private var primaryModeContent: some View {
        // Instructions
        Text("Start pairing to generate a code for the secondary device. The other device will need the pairing ID and challenge to join.")
            .font(.system(size: 12))
            .foregroundStyle(Color.ssdidAccent)
            .lineSpacing(4)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.accentDim)
            .cornerRadius(12)

        switch state {
        case .idle:
            Button {
                Task { await initiatePairing() }
            } label: {
                Text("Start Pairing")
            }
            .buttonStyle(.ssdidPrimary)

        case .loading:
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)

        case .waitingForSecondary(let pairingId, let challenge):
            VStack(alignment: .leading, spacing: 0) {
                Text("PAIRING ID")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text(pairingId)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(Color.textPrimary)
                Spacer().frame(height: 8)
                Text("CHALLENGE")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
                Text(challenge)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(Color.textPrimary)
                Spacer().frame(height: 12)
                HStack(spacing: 8) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                        .scaleEffect(0.8)
                    Text("Waiting for secondary device...")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .ssdidCard()

        case .pairingJoined(_, let deviceName):
            VStack(alignment: .leading, spacing: 4) {
                Text("Device wants to join:")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
                Text(deviceName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
            }
            .ssdidCard()

            Button {
                Task { await approvePairing() }
            } label: {
                Text("Approve Device")
                    .font(.ssdidBody.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.success)
                    .cornerRadius(12)
            }

        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private var secondaryModeContent: some View {
        // Instructions
        Text("Enter the pairing ID and challenge from the primary device to join.")
            .font(.system(size: 12))
            .foregroundStyle(Color.ssdidAccent)
            .lineSpacing(4)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.accentDim)
            .cornerRadius(12)

        if case .idle = state {
            formFields
        } else if case .error = state {
            formFields
        }
    }

    @ViewBuilder
    private var formFields: some View {
        TextField("", text: $pairingIdInput,
                  prompt: Text("Pairing ID").foregroundStyle(Color.textTertiary))
            .textFieldStyle(.plain)
            .font(.ssdidMono)
            .foregroundStyle(Color.textPrimary)
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

        TextField("", text: $challengeInput,
                  prompt: Text("Challenge").foregroundStyle(Color.textTertiary))
            .textFieldStyle(.plain)
            .font(.ssdidMono)
            .foregroundStyle(Color.textPrimary)
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

        TextField("", text: $deviceNameInput,
                  prompt: Text("Device Name").foregroundStyle(Color.textTertiary))
            .textFieldStyle(.plain)
            .font(.ssdidBody)
            .foregroundStyle(Color.textPrimary)
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

        Button {
            Task { await joinPairing() }
        } label: {
            Text("Join Pairing")
        }
        .buttonStyle(.ssdidPrimary(enabled: !pairingIdInput.isEmpty && !challengeInput.isEmpty))
        .disabled(pairingIdInput.isEmpty || challengeInput.isEmpty)
    }

    // MARK: - Actions

    private func initiatePairing() async {
        guard let identity = await services.vault.getIdentity(keyId: keyId) else {
            state = .error("Identity not found")
            return
        }

        state = .loading
        let deviceManager = makeDeviceManager()

        do {
            let pairingData = try await deviceManager.initiatePairing(identity: identity)
            state = .waitingForSecondary(pairingId: pairingData.pairingId, challenge: pairingData.challenge)

            // Start polling for secondary device joining
            pollingTask = Task {
                await pollPairingStatus(
                    deviceManager: deviceManager,
                    did: identity.did,
                    pairingId: pairingData.pairingId
                )
            }
        } catch {
            state = .error("Failed to initiate pairing: \(error.localizedDescription)")
        }
    }

    private func pollPairingStatus(deviceManager: DeviceManager, did: String, pairingId: String) async {
        // Poll every 3 seconds for up to 5 minutes
        let maxAttempts = 100
        for _ in 0..<maxAttempts {
            guard !Task.isCancelled else { return }

            do {
                try await Task.sleep(for: .seconds(3))
            } catch {
                return // Task cancelled
            }

            do {
                let status = try await deviceManager.checkPairingStatus(did: did, pairingId: pairingId)
                if status.status == "joined" || status.status == "pending_approval" {
                    await MainActor.run {
                        state = .pairingJoined(
                            pairingId: pairingId,
                            deviceName: status.deviceName ?? "Unknown Device"
                        )
                    }
                    return
                } else if status.status == "expired" || status.status == "rejected" {
                    await MainActor.run {
                        state = .error("Pairing session \(status.status)")
                    }
                    return
                }
            } catch {
                // Continue polling on transient errors
            }
        }

        await MainActor.run {
            state = .error("Pairing timed out. Please try again.")
        }
    }

    private func approvePairing() async {
        guard let identity = await services.vault.getIdentity(keyId: keyId) else {
            state = .error("Identity not found")
            return
        }

        guard case .pairingJoined(let pairingId, _) = state else { return }

        let deviceManager = makeDeviceManager()
        do {
            try await deviceManager.approvePairing(identity: identity, pairingId: pairingId)
            state = .approved
        } catch {
            state = .error("Failed to approve device: \(error.localizedDescription)")
        }
    }

    private func joinPairing() async {
        guard let identity = await services.vault.getIdentity(keyId: keyId) else {
            state = .error("Identity not found")
            return
        }

        state = .loading
        let deviceManager = makeDeviceManager()

        do {
            _ = try await deviceManager.joinPairing(
                did: identity.did,
                pairingId: pairingIdInput.trimmingCharacters(in: .whitespacesAndNewlines),
                challenge: challengeInput.trimmingCharacters(in: .whitespacesAndNewlines),
                identity: identity,
                deviceName: deviceNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            state = .joinSuccess
        } catch {
            state = .error("Failed to join pairing: \(error.localizedDescription)")
        }
    }
}
