import SwiftUI

struct IdentityDetailScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let keyId: String

    @State private var identity: Identity?
    @State private var credentials: [VerifiableCredential] = []
    @State private var showDeactivateDialog = false
    @State private var isDeactivating = false
    @State private var didCopied = false
    @State private var errorMessage: String?

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

                Text("Identity Details")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            if let errorMessage {
                Text(errorMessage)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.danger)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.dangerDim)
                    .cornerRadius(8)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 4)
            }

            if let id = identity {
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        // Main info card
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text(id.name)
                                    .font(.ssdidTitle)
                                    .foregroundStyle(Color.textPrimary)
                                Spacer()
                                Text("Active")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.success)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 3)
                                    .background(Color.successDim)
                                    .cornerRadius(4)
                            }

                            Spacer().frame(height: 16)

                            // DID with copy
                            HStack {
                                Text("DID")
                                    .font(.ssdidCaption)
                                    .foregroundStyle(Color.textTertiary)
                                Spacer()
                                Button {
                                    UIPasteboard.general.string = id.did
                                    HapticManager.notification(.success)
                                    didCopied = true
                                    Task {
                                        try? await Task.sleep(for: .seconds(2))
                                        didCopied = false
                                    }
                                } label: {
                                    Text(didCopied ? "Copied" : "Copy")
                                        .font(.ssdidCaption)
                                        .foregroundStyle(Color.ssdidAccent)
                                }
                            }
                            .padding(.vertical, 8)

                            Text(id.did)
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundStyle(Color.textPrimary)

                            Spacer().frame(height: 8)
                            Divider().background(Color.ssdidBorder)

                            detailRow("Key ID", value: id.keyId, mono: true)
                            detailRow("Algorithm", value: id.algorithm.rawValue.replacingOccurrences(of: "_", with: " "))
                            detailRow("W3C Type", value: id.algorithm.w3cType, mono: true)
                            detailRow("Created", value: id.createdAt)
                            detailRow("Key Storage", value: "Hardware-backed")
                            detailRow("Public Key", value: String(id.publicKeyMultibase.prefix(30)) + "...", mono: true)
                        }
                        .ssdidCard()

                        // Profile
                        if id.profileName != nil || id.email != nil {
                            Spacer().frame(height: 4)
                            Text("PROFILE")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)

                            VStack(alignment: .leading, spacing: 8) {
                                if let profileName = id.profileName {
                                    HStack {
                                        Text("Name")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color.textSecondary)
                                        Spacer()
                                        Text(profileName)
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color.textPrimary)
                                    }
                                }
                                if let email = id.email {
                                    HStack {
                                        Text("Email")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color.textSecondary)
                                        Spacer()
                                        Text(email)
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color.textPrimary)
                                    }
                                }
                            }
                            .ssdidCard()
                        }

                        // Connected Services
                        Spacer().frame(height: 8)
                        Text("CONNECTED SERVICES")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textSecondary)

                        if credentials.isEmpty {
                            VStack(spacing: 8) {
                                Text("🔗")
                                    .font(.system(size: 32))
                                Text("No services connected")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.textSecondary)
                                Text("Scan a QR code to register with a service")
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.textTertiary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(24)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                        } else {
                            ForEach(credentials, id: \.id) { vc in
                                let status = serviceConnectionStatus(vc)
                                let name = serviceName(vc)
                                let url = serviceUrl(vc)

                                HStack(spacing: 12) {
                                    Circle()
                                        .fill(status.color)
                                        .frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(name)
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundStyle(Color.textPrimary)
                                        if let url = url {
                                            Text(url)
                                                .font(.system(size: 11))
                                                .foregroundStyle(Color.textTertiary)
                                                .lineLimit(1)
                                        }
                                        Text("Issued: \(String(vc.issuanceDate.prefix(10)))")
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.textTertiary)
                                    }
                                    Spacer()
                                    Text(status.label)
                                        .font(.system(size: 11))
                                        .foregroundStyle(status.color)
                                }
                                .padding(14)
                                .background(Color.bgCard)
                                .cornerRadius(12)
                            }
                        }

                        // Actions
                        Spacer().frame(height: 8)
                        Text("ACTIONS")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textSecondary)

                        Spacer().frame(height: 8)

                        HStack(spacing: 10) {
                            actionCard(icon: "key.fill", label: "Recovery") {
                                router.push(.recoverySetup(keyId: keyId))
                            }
                            actionCard(icon: "arrow.triangle.2.circlepath", label: "Rotate Key") {
                                router.push(.keyRotation(keyId: keyId))
                            }
                            actionCard(icon: "iphone.gen2", label: "Devices") {
                                router.push(.deviceManagement(keyId: keyId))
                            }
                        }

                        // Danger zone
                        Spacer().frame(height: 24)
                        Text("DANGER ZONE")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.danger)

                        Spacer().frame(height: 8)

                        Button {
                            showDeactivateDialog = true
                        } label: {
                            if isDeactivating {
                                HStack(spacing: 8) {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    Text("Deactivating...")
                                }
                            } else {
                                Text("Deactivate Identity")
                            }
                        }
                        .buttonStyle(.ssdidDanger)
                        .disabled(isDeactivating)

                        Spacer().frame(height: 24)
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .ssdidAccent))
                Spacer()
            }
        }
        .background(Color.bgPrimary)
        .alert("Deactivate Identity?", isPresented: $showDeactivateDialog) {
            Button("Cancel", role: .cancel) {}
            Button("Deactivate", role: .destructive) {
                deactivateIdentity()
            }
        } message: {
            if credentials.isEmpty {
                Text("This is irreversible. Your DID will be permanently deactivated on the registry and all associated data will be deleted.")
            } else {
                let serviceNames = credentials.map { serviceName($0) }
                let serviceList = serviceNames.joined(separator: ", ")
                Text("This identity is connected to \(credentials.count) service\(credentials.count == 1 ? "" : "s") (\(serviceList)). You may lose access to your data and accounts on these services.\n\nThis is irreversible. Your DID will be permanently deactivated on the registry.")
            }
        }
        .task {
            await loadIdentity()
            if let id = identity {
                credentials = await services.vault.getCredentialsForDid(id.did)
            }
        }
    }

    @ViewBuilder
    private func detailRow(_ label: String, value: String, mono: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.ssdidCaption)
                .foregroundStyle(Color.textTertiary)
            Text(value)
                .font(mono ? .ssdidMono : .system(size: 13))
                .foregroundStyle(Color.textPrimary)

            Spacer().frame(height: 8)
            Divider().background(Color.ssdidBorder)
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func actionCard(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.accentDim)
                        .frame(width: 42, height: 42)
                    Image(systemName: icon)
                        .font(.system(size: 18))
                        .foregroundStyle(Color.ssdidAccent)
                }
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .background(Color.bgCard)
            .cornerRadius(12)
        }
    }

    private func loadIdentity() async {
        let vault = services.vault
        identity = await vault.getIdentity(keyId: keyId)
        if identity == nil {
            errorMessage = "Identity not found for key: \(keyId)"
        }
    }

    private enum ServiceConnectionStatus {
        case active, expiring, expired

        var color: Color {
            switch self {
            case .active: return .success
            case .expiring: return .warning
            case .expired: return .danger
            }
        }

        var label: String {
            switch self {
            case .active: return "Active"
            case .expiring: return "Expiring soon"
            case .expired: return "Expired"
            }
        }
    }

    private func serviceConnectionStatus(_ vc: VerifiableCredential) -> ServiceConnectionStatus {
        guard let exp = vc.expirationDate else { return .active }
        let formatter = ISO8601DateFormatter()
        guard let expDate = formatter.date(from: exp) else { return .active }
        let now = Date()
        if expDate < now { return .expired }
        if expDate < Calendar.current.date(byAdding: .day, value: 30, to: now) ?? now { return .expiring }
        return .active
    }

    private func serviceName(_ vc: VerifiableCredential) -> String {
        // additionalProperties values are AnyCodable — extract the underlying String
        if let anyCodable = vc.credentialSubject.additionalProperties["service"],
           let name = anyCodable.value as? String, !name.isEmpty {
            // Capitalize known service names for display
            switch name.lowercased() {
            case "drive": return "SSDID Drive"
            default: return name.capitalized
            }
        }
        // Fallback: truncate issuer DID
        let issuer = vc.issuer
        if issuer.count > 30 {
            return String(issuer.prefix(20)) + "..." + String(issuer.suffix(8))
        }
        return issuer
    }

    private func serviceUrl(_ vc: VerifiableCredential) -> String? {
        guard let anyCodable = vc.credentialSubject.additionalProperties["serviceUrl"],
              let url = anyCodable.value as? String, !url.isEmpty else { return nil }
        return url
    }

    private func deactivateIdentity() {
        isDeactivating = true
        Task {
            do {
                // Deactivate on registry + delete locally + log activity
                try await services.ssdidClient.deactivateDid(keyId: keyId)
                isDeactivating = false
                router.pop()
            } catch {
                isDeactivating = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
