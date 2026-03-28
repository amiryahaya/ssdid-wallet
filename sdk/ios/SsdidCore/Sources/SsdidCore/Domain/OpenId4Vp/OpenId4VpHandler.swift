import Foundation

/// Result of processing an authorization request before user consent.
struct PresentationReviewResult {
    let authRequest: AuthorizationRequest
    let matches: [VpMatchResult]
}

/// Protocol for listing stored SD-JWT VCs, implemented by VaultStorage.
protocol SdJwtVcStore {
    func listSdJwtVcs() async -> [StoredSdJwtVc]
}

/// Orchestrates the OpenID4VP presentation flow.
final class OpenId4VpHandler: @unchecked Sendable {

    private let transport: OpenId4VpTransport
    private let peMatcher: PresentationDefinitionMatcher
    private let dcqlMatcher: DcqlMatcher
    private let vcStore: SdJwtVcStore

    init(
        transport: OpenId4VpTransport,
        peMatcher: PresentationDefinitionMatcher = PresentationDefinitionMatcher(),
        dcqlMatcher: DcqlMatcher = DcqlMatcher(),
        vcStore: SdJwtVcStore
    ) {
        self.transport = transport
        self.peMatcher = peMatcher
        self.dcqlMatcher = dcqlMatcher
        self.vcStore = vcStore
    }

    /// Processes an authorization request URI: parses, resolves request_uri if present,
    /// matches credentials, and returns matches for user review.
    func processRequest(uri: String) async throws -> PresentationReviewResult {
        let parsed = try AuthorizationRequest.parse(uri)

        let authRequest: AuthorizationRequest
        if let requestUri = parsed.requestUri {
            let json = try await transport.fetchRequestObject(requestUri: requestUri)
            authRequest = try AuthorizationRequest.parseJson(json)
        } else {
            authRequest = parsed
        }

        let storedVcs = await vcStore.listSdJwtVcs()

        let matches: [VpMatchResult]
        if let pd = authRequest.presentationDefinition {
            matches = peMatcher.match(pd: pd, credentials: storedVcs)
        } else if let dcql = authRequest.dcqlQuery {
            matches = dcqlMatcher.match(dcql: dcql, credentials: storedVcs)
        } else {
            matches = []
        }

        if matches.isEmpty {
            if let responseUri = authRequest.responseUri {
                try? await transport.postError(
                    responseUri: responseUri,
                    error: "access_denied",
                    state: authRequest.state
                )
            }
            throw OpenId4VpError.noMatchingCredentials("No stored credentials match the request")
        }

        return PresentationReviewResult(authRequest: authRequest, matches: matches)
    }

    /// Submits a VP presentation after user consent.
    func submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: VpMatchResult,
        selectedClaims: [String],
        algorithm: String,
        signer: @Sendable (Data) -> Data
    ) async throws {
        guard let responseUri = authRequest.responseUri else {
            throw OpenId4VpError.invalidRequest("No response_uri in authorization request")
        }
        guard let nonce = authRequest.nonce else {
            throw OpenId4VpError.invalidRequest("No nonce in authorization request")
        }

        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: matchResult.credential,
            selectedClaims: selectedClaims,
            audience: authRequest.clientId,
            nonce: nonce,
            algorithm: algorithm,
            signer: signer
        )

        // Determine definition ID from PE or DCQL query
        let definitionId: String
        if let pd = authRequest.presentationDefinition,
           let pdId = pd["id"] as? String {
            definitionId = pdId
        } else if authRequest.dcqlQuery != nil {
            definitionId = matchResult.descriptorId
        } else {
            throw OpenId4VpError.invalidRequest("Missing presentation_definition id or dcql_query")
        }

        let submission = PresentationSubmission.create(
            definitionId: definitionId,
            descriptorIds: [matchResult.descriptorId]
        )

        try await transport.postVpResponse(
            responseUri: responseUri,
            vpToken: vpToken,
            presentationSubmission: try submission.toJson(),
            state: authRequest.state
        )
    }
}
