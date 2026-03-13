import SwiftUI

struct DriveLoginScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let serviceUrl: String
    let serviceName: String
    let challengeId: String
    let callbackUrl: String
    let requestedClaims: String

    struct ClaimItem: Identifiable {
        let id: String
        let key: String
        let required: Bool
    }

    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?
    @State private var selectedClaims: Set<String> = []
    @State private var isSubmitting = false
    @State private var isSuccess = false
    @State private var sessionToken: String?
    @State private var errorMessage: String?

    private var parsedClaims: [ClaimItem] {
        guard let data = requestedClaims.data(using: .utf8),
              let parsed = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return [
                ClaimItem(id: "name", key: "name", required: true),
                ClaimItem(id: "email", key: "email", required: false)
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

            // Error banner
            if let errorMessage {
                ErrorBanner(message: errorMessage, onDismiss: { self.errorMessage = nil })
                    .padding(.horizontal, 20)
                    .padding(.bottom, 4)
            }

            if isSuccess, let token = sessionToken {
                // Success state
                Spacer()
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(Color.green)
                    Text("Authenticated Successfully")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                    Text("You are now signed in to \(serviceName.isEmpty ? "the service" : serviceName).")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 40)
                Spacer()

                // Return button
                VStack(spacing: 8) {
                    Button {
                        returnToDrive(sessionToken: token)
                    } label: {
                        Text(callbackUrl.isEmpty ? "Done" : "Return to \(serviceName.isEmpty ? "SSDID Drive" : serviceName)")
                    }
                    .buttonStyle(.ssdidPrimary(enabled: true))
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            } else {
                // Normal consent flow
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        // Service info card
                        VStack(alignment: .leading, spacing: 4) {
                            Text(serviceName.isEmpty ? "Service" : serviceName)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(Color.textPrimary)
                            Text(serviceUrl)
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
                                Text("No identities found")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.textSecondary)
                                Button {
                                    router.push(.createIdentity())
                                } label: {
                                    Text("Create New Identity")
                                }
                                .buttonStyle(.ssdidPrimary)
                            }
                            .frame(maxWidth: .infinity)
                            .ssdidCard()
                        } else {
                            ForEach(identities) { identity in
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
                        }

                        // Requested information
                        if !parsedClaims.isEmpty {
                            Spacer().frame(height: 4)
                            Text("REQUESTED INFORMATION")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)

                            ForEach(parsedClaims) { claim in
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
                        }

                        // Authentication method
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

                    Button { router.pop() } label: {
                        Text("Decline")
                    }
                    .buttonStyle(.ssdidSecondary)
                    .disabled(isSubmitting)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
        .background(Color.bgPrimary)
        .task {
            await loadIdentities()
            for claim in parsedClaims where claim.required {
                selectedClaims.insert(claim.key)
            }
        }
    }

    // MARK: - Actions

    /// Load identities from the vault.
    private func loadIdentities() async {
        identities = await services.vault.listIdentities().filter { $0.isActive }
        // Auto-select if only one identity
        if identities.count == 1 {
            selectedIdentity = identities.first
        }
    }

    /// Perform real authentication: register if needed, then authenticate with Drive.
    private func approve() {
        guard !isSubmitting else { return }
        guard let identity = selectedIdentity else { return }

        isSubmitting = true
        errorMessage = nil

        Task {
            do {
                let driveApi = services.httpClient.driveApi(baseURL: serviceUrl)

                // Check if we already have a credential for this DID
                var credential = await services.vault.getCredentialForDid(identity.did)

                // If no credential, register with Drive first
                if credential == nil {
                    credential = try await registerWithDrive(identity: identity, driveApi: driveApi)
                }

                guard let vc = credential else {
                    errorMessage = "Failed to obtain credential"
                    isSubmitting = false
                    return
                }

                // Authenticate with the credential
                let response = try await driveApi.authenticate(
                    request: DriveAuthenticateRequest(
                        credential: vc,
                        challengeId: challengeId.isEmpty ? nil : challengeId
                    )
                )

                sessionToken = response.sessionToken
                isSuccess = true
                isSubmitting = false

                // If callback URL exists, auto-return after a short delay
                if !callbackUrl.isEmpty {
                    try? await Task.sleep(for: .seconds(1))
                    returnToDrive(sessionToken: response.sessionToken)
                }
            } catch {
                isSubmitting = false

                // Handle specific HTTP errors
                let message: String
                if let urlError = error as? URLError {
                    switch urlError.code {
                    case .notConnectedToInternet:
                        message = "No internet connection"
                    case .timedOut:
                        message = "Connection timed out"
                    default:
                        message = "Network error: \(urlError.localizedDescription)"
                    }
                } else {
                    let errorStr = error.localizedDescription
                    if errorStr.contains("401") {
                        // Credential expired — delete and retry
                        if let vc = await services.vault.getCredentialForDid(identity.did) {
                            try? await services.vault.deleteCredential(credentialId: vc.id)
                        }
                        message = "Credential expired. Please try again."
                    } else if errorStr.contains("404") {
                        message = "No account found. Please register first."
                    } else {
                        message = errorStr
                    }
                }
                errorMessage = message
            }
        }
    }

    /// Register with Drive: send DID, sign challenge, receive credential.
    private func registerWithDrive(identity: Identity, driveApi: DriveApi) async throws -> VerifiableCredential {
        // Step 1: Start registration
        let startResp = try await driveApi.register(request: RegisterStartRequest(
            did: identity.did,
            keyId: identity.keyId
        ))

        // Step 2: Sign the challenge
        let signatureBytes = try await services.vault.sign(keyId: identity.keyId, data: Data(startResp.challenge.utf8))
        let signedChallenge = Multibase.encode(signatureBytes)

        // Step 3: Complete registration — receive VC
        let verifyResp = try await driveApi.registerVerify(request: RegisterVerifyRequest(
            did: identity.did,
            keyId: identity.keyId,
            signedChallenge: signedChallenge
        ))

        // Step 4: Store the credential in vault
        let vc = verifyResp.credential
        try await services.vault.storeCredential(vc)
        return vc
    }

    /// Open the callback URL to return the session token to Drive.
    private func returnToDrive(sessionToken: String) {
        if callbackUrl.isEmpty {
            router.pop()
            return
        }

        guard var components = URLComponents(string: callbackUrl) else {
            router.pop()
            return
        }

        var items = components.queryItems ?? []
        items.append(URLQueryItem(name: "session_token", value: sessionToken))
        components.queryItems = items

        if let url = components.url, UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
        router.pop()
    }
}
