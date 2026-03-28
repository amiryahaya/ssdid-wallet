import SwiftUI
import SsdidCore

struct InstitutionalSetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let keyId: String

    enum InstitutionalState {
        case idle
        case enrolling
        case success(String)
        case error(String)
    }

    @State private var identity: Identity?
    @State private var state: InstitutionalState = .idle
    @State private var orgName = ""
    @State private var orgDid = ""
    @State private var encryptedKey = ""

    private var isEnrolling: Bool {
        if case .enrolling = state { return true }
        return false
    }

    private var hasRecoveryKey: Bool {
        identity?.hasRecoveryKey == true
    }

    private var enrollEnabled: Bool {
        hasRecoveryKey && !orgName.isEmpty && !orgDid.isEmpty && !encryptedKey.isEmpty && !isEnrolling
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

                Text("Institutional Recovery")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            switch state {
            case .success(let name):
                Spacer()
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 28)
                            .fill(Color.successDim)
                            .frame(width: 56, height: 56)
                        Image(systemName: "checkmark")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(Color.success)
                    }
                    Spacer().frame(height: 16)
                    Text("Enrolled Successfully")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 8)
                    Text(name)
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                    Spacer().frame(height: 24)
                    Button { router.pop() } label: { Text("Done") }
                        .buttonStyle(.ssdidPrimary)
                }
                .padding(.horizontal, 20)
                Spacer()

            default:
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Enroll with a trusted organization that can assist with identity recovery.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.textSecondary)

                        if !hasRecoveryKey {
                            Text("A recovery key is required before enrolling with an organization. Generate one in Recovery Setup first.")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.warning)
                                .padding(18)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.warningDim)
                                .cornerRadius(16)
                        }

                        // Organization Name
                        Text("ORGANIZATION NAME")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $orgName,
                                  prompt: Text("Organization name").foregroundStyle(Color.textTertiary))
                            .textFieldStyle(.plain)
                            .font(.ssdidBody)
                            .foregroundStyle(Color.textPrimary)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                        // Organization DID
                        Text("ORGANIZATION DID")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $orgDid,
                                  prompt: Text("did:ssdid:org...").foregroundStyle(Color.textTertiary))
                            .textFieldStyle(.plain)
                            .font(.ssdidMono)
                            .foregroundStyle(Color.textPrimary)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                        // Encrypted Key
                        Text("ENCRYPTED RECOVERY KEY")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        TextField("", text: $encryptedKey,
                                  prompt: Text("Base64-encoded encrypted key").foregroundStyle(Color.textTertiary),
                                  axis: .vertical)
                            .textFieldStyle(.plain)
                            .font(.ssdidMono)
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(3...5)
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

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

                        // Enroll button
                        Button {
                            enroll()
                        } label: {
                            if isEnrolling {
                                HStack(spacing: 10) {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    Text("Enrolling...")
                                }
                            } else {
                                Text("Enroll Organization")
                            }
                        }
                        .buttonStyle(.ssdidPrimary(enabled: enrollEnabled))
                        .disabled(!enrollEnabled)

                        Spacer().frame(height: 20)
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
        .background(Color.bgPrimary)
        .task {
            identity = await services.vault.getIdentity(keyId: keyId)
        }
    }

    private func enroll() {
        guard !orgDid.isEmpty && orgDid.hasPrefix("did:") else {
            state = .error("Invalid DID format")
            return
        }
        guard let identity = identity else {
            state = .error("Identity not found")
            return
        }
        state = .enrolling
        Task {
            do {
                let recoveryManager = RecoveryManager(
                    vault: services.vault,
                    storage: services.storage,
                    classicalProvider: services.classicalProvider,
                    pqcProvider: services.pqcProvider,
                    keychainManager: services.keychainManager
                )
                let institutionalManager = InstitutionalRecoveryManager(
                    recoveryManager: recoveryManager
                )
                let encryptedKeyData = Data(encryptedKey.utf8)
                _ = try await institutionalManager.enrollOrganization(
                    identity: identity,
                    orgDid: orgDid,
                    orgName: orgName,
                    encryptedRecoveryKey: encryptedKeyData
                )
                state = .success(orgName)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
}
