package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.ssdid.wallet.domain.sdjwt.SdJwtParser
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import java.util.UUID

class OpenId4VpHandler(
    private val transport: OpenId4VpTransport,
    private val peMatcher: PresentationDefinitionMatcher,
    private val dcqlMatcher: DcqlMatcher
) {
    data class ProcessedRequest(
        val authRequest: AuthorizationRequest,
        val matchResults: List<MatchResult>,
        val query: CredentialQuery
    )

    fun processRequest(uri: String, storedVcs: List<StoredSdJwtVc>): Result<ProcessedRequest> = runCatching {
        var authRequest = AuthorizationRequest.parse(uri).getOrThrow()

        // Fetch request object if by-reference
        if (authRequest.requestUri != null) {
            authRequest = transport.fetchRequestObject(authRequest.requestUri!!).getOrThrow()
        }

        // Match using appropriate query language
        val (matchResults, query) = when {
            authRequest.presentationDefinition != null -> {
                val q = peMatcher.toCredentialQuery(authRequest.presentationDefinition!!)
                peMatcher.match(authRequest.presentationDefinition!!, storedVcs) to q
            }
            authRequest.dcqlQuery != null -> {
                val q = dcqlMatcher.toCredentialQuery(authRequest.dcqlQuery!!)
                dcqlMatcher.match(authRequest.dcqlQuery!!, storedVcs) to q
            }
            else -> throw IllegalStateException("No query in authorization request")
        }

        if (matchResults.isEmpty()) {
            authRequest.responseUri?.let { responseUri ->
                transport.postError(responseUri, "no_credentials_available", authRequest.state)
            }
            throw NoMatchingCredentialsException("No stored credentials match the request")
        }

        ProcessedRequest(authRequest, matchResults, query)
    }

    fun submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        storedVc: StoredSdJwtVc,
        selectedClaims: Set<String>,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): Result<Unit> = runCatching {
        val sdJwtVc = SdJwtParser.parse(storedVc.compact)

        val vpToken = VpTokenBuilder.build(
            credential = sdJwtVc,
            selectedClaimNames = selectedClaims,
            audience = authRequest.clientId,
            nonce = authRequest.nonce ?: throw IllegalStateException("Missing nonce"),
            algorithm = algorithm,
            signer = signer
        )

        val submission = if (authRequest.presentationDefinition != null) {
            val pd = Json.parseToJsonElement(authRequest.presentationDefinition!!).jsonObject
            val definitionId = pd["id"]?.jsonPrimitive?.content ?: "unknown"
            PresentationSubmission(
                id = UUID.randomUUID().toString(),
                definitionId = definitionId,
                descriptorMap = listOf(
                    DescriptorMapEntry(
                        id = matchResult.descriptorId,
                        format = "vc+sd-jwt",
                        path = "$"
                    )
                )
            )
        } else null

        transport.postVpResponse(
            responseUri = authRequest.responseUri
                ?: throw IllegalStateException("Missing response_uri"),
            vpToken = vpToken,
            presentationSubmission = submission,
            state = authRequest.state
        ).getOrThrow()
    }

    fun declineRequest(authRequest: AuthorizationRequest): Result<Unit> = runCatching {
        authRequest.responseUri?.let { uri ->
            transport.postError(uri, "access_denied", authRequest.state).getOrThrow()
        }
    }
}

class NoMatchingCredentialsException(message: String) : Exception(message)
