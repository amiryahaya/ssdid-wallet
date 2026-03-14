import SwiftUI

struct PresentationRequestScreen: View {
    @Environment(AppRouter.self) private var router

    let uri: String

    @State private var state: ViewState = .loading
    @State private var selectedClaims: Set<String> = []

    private let handler: OpenId4VpHandler

    init(uri: String) {
        self.uri = uri
        let transport = OpenId4VpTransport()
        self.handler = OpenId4VpHandler(
            transport: transport,
            peMatcher: PresentationDefinitionMatcher(),
            dcqlMatcher: DcqlMatcher(),
            vpTokenBuilder: VpTokenBuilder()
        )
    }

    enum ViewState {
        case loading
        case matched(ProcessedInfo)
        case submitting
        case success
        case error(String)
        case noCredentials
    }

    struct ProcessedInfo {
        let authRequest: AuthorizationRequest
        let matchResult: MatchResult
        let credentialType: String
        let claims: [ClaimEntry]
    }

    struct ClaimEntry: Identifiable {
        let id: String
        let name: String
        let required: Bool
        let available: Bool
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
                .accessibilityLabel("Back")
                .padding(.leading, 8)

                Text("Presentation Request")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            Group {
                switch state {
                case .loading:
                    Spacer()
                    ProgressView("Processing request...")
                    Spacer()
                case .matched(let info):
                    matchedView(info)
                case .submitting:
                    Spacer()
                    VStack(spacing: 12) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                        Text("Sharing presentation...")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)
                    }
                    Spacer()
                case .success:
                    successView()
                case .error(let msg):
                    errorView(msg)
                case .noCredentials:
                    noCredentialsView()
                }
            }
        }
        .background(Color.bgPrimary)
        .onAppear { processRequest() }
    }

    @ViewBuilder
    private func matchedView(_ info: ProcessedInfo) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                // Verifier info card
                VStack(alignment: .leading, spacing: 4) {
                    Text("Verifier")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                    Text(info.authRequest.clientId)
                        .font(.ssdidMono)
                        .foregroundStyle(Color.textTertiary)
                        .lineLimit(1)
                    Spacer().frame(height: 4)
                    Text("This verifier is requesting to view selected credentials from your wallet.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                }
                .ssdidCard()

                // Credential type section
                Spacer().frame(height: 4)
                Text("CREDENTIAL")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.accentDim)
                            .frame(width: 36, height: 36)
                        Image(systemName: "person.text.rectangle")
                            .font(.system(size: 18))
                            .foregroundStyle(Color.ssdidAccent)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(info.credentialType)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.textPrimary)
                        Text("Matching credential found")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.textTertiary)
                    }
                    Spacer()
                }
                .ssdidCard()

                // Claims section
                Spacer().frame(height: 4)
                Text("REQUESTED INFORMATION")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                ForEach(info.claims) { claim in
                    claimRow(claim)
                }
            }
            .padding(.horizontal, 20)
        }

        // Footer buttons
        VStack(spacing: 8) {
            Button {
                approve(info)
            } label: {
                Text("Approve")
            }
            .buttonStyle(.ssdidPrimary(enabled: true))

            Button {
                decline(info)
            } label: {
                Text("Decline")
            }
            .buttonStyle(.ssdidSecondary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func claimRow(_ claim: ClaimEntry) -> some View {
        let isSelected = selectedClaims.contains(claim.name)
        Button {
            if !claim.required {
                if isSelected { selectedClaims.remove(claim.name) }
                else { selectedClaims.insert(claim.name) }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                    .accessibilityLabel(isSelected ? "\(claim.name.capitalized) selected" : "\(claim.name.capitalized) not selected")
                Text(claim.name.capitalized)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                if !claim.available {
                    Text("Unavailable")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.bgPrimary)
                        .cornerRadius(6)
                } else {
                    Text(claim.required ? "Required" : "Optional")
                        .font(.system(size: 11))
                        .foregroundStyle(claim.required ? Color.ssdidAccent : Color.textTertiary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(claim.required ? Color.accentDim : Color.bgPrimary)
                        .cornerRadius(6)
                }
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
        }
        .disabled(claim.required)
    }

    @ViewBuilder
    private func successView() -> some View {
        Spacer()
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.ssdidAccent)
            Text("Presentation shared successfully")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.textPrimary)
        }
        Spacer()
        Button {
            router.pop()
        } label: {
            Text("Done")
        }
        .buttonStyle(.ssdidPrimary(enabled: true))
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func errorView(_ message: String) -> some View {
        Spacer()
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 60))
                .foregroundStyle(.orange)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
            Button {
                processRequest()
            } label: {
                Text("Try Again")
            }
            .buttonStyle(.ssdidPrimary(enabled: true))
            .padding(.horizontal, 40)
        }
        Spacer()
    }

    @ViewBuilder
    private func noCredentialsView() -> some View {
        Spacer()
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.textTertiary)
            Text("No matching credentials")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.textPrimary)
            Text("Your wallet does not contain credentials that match this request.")
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        Spacer()
        Button {
            router.pop()
        } label: {
            Text("Go Back")
        }
        .buttonStyle(.ssdidSecondary)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - Actions

    private func processRequest() {
        state = .loading
        // Load stored VCs (empty for now - real implementation would load from vault)
        let storedVcs: [StoredSdJwtVc] = []

        do {
            let result = try handler.processRequest(uri: uri, storedVcs: storedVcs)
            guard let firstMatch = result.matchResults.first else {
                state = .noCredentials
                return
            }

            let claims = firstMatch.availableClaims.values
                .sorted { $0.name < $1.name }
                .map { ClaimEntry(id: $0.name, name: $0.name, required: $0.required, available: $0.available) }

            // Pre-select all available claims
            for claim in claims where claim.available {
                selectedClaims.insert(claim.name)
            }

            state = .matched(ProcessedInfo(
                authRequest: result.authRequest,
                matchResult: firstMatch,
                credentialType: firstMatch.credentialType,
                claims: claims
            ))
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    private func approve(_ info: ProcessedInfo) {
        state = .submitting
        // Build selected claims set from toggle state
        // Real implementation would retrieve stored VC and signer from vault
        state = .success
    }

    private func decline(_ info: ProcessedInfo) {
        try? handler.declineRequest(authRequest: info.authRequest)
        router.pop()
    }
}
