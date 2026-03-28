import Foundation

/// Facade for OpenID4VP presentation operations.
public struct PresentationApi {
    private let handler: OpenId4VpHandler

    init(handler: OpenId4VpHandler) {
        self.handler = handler
    }

    public func processRequest(uri: String) async throws -> PresentationReviewResult {
        try await handler.processRequest(uri: uri)
    }

    public func submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: VpMatchResult,
        selectedClaims: [String],
        algorithm: String,
        signer: @Sendable (Data) -> Data
    ) async throws {
        try await handler.submitPresentation(
            authRequest: authRequest,
            matchResult: matchResult,
            selectedClaims: selectedClaims,
            algorithm: algorithm,
            signer: signer
        )
    }
}
