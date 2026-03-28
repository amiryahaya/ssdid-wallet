import Foundation

/// Facade for OpenID4VCI credential issuance operations.
public struct IssuanceApi {
    private let handler: OpenId4VciHandler

    init(handler: OpenId4VciHandler) {
        self.handler = handler
    }

    public func processOffer(uri: String) async throws -> CredentialOfferReview {
        try await handler.processOffer(uri: uri)
    }

    public func processOfferJson(_ json: String) async throws -> CredentialOfferReview {
        try await handler.processOfferJson(json: json)
    }

    public func acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigId: String,
        txCode: String? = nil,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: @escaping @Sendable (Data) -> Data
    ) async throws -> IssuanceResult {
        try await handler.acceptOffer(
            offer: offer,
            metadata: metadata,
            selectedConfigId: selectedConfigId,
            txCode: txCode,
            walletDid: walletDid,
            keyId: keyId,
            algorithm: algorithm,
            signer: signer
        )
    }
}
