import SwiftUI

struct InviteAcceptScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let serverUrl: String
    let token: String
    let callbackUrl: String

    @State private var invitation: InvitationDetailsResponse?
    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?
    @State private var walletEmail: String = ""
    @State private var emailMatch = false

    @State private var isLoading = true
    @State private var isAccepting = false
    @State private var isSuccess = false
    @State private var sessionToken: String?
    @State private var errorMessage: String?
    @State private var acceptTask: Task<Void, Never>?
    @State private var hasReturned = false

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { decline() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Invitation")
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

            if isLoading {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle())
                Text("Loading invitation...")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .padding(.top, 8)
                Spacer()
            } else if isSuccess, let token = sessionToken {
                // Success state
                Spacer()
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(Color.green)
                    Text("Invitation Accepted")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                    if let inv = invitation {
                        Text("You've joined \(inv.tenantName).")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.horizontal, 40)
                Spacer()

                VStack(spacing: 8) {
                    Button {
                        returnToDrive(sessionToken: token)
                    } label: {
                        Text(callbackUrl.isEmpty ? "Done" : "Return to SSDID Drive")
                    }
                    .buttonStyle(.ssdidPrimary(enabled: true))
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            } else if let inv = invitation {
                // Invitation details
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        // Invitation card
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "building.2")
                                    .foregroundStyle(Color.ssdidAccent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Organization")
                                        .font(.system(size: 11))
                                        .foregroundStyle(Color.textTertiary)
                                    Text(inv.tenantName)
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(Color.textPrimary)
                                }
                            }

                            if let inviter = inv.inviterName {
                                HStack(spacing: 8) {
                                    Image(systemName: "person")
                                        .foregroundStyle(Color.ssdidAccent)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Invited by")
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.textTertiary)
                                        Text(inviter)
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color.textPrimary)
                                    }
                                }
                            }

                            HStack(spacing: 8) {
                                Image(systemName: "envelope")
                                    .foregroundStyle(Color.ssdidAccent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Invitation email")
                                        .font(.system(size: 11))
                                        .foregroundStyle(Color.textTertiary)
                                    Text(inv.email)
                                        .font(.system(size: 14))
                                        .foregroundStyle(Color.textPrimary)
                                }
                            }

                            HStack(spacing: 8) {
                                Image(systemName: "person.badge.shield.checkmark")
                                    .foregroundStyle(Color.ssdidAccent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Role")
                                        .font(.system(size: 11))
                                        .foregroundStyle(Color.textTertiary)
                                    Text(inv.role.capitalized)
                                        .font(.system(size: 14))
                                        .foregroundStyle(Color.textPrimary)
                                }
                            }

                            if let msg = inv.message, !msg.isEmpty {
                                Divider()
                                Text("\"\(msg)\"")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.textSecondary)
                                    .italic()
                            }
                        }
                        .ssdidCard()

                        // Email verification status
                        if !walletEmail.isEmpty {
                            if emailMatch {
                                HStack(spacing: 8) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(Color.green)
                                    Text("Email verified: \(walletEmail)")
                                        .font(.system(size: 13))
                                        .foregroundStyle(Color.textSecondary)
                                }
                                .padding(.horizontal, 4)
                            } else {
                                HStack(spacing: 8) {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(Color.orange)
                                    Text("Email mismatch: invitation is for \(inv.email) but your wallet email is \(walletEmail)")
                                        .font(.system(size: 13))
                                        .foregroundStyle(Color.orange)
                                }
                                .padding(.horizontal, 4)
                            }
                        }

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
                                .disabled(isAccepting)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }

                // Footer buttons
                VStack(spacing: 8) {
                    Button {
                        accept()
                    } label: {
                        if isAccepting {
                            HStack(spacing: 10) {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text("Accepting...")
                            }
                        } else {
                            Text("Accept Invitation")
                        }
                    }
                    .buttonStyle(.ssdidPrimary(enabled: selectedIdentity != nil && emailMatch && !isAccepting))
                    .disabled(selectedIdentity == nil || !emailMatch || isAccepting)

                    Button { decline() } label: {
                        Text("Decline")
                    }
                    .buttonStyle(.ssdidSecondary)
                    .disabled(isAccepting)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
        .background(Color.bgPrimary)
        .task {
            await loadData()
        }
        .onDisappear { acceptTask?.cancel() }
    }

    // MARK: - Actions

    private func loadData() async {
        isLoading = true
        errorMessage = nil

        do {
            let driveApi = services.httpClient.driveApi(baseURL: serverUrl)
            let inv = try await driveApi.getInvitationByToken(token)
            invitation = inv

            // Load identities
            identities = await services.vault.listIdentities().filter { $0.isActive }
            if identities.count == 1 {
                selectedIdentity = identities.first
            }

            // Check email match from profile claims
            let profileManager = ProfileManager(vault: services.vault)
            let claims = await profileManager.getProfileClaims()
            walletEmail = claims["email"] ?? ""

            let normalizeEmail: (String) -> String = {
                $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            }
            emailMatch = !walletEmail.isEmpty && normalizeEmail(walletEmail) == normalizeEmail(inv.email)

            if !emailMatch && !walletEmail.isEmpty {
                errorMessage = "Email mismatch: invitation is for \(inv.email) but your wallet email is \(walletEmail)"
            } else if walletEmail.isEmpty {
                errorMessage = "No email configured in wallet profile"
            }
        } catch {
            if let httpError = error as? HttpError,
               case .requestFailed(let statusCode, _) = httpError, statusCode == 404 {
                errorMessage = "Invitation not found or expired"
            } else {
                errorMessage = error.localizedDescription
            }
        }

        isLoading = false
    }

    private func accept() {
        guard !isAccepting, let identity = selectedIdentity else { return }

        isAccepting = true
        errorMessage = nil

        acceptTask?.cancel()
        acceptTask = Task {
            defer { isAccepting = false }
            do {
                let driveApi = services.httpClient.driveApi(baseURL: serverUrl)

                // Get or register credential
                var credential = await services.vault.getCredentialForDid(identity.did)
                if credential == nil {
                    credential = try await registerWithDrive(identity: identity, driveApi: driveApi)
                }
                guard let vc = credential else {
                    errorMessage = "Failed to obtain credential"
                    return
                }

                // Accept with wallet
                let response = try await driveApi.acceptWithWallet(
                    token: token,
                    request: AcceptWithWalletRequest(credential: vc, email: walletEmail)
                )

                // Verify server signature -- blank fields = fatal
                guard !response.serverDid.isEmpty, !response.serverSignature.isEmpty else {
                    errorMessage = "Server did not provide identity proof. Please try again."
                    return
                }

                let verified = try await services.ssdidClient.verifyChallengeResponse(
                    did: response.serverDid,
                    keyId: response.serverKeyId,
                    challenge: response.sessionToken,
                    signedChallenge: response.serverSignature
                )

                guard verified else {
                    errorMessage = "Server identity verification failed. Please try again."
                    return
                }

                sessionToken = response.sessionToken
                isSuccess = true

                // Auto-return after short delay
                if !callbackUrl.isEmpty {
                    try? await Task.sleep(for: .seconds(1))
                    returnToDrive(sessionToken: response.sessionToken)
                }
            } catch {
                if !Task.isCancelled {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func registerWithDrive(identity: Identity, driveApi: DriveApi) async throws -> VerifiableCredential {
        let startResp = try await driveApi.register(request: RegisterStartRequest(
            did: identity.did,
            keyId: identity.keyId
        ))
        let signatureBytes = try await services.vault.sign(keyId: identity.keyId, data: Data(startResp.challenge.utf8))
        let signedChallenge = Multibase.encode(signatureBytes)
        let verifyResp = try await driveApi.registerVerify(request: RegisterVerifyRequest(
            did: identity.did,
            keyId: identity.keyId,
            signedChallenge: signedChallenge
        ))
        let vc = verifyResp.credential
        try await services.vault.storeCredential(vc)
        return vc
    }

    private func returnToDrive(sessionToken: String) {
        guard !hasReturned else { return }
        hasReturned = true

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
        items.append(URLQueryItem(name: "status", value: "success"))
        components.queryItems = items

        if let url = components.url, UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
        router.pop()
    }

    private func decline() {
        if !callbackUrl.isEmpty {
            guard var components = URLComponents(string: callbackUrl) else {
                router.pop()
                return
            }
            var items = components.queryItems ?? []
            items.append(URLQueryItem(name: "status", value: "cancelled"))
            components.queryItems = items
            if let url = components.url, UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
            }
        }
        router.pop()
    }
}
