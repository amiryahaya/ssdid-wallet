import Foundation

/// ViewModel for the OpenID4VCI credential offer flow.
///
/// State machine:
/// `Loading -> ReviewingOffer -> (PinEntry if tx_code) -> Processing -> Success/Deferred/Error`
@MainActor
@Observable
final class CredentialOfferViewModel {

    // MARK: - State

    enum UiState: Equatable {
        case loading
        case reviewingOffer(ReviewData)
        case pinEntry(PinData)
        case processing
        case success
        case deferred(transactionId: String)
        case error(String)

        static func == (lhs: UiState, rhs: UiState) -> Bool {
            switch (lhs, rhs) {
            case (.loading, .loading), (.processing, .processing), (.success, .success):
                return true
            case (.error(let a), .error(let b)):
                return a == b
            case (.deferred(let a), .deferred(let b)):
                return a == b
            case (.reviewingOffer(let a), .reviewingOffer(let b)):
                return a == b
            case (.pinEntry(let a), .pinEntry(let b)):
                return a == b
            default:
                return false
            }
        }
    }

    struct ReviewData: Equatable {
        let issuerName: String
        let credentialTypes: [String]
        var selectedConfigId: String
        let requiresPin: Bool
        let pinDescription: String?
        let pinLength: Int
        let pinInputMode: String
        let identities: [Identity]
        var selectedIdentity: Identity?
        let offer: CredentialOffer
        let metadata: IssuerMetadata

        static func == (lhs: ReviewData, rhs: ReviewData) -> Bool {
            lhs.issuerName == rhs.issuerName &&
            lhs.credentialTypes == rhs.credentialTypes &&
            lhs.selectedConfigId == rhs.selectedConfigId &&
            lhs.requiresPin == rhs.requiresPin &&
            lhs.selectedIdentity?.keyId == rhs.selectedIdentity?.keyId
        }
    }

    struct PinData: Equatable {
        let description: String?
        let length: Int
        let inputMode: String
        let offer: CredentialOffer
        let metadata: IssuerMetadata
        let selectedIdentity: Identity
        let selectedConfigId: String

        static func == (lhs: PinData, rhs: PinData) -> Bool {
            lhs.length == rhs.length &&
            lhs.inputMode == rhs.inputMode &&
            lhs.selectedIdentity.keyId == rhs.selectedIdentity.keyId &&
            lhs.selectedConfigId == rhs.selectedConfigId
        }
    }

    var state: UiState = .loading

    private let handler: OpenId4VciHandler
    private let vault: Vault

    init(handler: OpenId4VciHandler, vault: Vault) {
        self.handler = handler
        self.vault = vault
    }

    // MARK: - Actions

    func processOffer(rawUri: String) {
        state = .loading
        Task {
            do {
                let review = try await handler.processOffer(uri: rawUri)
                let identities = await vault.listIdentities()

                state = .reviewingOffer(ReviewData(
                    issuerName: review.metadata.credentialIssuer,
                    credentialTypes: review.credentialConfigNames,
                    selectedConfigId: review.credentialConfigNames.first ?? "",
                    requiresPin: review.offer.txCode != nil,
                    pinDescription: review.offer.txCode?.description,
                    pinLength: review.offer.txCode?.length ?? 0,
                    pinInputMode: review.offer.txCode?.inputMode ?? "numeric",
                    identities: identities,
                    selectedIdentity: identities.first,
                    offer: review.offer,
                    metadata: review.metadata
                ))
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    func processOfferJson(json: String) {
        state = .loading
        Task {
            do {
                let review = try await handler.processOfferJson(json: json)
                let identities = await vault.listIdentities()

                state = .reviewingOffer(ReviewData(
                    issuerName: review.metadata.credentialIssuer,
                    credentialTypes: review.credentialConfigNames,
                    selectedConfigId: review.credentialConfigNames.first ?? "",
                    requiresPin: review.offer.txCode != nil,
                    pinDescription: review.offer.txCode?.description,
                    pinLength: review.offer.txCode?.length ?? 0,
                    pinInputMode: review.offer.txCode?.inputMode ?? "numeric",
                    identities: identities,
                    selectedIdentity: identities.first,
                    offer: review.offer,
                    metadata: review.metadata
                ))
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    func selectIdentity(_ identity: Identity) {
        guard case .reviewingOffer(var data) = state else { return }
        data.selectedIdentity = identity
        state = .reviewingOffer(data)
    }

    func selectConfigId(_ configId: String) {
        guard case .reviewingOffer(var data) = state else { return }
        data.selectedConfigId = configId
        state = .reviewingOffer(data)
    }

    func acceptOffer() {
        guard case .reviewingOffer(let data) = state else { return }
        guard let identity = data.selectedIdentity else { return }

        if data.requiresPin {
            state = .pinEntry(PinData(
                description: data.pinDescription,
                length: data.pinLength,
                inputMode: data.pinInputMode,
                offer: data.offer,
                metadata: data.metadata,
                selectedIdentity: identity,
                selectedConfigId: data.selectedConfigId
            ))
            return
        }

        executeIssuance(
            offer: data.offer,
            metadata: data.metadata,
            identity: identity,
            selectedConfigId: data.selectedConfigId,
            txCode: nil
        )
    }

    func submitPin(_ pin: String) {
        guard case .pinEntry(let data) = state else { return }

        executeIssuance(
            offer: data.offer,
            metadata: data.metadata,
            identity: data.selectedIdentity,
            selectedConfigId: data.selectedConfigId,
            txCode: pin
        )
    }

    func decline() {
        state = .error("Declined by user")
    }

    // MARK: - Private

    private func executeIssuance(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        identity: Identity,
        selectedConfigId: String,
        txCode: String?
    ) {
        state = .processing
        Task {
            do {
                let algorithm = identity.algorithm.jwaName
                let keyId = identity.keyId
                let vaultRef = vault

                // Bridge async vault.sign to synchronous signer closure.
                nonisolated(unsafe) var signerResult = Data()
                let signer: @Sendable (Data) -> Data = { data in
                    let semaphore = DispatchSemaphore(value: 0)
                    signerResult = Data()
                    Task.detached { @Sendable in
                        do {
                            signerResult = try await vaultRef.sign(keyId: keyId, data: data)
                        } catch {
                            // Signing failure will produce empty signature
                        }
                        semaphore.signal()
                    }
                    semaphore.wait()
                    return signerResult
                }

                let issuanceResult = try await handler.acceptOffer(
                    offer: offer,
                    metadata: metadata,
                    selectedConfigId: selectedConfigId,
                    txCode: txCode,
                    walletDid: identity.did,
                    keyId: keyId,
                    algorithm: algorithm,
                    signer: signer
                )

                switch issuanceResult {
                case .success:
                    state = .success
                case .deferred(let transactionId, _, _):
                    state = .deferred(transactionId: transactionId)
                case .failed(let error):
                    state = .error(error)
                }
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
}
