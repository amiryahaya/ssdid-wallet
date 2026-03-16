import Foundation
import UIKit

@Observable
@MainActor
final class InviteAcceptViewModel {

    // MARK: - State

    var isLoading = true
    var invitation: InvitationDetailsResponse?
    var identities: [Identity] = []
    var selectedIdentity: Identity?
    var walletEmail = ""
    var emailMatch = false
    var errorMessage: String?
    var isAccepting = false
    var isSuccess = false
    var sessionToken: String?
    var shouldAutoReturn = false

    // MARK: - Private

    private let serverUrl: String
    private let token: String
    private let callbackUrl: String
    private let services: ServiceContainer
    private var acceptTask: Task<Void, Never>?
    private var hasReturned = false

    init(serverUrl: String, token: String, callbackUrl: String, services: ServiceContainer) {
        self.serverUrl = serverUrl
        self.token = token
        self.callbackUrl = callbackUrl
        self.services = services
    }

    // MARK: - Public

    func cancelAccept() {
        acceptTask?.cancel()
    }

    func loadData() async {
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

            // Use selected identity's email
            walletEmail = selectedIdentity?.email ?? ""

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
            guard !Task.isCancelled else { return }
            if let httpError = error as? HttpError,
               case .requestFailed(let statusCode, _) = httpError, statusCode == 404 {
                errorMessage = "Invitation not found or expired"
            } else {
                errorMessage = error.localizedDescription
            }
        }

        guard !Task.isCancelled else { return }
        isLoading = false
    }

    func accept() {
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
                    try await Task.sleep(for: .seconds(1))
                    guard !Task.isCancelled else { return }
                    shouldAutoReturn = true
                }
            } catch {
                if !Task.isCancelled {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    func returnToDrive(sessionToken: String, pop: () -> Void) {
        guard !hasReturned else { return }
        hasReturned = true

        if callbackUrl.isEmpty {
            pop()
            return
        }

        guard var components = URLComponents(string: callbackUrl) else {
            pop()
            return
        }

        var items = components.queryItems ?? []
        items.append(URLQueryItem(name: "session_token", value: sessionToken))
        items.append(URLQueryItem(name: "status", value: "success"))
        components.queryItems = items

        if let url = components.url, UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
        pop()
    }

    func decline(pop: () -> Void) {
        if !callbackUrl.isEmpty {
            guard var components = URLComponents(string: callbackUrl) else {
                pop()
                return
            }
            var items = components.queryItems ?? []
            items.append(URLQueryItem(name: "status", value: "cancelled"))
            components.queryItems = items
            if let url = components.url, UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
            }
        }
        pop()
    }

    // MARK: - Private

    private func registerWithDrive(identity: Identity, driveApi: DriveApi) async throws -> VerifiableCredential {
        let startResp = try await driveApi.register(request: RegisterStartRequest(
            did: identity.did,
            keyId: identity.keyId
        ))
        let signatureBytes = try await services.vault.sign(keyId: identity.keyId, data: Data(startResp.challenge.utf8))
        let signedChallenge = Multibase.encode(signatureBytes)
        let profileClaims = identity.claimsMap()
        let verifyResp = try await driveApi.registerVerify(request: RegisterVerifyRequest(
            did: identity.did,
            keyId: identity.keyId,
            signedChallenge: signedChallenge,
            inviteToken: token,
            sharedClaims: profileClaims.isEmpty ? nil : profileClaims
        ))
        let vc = verifyResp.credential
        try await services.vault.storeCredential(vc)
        return vc
    }
}
