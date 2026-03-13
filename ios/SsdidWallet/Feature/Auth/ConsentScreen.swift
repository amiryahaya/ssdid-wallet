import SwiftUI

struct ConsentScreen: View {
    @Environment(AppRouter.self) private var router

    let serverUrl: String
    let callbackUrl: String
    let sessionId: String
    let requestedClaims: String
    let acceptedAlgorithms: String?

    struct ClaimItem: Identifiable {
        let id: String
        let key: String
        let required: Bool
    }

    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?
    @State private var selectedClaims: Set<String> = []
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private var claims: [ClaimItem] {
        // Parse requestedClaims JSON
        guard let data = requestedClaims.data(using: .utf8),
              let parsed = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return [
                ClaimItem(id: "name", key: "name", required: true),
                ClaimItem(id: "email", key: "email", required: true),
                ClaimItem(id: "phone", key: "phone", required: false)
            ]
        }
        return parsed.map {
            ClaimItem(id: $0["key"] ?? "", key: $0["key"] ?? "", required: $0["required"] == "true")
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

                Text("Sign In Request")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            if let errorMessage {
                ErrorBanner(message: errorMessage, onDismiss: { self.errorMessage = nil })
                    .padding(.horizontal, 20)
                    .padding(.bottom, 4)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    // Service info card
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Service")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.textPrimary)
                        Text(serverUrl)
                            .font(.ssdidMono)
                            .foregroundStyle(Color.textTertiary)
                            .lineLimit(1)
                        Spacer().frame(height: 4)
                        Text("This service is requesting to verify your identity and access selected information.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.textSecondary)
                    }
                    .ssdidCard()

                    // Identity section
                    Spacer().frame(height: 4)
                    Text("IDENTITY")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    if identities.isEmpty {
                        VStack(spacing: 12) {
                            Text("No compatible identities found")
                                .font(.system(size: 14))
                                .foregroundStyle(Color.textSecondary)
                            Button {
                                router.push(.createIdentity(acceptedAlgorithms: acceptedAlgorithms))
                            } label: {
                                Text("Create New Identity")
                            }
                            .buttonStyle(.ssdidPrimary)
                        }
                        .frame(maxWidth: .infinity)
                        .ssdidCard()
                    } else {
                        ForEach(identities) { identity in
                            identityRow(identity)
                        }
                    }

                    // Requested information section
                    Spacer().frame(height: 4)
                    Text("REQUESTED INFORMATION")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    ForEach(claims) { claim in
                        claimRow(claim)
                    }

                    // Authentication section
                    Spacer().frame(height: 4)
                    Text("AUTHENTICATION")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.accentDim)
                                .frame(width: 36, height: 36)
                            Image(systemName: "faceid")
                                .font(.system(size: 18))
                                .foregroundStyle(Color.ssdidAccent)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Biometric + Hardware Key")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.textPrimary)
                            Text("Your identity will be confirmed using biometric authentication and a hardware-backed cryptographic key.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textTertiary)
                        }
                    }
                    .ssdidCard()
                }
                .padding(.horizontal, 20)
            }

            // Footer buttons
            VStack(spacing: 8) {
                Button {
                    approve()
                } label: {
                    if isSubmitting {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Approving...")
                        }
                    } else {
                        Text("Approve")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: selectedIdentity != nil && !isSubmitting))
                .disabled(selectedIdentity == nil || isSubmitting)

                Button {
                    router.pop()
                } label: {
                    Text("Decline")
                }
                .buttonStyle(.ssdidSecondary)
                .disabled(isSubmitting)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Color.bgPrimary)
        .onAppear {
            // Pre-select required claims
            for claim in claims where claim.required {
                selectedClaims.insert(claim.key)
            }
        }
    }

    @ViewBuilder
    private func identityRow(_ identity: Identity) -> some View {
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
                AlgorithmBadge(
                    name: identity.algorithm.rawValue.replacingOccurrences(of: "_", with: "-"),
                    isPostQuantum: identity.algorithm.isPostQuantum
                )
            }
            .padding(14)
            .background(isSelected ? Color.accentDim : Color.bgCard)
            .cornerRadius(12)
        }
        .disabled(isSubmitting)
    }

    @ViewBuilder
    private func claimRow(_ claim: ClaimItem) -> some View {
        let isSelected = selectedClaims.contains(claim.key)
        Button {
            if !claim.required {
                if isSelected { selectedClaims.remove(claim.key) }
                else { selectedClaims.insert(claim.key) }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                Text(claim.key.capitalized)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text(claim.required ? "Required" : "Optional")
                    .font(.system(size: 11))
                    .foregroundStyle(claim.required ? Color.ssdidAccent : Color.textTertiary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(claim.required ? Color.accentDim : Color.bgPrimary)
                    .cornerRadius(6)
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
        }
        .disabled(claim.required || isSubmitting)
    }

    private func approve() {
        isSubmitting = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isSubmitting = false
            router.pop()
        }
    }
}
