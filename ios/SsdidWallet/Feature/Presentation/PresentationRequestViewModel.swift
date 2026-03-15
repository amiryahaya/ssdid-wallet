import Foundation

/// ViewModel for the OpenID4VP presentation request flow.
@MainActor
@Observable
final class PresentationRequestViewModel {

    // MARK: - State

    enum UiState: Equatable {
        case loading
        case credentialMatch(verifierName: String, claims: [ClaimItem], matchResult: VpMatchResult, authRequest: AuthorizationRequest)
        case submitting
        case success
        case error(String)

        static func == (lhs: UiState, rhs: UiState) -> Bool {
            switch (lhs, rhs) {
            case (.loading, .loading), (.submitting, .submitting), (.success, .success):
                return true
            case (.error(let a), .error(let b)):
                return a == b
            case (.credentialMatch(let vn1, let c1, _, let ar1), .credentialMatch(let vn2, let c2, _, let ar2)):
                return vn1 == vn2 && c1 == c2 && ar1 == ar2
            default:
                return false
            }
        }
    }

    struct ClaimItem: Identifiable, Equatable {
        let name: String
        let value: String
        let required: Bool
        var selected: Bool

        var id: String { name }
    }

    var state: UiState = .loading

    private let handler: OpenId4VpHandler
    private let vault: Vault

    init(handler: OpenId4VpHandler, vault: Vault) {
        self.handler = handler
        self.vault = vault
    }

    // MARK: - Actions

    func processRequest(rawUri: String) {
        state = .loading
        Task {
            do {
                let review = try await handler.processRequest(uri: rawUri)
                setReviewResult(review)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    func setReviewResult(_ review: PresentationReviewResult) {
        guard let match = review.matches.first else {
            state = .error("No matching credentials found")
            return
        }

        let claims = match.requiredClaims.map { name in
            ClaimItem(name: name, value: match.credential.claims[name] ?? "", required: true, selected: true)
        } + match.optionalClaims.map { name in
            ClaimItem(name: name, value: match.credential.claims[name] ?? "", required: false, selected: false)
        }

        state = .credentialMatch(
            verifierName: review.authRequest.clientId,
            claims: claims,
            matchResult: match,
            authRequest: review.authRequest
        )
    }

    func toggleClaim(name: String) {
        guard case .credentialMatch(let verifierName, var claims, let matchResult, let authRequest) = state else { return }
        guard let index = claims.firstIndex(where: { $0.name == name && !$0.required }) else { return }
        claims[index].selected.toggle()
        state = .credentialMatch(verifierName: verifierName, claims: claims, matchResult: matchResult, authRequest: authRequest)
    }

    func approve() {
        guard case .credentialMatch(_, let claims, let matchResult, let authRequest) = state else { return }
        state = .submitting

        Task {
            do {
                let identities = await vault.listIdentities()
                guard let identity = identities.first else {
                    state = .error("No identity available")
                    return
                }

                let selectedClaims = claims.filter(\.selected).map(\.name)
                let algorithm = identity.algorithm.jwaName
                let keyId = identity.keyId
                let vaultRef = vault

                // The signer closure is synchronous (required by VpTokenBuilder).
                // We bridge async vault.sign using a semaphore, similar to
                // Android's runBlocking pattern.
                nonisolated(unsafe) var signerResult: Data = Data()
                let signer: @Sendable (Data) -> Data = { data in
                    let semaphore = DispatchSemaphore(value: 0)
                    signerResult = Data()
                    Task.detached { @Sendable in
                        do {
                            signerResult = try await vaultRef.sign(keyId: keyId, data: data)
                        } catch {
                            // Signing failure will produce an empty signature,
                            // which the verifier will reject.
                        }
                        semaphore.signal()
                    }
                    semaphore.wait()
                    return signerResult
                }

                try await handler.submitPresentation(
                    authRequest: authRequest,
                    matchResult: matchResult,
                    selectedClaims: selectedClaims,
                    algorithm: algorithm,
                    signer: signer
                )
                state = .success
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    func decline() {
        state = .error("Declined by user")
    }
}

// MARK: - Algorithm JWA name extension

extension Algorithm {
    /// Returns the JWA (JSON Web Algorithm) name for this algorithm.
    var jwaName: String {
        switch self {
        case .ED25519:           return "EdDSA"
        case .ECDSA_P256:        return "ES256"
        case .ECDSA_P384:        return "ES384"
        case .KAZ_SIGN_128:      return "KAZ128"
        case .KAZ_SIGN_192:      return "KAZ192"
        case .KAZ_SIGN_256:      return "KAZ256"
        case .ML_DSA_44:         return "ML-DSA-44"
        case .ML_DSA_65:         return "ML-DSA-65"
        case .ML_DSA_87:         return "ML-DSA-87"
        case .SLH_DSA_SHA2_128S: return "SLH-DSA-SHA2-128s"
        case .SLH_DSA_SHA2_128F: return "SLH-DSA-SHA2-128f"
        case .SLH_DSA_SHA2_192S: return "SLH-DSA-SHA2-192s"
        case .SLH_DSA_SHA2_192F: return "SLH-DSA-SHA2-192f"
        case .SLH_DSA_SHA2_256S: return "SLH-DSA-SHA2-256s"
        case .SLH_DSA_SHA2_256F: return "SLH-DSA-SHA2-256f"
        case .SLH_DSA_SHAKE_128S: return "SLH-DSA-SHAKE-128s"
        case .SLH_DSA_SHAKE_128F: return "SLH-DSA-SHAKE-128f"
        case .SLH_DSA_SHAKE_192S: return "SLH-DSA-SHAKE-192s"
        case .SLH_DSA_SHAKE_192F: return "SLH-DSA-SHAKE-192f"
        case .SLH_DSA_SHAKE_256S: return "SLH-DSA-SHAKE-256s"
        case .SLH_DSA_SHAKE_256F: return "SLH-DSA-SHAKE-256f"
        }
    }
}
