import SwiftUI
import SsdidCore

struct CredentialDetailScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let credentialId: String

    @State private var credential: VerifiableCredential?
    @State private var showRawJson = false
    @State private var showDeleteDialog = false
    @State private var isVerifying = false
    @State private var freshnessRatio: Double = 0.0

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 4) {
                    Button { router.pop() } label: {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(Color.textPrimary)
                            .font(.system(size: 20))
                    }
                    .padding(.leading, 8)

                    Text("Credential Details")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                }

                Spacer()

                Button { showDeleteDialog = true } label: {
                    Text("Delete")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.danger)
                }
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            if let vc = credential {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        // Main info card
                        VStack(alignment: .leading, spacing: 0) {
                            let isExpired = isCredentialExpired(vc)
                            HStack {
                                Text(vc.type.last ?? "Credential")
                                    .font(.ssdidHeadline)
                                    .foregroundStyle(Color.textPrimary)
                                if vc.type.contains("SdJwtVerifiableCredential") {
                                    Text("SD-JWT VC")
                                        .font(.system(size: 9, weight: .semibold))
                                        .foregroundStyle(Color.ssdidAccent)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.accentDim)
                                        .cornerRadius(4)
                                }
                                Spacer()
                                Text(isExpired ? "Expired" : "Valid")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundStyle(isExpired ? Color.danger : Color.success)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 3)
                                    .background(isExpired ? Color.dangerDim : Color.successDim)
                                    .cornerRadius(4)
                            }

                            Spacer().frame(height: 16)

                            credDetailRow("ID", value: vc.id, mono: true)
                            credDetailRow("Type", value: vc.type.joined(separator: ", "))
                            credDetailRow("Issuer", value: vc.issuer, mono: true)
                            credDetailRow("Issuance Date", value: vc.issuanceDate)
                            if let exp = vc.expirationDate {
                                credDetailRow("Expiration Date", value: exp)
                            }
                        }
                        .ssdidCard()

                        // Credential subject
                        VStack(alignment: .leading, spacing: 0) {
                            Text("CREDENTIAL SUBJECT")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)

                            Spacer().frame(height: 12)

                            credDetailRow("Subject ID", value: vc.credentialSubject.id, mono: true)

                            if !vc.credentialSubject.claims.isEmpty {
                                Spacer().frame(height: 4)
                                Text("CLAIMS")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(Color.textTertiary)
                                Spacer().frame(height: 8)
                                ForEach(Array(vc.credentialSubject.claims.keys.sorted()), id: \.self) { key in
                                    if let value = vc.credentialSubject.claims[key] {
                                        credDetailRow(key, value: value)
                                    }
                                }
                            } else {
                                Spacer().frame(height: 8)
                                Text("No additional claims")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.textTertiary)
                            }
                        }
                        .ssdidCard()

                        // Proof
                        VStack(alignment: .leading, spacing: 0) {
                            Text("PROOF")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)

                            Spacer().frame(height: 12)

                            credDetailRow("Type", value: vc.proof.type)
                            credDetailRow("Created", value: vc.proof.created)
                            credDetailRow("Purpose", value: vc.proof.proofPurpose)
                            credDetailRow("Verification Method", value: vc.proof.verificationMethod, mono: true)
                        }
                        .ssdidCard()

                        // Bundle freshness badge
                        BundleFreshnessBadge(freshnessRatio: freshnessRatio)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)

                        // Verify Credential button
                        Button {
                            guard let vc = credential else { return }
                            let orchestrator = services.verificationOrchestrator
                            isVerifying = true
                            Task {
                                let result = await orchestrator.verify(credential: vc)
                                await MainActor.run {
                                    isVerifying = false
                                    router.push(.verificationResult(result: result))
                                }
                            }
                        } label: {
                            Label(isVerifying ? "Verifying..." : "Verify Credential", systemImage: "checkmark.shield")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .disabled(isVerifying)
                        .foregroundStyle(Color.ssdidAccent)
                        .font(.system(size: 14))

                        // Raw JSON toggle
                        Button {
                            showRawJson.toggle()
                        } label: {
                            Text(showRawJson ? "Hide Raw JSON" : "Show Raw JSON")
                                .font(.system(size: 14))
                                .foregroundStyle(Color.ssdidAccent)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }

                        if showRawJson {
                            VStack(alignment: .leading) {
                                Text(prettyPrintJson(vc))
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(Color.textSecondary)
                                    .lineSpacing(4)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.bgSecondary)
                            .cornerRadius(12)
                        }

                        Spacer().frame(height: 20)
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                Spacer()
            }
        }
        .background(Color.bgPrimary)
        .alert("Delete Credential", isPresented: $showDeleteDialog) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                Task {
                    if let vc = credential {
                        try? await services.vault.deleteCredential(credentialId: vc.id)
                        try? await services.credentialRepository.deleteCredential(credentialId: vc.id)
                    }
                    router.pop()
                }
            }
        } message: {
            Text("Are you sure you want to delete this credential? This action cannot be undone.")
        }
        .task {
            // Load the credential from vault
            let all = await services.vault.listCredentials()
            credential = all.first { $0.id == credentialId }

            // Compute bundle freshness for this credential's issuer
            if let vc = credential {
                if let bundle = await services.bundleStore.getBundle(issuerDid: vc.issuer) {
                    freshnessRatio = services.ttlProvider.freshnessRatio(fetchedAt: bundle.fetchedAt)
                }
            }
        }
    }

    @ViewBuilder
    private func credDetailRow(_ label: String, value: String, mono: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(Color.textTertiary)
            Text(value)
                .font(.system(size: 13, design: mono ? .monospaced : .default))
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 6)
            Divider().background(Color.ssdidBorder)
        }
        .padding(.vertical, 6)
    }

    private func isCredentialExpired(_ vc: VerifiableCredential) -> Bool {
        guard let exp = vc.expirationDate else { return false }
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: exp) else { return false }
        return Date() > date
    }

    private func prettyPrintJson(_ vc: VerifiableCredential) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(vc),
              let str = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return str
    }
}
