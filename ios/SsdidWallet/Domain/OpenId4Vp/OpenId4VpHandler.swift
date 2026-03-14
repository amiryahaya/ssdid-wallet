import Foundation

/// Error types for OpenID4VP operations.
enum OpenId4VpError: Error, LocalizedError {
    case invalidRequest(String)
    case transportError(String)
    case noMatchingCredentials(String)

    var errorDescription: String? {
        switch self {
        case .invalidRequest(let reason):
            return "Invalid VP request: \(reason)"
        case .transportError(let reason):
            return "VP transport error: \(reason)"
        case .noMatchingCredentials(let reason):
            return "No matching credentials: \(reason)"
        }
    }
}

/// Result of processing an authorization request before user consent.
struct PresentationReviewResult {
    let authRequest: AuthorizationRequest
    let matches: [VpMatchResult]
}

/// Protocol for listing stored SD-JWT VCs, implemented by VaultStorage.
protocol SdJwtVcStore {
    func listSdJwtVcs() async -> [StoredSdJwtVc]
}

/// Protocol for listing stored mdoc credentials, implemented by VaultStorage.
protocol MDocStore {
    func listMDocs() async -> [StoredMDoc]
}

/// Orchestrates the OpenID4VP presentation flow.
final class OpenId4VpHandler {

    private let transport: OpenId4VpTransport
    private let peMatcher: PresentationDefinitionMatcher
    private let dcqlMatcher: DcqlMatcher
    private let vcStore: SdJwtVcStore
    private let mdocStore: MDocStore?

    init(
        transport: OpenId4VpTransport,
        peMatcher: PresentationDefinitionMatcher = PresentationDefinitionMatcher(),
        dcqlMatcher: DcqlMatcher = DcqlMatcher(),
        vcStore: SdJwtVcStore,
        mdocStore: MDocStore? = nil
    ) {
        self.transport = transport
        self.peMatcher = peMatcher
        self.dcqlMatcher = dcqlMatcher
        self.vcStore = vcStore
        self.mdocStore = mdocStore
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
        let storedMDocs = await mdocStore?.listMDocs() ?? []

        let matches: [VpMatchResult]
        if let pd = authRequest.presentationDefinition {
            matches = peMatcher.matchAll(pd: pd, sdJwtVcs: storedVcs, mdocs: storedMDocs)
        } else if let dcql = authRequest.dcqlQuery {
            matches = dcqlMatcher.matchAll(dcql: dcql, sdJwtVcs: storedVcs, mdocs: storedMDocs)
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
        signer: (Data) -> Data
    ) async throws {
        guard let responseUri = authRequest.responseUri else {
            throw OpenId4VpError.invalidRequest("No response_uri in authorization request")
        }
        guard let nonce = authRequest.nonce else {
            throw OpenId4VpError.invalidRequest("No nonce in authorization request")
        }

        let vpToken: String
        switch matchResult.credentialRef {
        case .sdJwt(let credential):
            vpToken = try VpTokenBuilder.build(
                storedSdJwtVc: credential,
                selectedClaims: selectedClaims,
                audience: authRequest.clientId,
                nonce: nonce,
                algorithm: algorithm,
                signer: signer
            )

        case .mdoc(let storedMDoc):
            // Convert selectedClaims to namespace->elements map
            // Claims should be in "namespace/element" format
            var requestedElements = [String: [String]]()
            for claim in selectedClaims {
                let parts = claim.split(separator: "/", maxSplits: 1)
                if parts.count == 2 {
                    let ns = String(parts[0])
                    let elem = String(parts[1])
                    requestedElements[ns, default: []].append(elem)
                }
            }
            // Fallback: treat claims as elements in the first namespace
            if requestedElements.isEmpty, let firstNs = storedMDoc.nameSpaces.keys.first {
                requestedElements[firstNs] = selectedClaims
            }

            guard let token = MDocVpTokenBuilder.build(
                storedMDoc: storedMDoc,
                requestedElements: requestedElements,
                clientId: authRequest.clientId,
                responseUri: responseUri,
                nonce: nonce,
                signer: signer
            ) else {
                throw OpenId4VpError.invalidRequest("Failed to build mdoc VP token")
            }
            vpToken = token
        }

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
