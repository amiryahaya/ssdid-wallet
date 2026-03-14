package my.ssdid.wallet.domain.oid4vp

import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.domain.vault.Vault

class NoMatchingCredentialsException(message: String) : Exception(message)

data class PresentationReviewResult(
    val authRequest: AuthorizationRequest,
    val matches: List<MatchResult>
)

class OpenId4VpHandler(
    private val transport: OpenId4VpTransport,
    private val peMatcher: PresentationDefinitionMatcher,
    private val dcqlMatcher: DcqlMatcher,
    private val vault: Vault
) {

    fun processRequest(uri: String): Result<PresentationReviewResult> = runCatching {
        val parsed = AuthorizationRequest.parse(uri).getOrThrow()

        val authRequest = if (parsed.requestUri != null) {
            val json = transport.fetchRequestObject(parsed.requestUri)
            AuthorizationRequest.parseJson(json).getOrThrow()
        } else {
            parsed
        }

        val storedVcs = runBlocking { vault.listStoredSdJwtVcs() }

        val matches = when {
            authRequest.presentationDefinition != null ->
                peMatcher.match(authRequest.presentationDefinition, storedVcs)
            authRequest.dcqlQuery != null ->
                dcqlMatcher.match(authRequest.dcqlQuery, storedVcs)
            else -> emptyList()
        }

        if (matches.isEmpty()) {
            authRequest.responseUri?.let { responseUri ->
                runCatching { transport.postError(responseUri, "access_denied", authRequest.state) }
            }
            throw NoMatchingCredentialsException("No stored credentials match the request")
        }

        PresentationReviewResult(authRequest, matches)
    }

    fun submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        selectedClaims: List<String>,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): Result<Unit> = runCatching {
        val responseUri = authRequest.responseUri
            ?: throw IllegalStateException("No response_uri in authorization request")
        val nonce = authRequest.nonce
            ?: throw IllegalStateException("No nonce in authorization request")

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = matchResult.credential,
            selectedClaims = selectedClaims,
            audience = authRequest.clientId,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer
        )

        val definitionId = authRequest.presentationDefinition?.get("id")
            ?.let { it.toString().trim('"') }
            ?: throw IllegalStateException("Missing presentation_definition id")

        val submission = PresentationSubmission.create(
            definitionId = definitionId,
            descriptorIds = listOf(matchResult.descriptorId)
        )

        transport.postVpResponse(responseUri, vpToken, submission.toJson(), authRequest.state)
    }
}
