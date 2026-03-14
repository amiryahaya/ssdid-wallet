import Foundation

class OpenId4VpHandler {

    struct ProcessedRequest {
        let authRequest: AuthorizationRequest
        let matchResults: [MatchResult]
        let query: CredentialQuery
    }

    let transport: OpenId4VpTransport
    let peMatcher: PresentationDefinitionMatcher
    let dcqlMatcher: DcqlMatcher
    let vpTokenBuilder: VpTokenBuilder

    init(
        transport: OpenId4VpTransport,
        peMatcher: PresentationDefinitionMatcher,
        dcqlMatcher: DcqlMatcher,
        vpTokenBuilder: VpTokenBuilder
    ) {
        self.transport = transport
        self.peMatcher = peMatcher
        self.dcqlMatcher = dcqlMatcher
        self.vpTokenBuilder = vpTokenBuilder
    }

    /// Parse the authorization request URI, resolve by-reference if needed,
    /// then match stored credentials against the query.
    func processRequest(uri: String, storedVcs: [StoredSdJwtVc]) throws -> ProcessedRequest {
        // Step 1: Parse the URI
        let authRequest: AuthorizationRequest
        let parseResult = AuthorizationRequest.parse(uri)
        switch parseResult {
        case .success(let parsed):
            // If by-reference, fetch the request object
            if parsed.requestUri != nil {
                authRequest = try transport.fetchRequestObject(requestUri: parsed.requestUri!)
            } else {
                authRequest = parsed
            }
        case .failure(let error):
            throw error
        }

        // Step 2: Determine query type and match
        let matchResults: [MatchResult]
        let query: CredentialQuery

        if let pdJson = authRequest.presentationDefinition {
            query = peMatcher.toCredentialQuery(pdJson)
            matchResults = peMatcher.match(pdJson, storedCredentials: storedVcs)
        } else if let dcqlJson = authRequest.dcqlQuery {
            query = dcqlMatcher.toCredentialQuery(dcqlJson)
            matchResults = dcqlMatcher.match(dcqlJson, storedCredentials: storedVcs)
        } else {
            throw OpenId4VpError.noQuery
        }

        return ProcessedRequest(
            authRequest: authRequest,
            matchResults: matchResults,
            query: query
        )
    }

    /// Build and submit a VP token for the selected credential and claims.
    func submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        storedVc: StoredSdJwtVc,
        selectedClaims: Set<String>,
        algorithm: String,
        signer: @escaping (Data) -> Data
    ) throws {
        guard let responseUri = authRequest.responseUri else {
            throw OpenId4VpError.missingResponseUri
        }
        guard let nonce = authRequest.nonce else {
            throw OpenId4VpError.missingNonce
        }

        // Parse the stored compact SD-JWT VC
        let sdJwtVc = try SdJwtParser.parse(storedVc.compact)

        // Build the VP token
        let vpToken = try vpTokenBuilder.build(
            sdJwtVc: sdJwtVc,
            selectedClaimNames: selectedClaims,
            audience: authRequest.clientId,
            nonce: nonce,
            algorithm: algorithm,
            signer: signer
        )

        // Build presentation submission if this was a PE request
        let presentationSubmission: PresentationSubmission?
        if authRequest.presentationDefinition != nil {
            presentationSubmission = vpTokenBuilder.buildPresentationSubmission(
                definitionId: matchResult.descriptorId,
                descriptorId: matchResult.descriptorId
            )
        } else {
            presentationSubmission = nil
        }

        // Post the response
        try transport.postVpResponse(
            responseUri: responseUri,
            vpToken: vpToken,
            presentationSubmission: presentationSubmission,
            state: authRequest.state
        )
    }

    /// Decline the authorization request by posting an error response.
    func declineRequest(authRequest: AuthorizationRequest) throws {
        guard let responseUri = authRequest.responseUri else {
            // No response_uri means we cannot notify the verifier; just return.
            return
        }
        try transport.postError(
            responseUri: responseUri,
            error: "access_denied",
            state: authRequest.state
        )
    }
}
