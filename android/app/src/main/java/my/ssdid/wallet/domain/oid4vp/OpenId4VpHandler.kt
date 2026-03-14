package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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

    suspend fun processRequest(uri: String): Result<PresentationReviewResult> = runCatching {
        val parsed = AuthorizationRequest.parse(uri).getOrThrow()

        val authRequest = if (parsed.requestUri != null) {
            val json = transport.fetchRequestObject(parsed.requestUri)
            AuthorizationRequest.parseJson(json).getOrThrow()
        } else {
            parsed
        }

        val storedVcs = vault.listStoredSdJwtVcs()
        val storedMDocs = vault.listMDocs()

        val matches = when {
            authRequest.presentationDefinition != null ->
                peMatcher.matchAll(authRequest.presentationDefinition, storedVcs, storedMDocs)
            authRequest.dcqlQuery != null ->
                dcqlMatcher.matchAll(authRequest.dcqlQuery, storedVcs, storedMDocs)
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

    suspend fun submitPresentation(
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

        val vpToken = when (val ref = matchResult.credentialRef) {
            is CredentialRef.SdJwt -> VpTokenBuilder.build(
                storedSdJwtVc = ref.credential,
                selectedClaims = selectedClaims,
                audience = authRequest.clientId,
                nonce = nonce,
                algorithm = algorithm,
                signer = signer
            )
            is CredentialRef.MDoc -> {
                // Convert selectedClaims to namespace->elements map
                val requestedElements = selectedClaims.groupBy(
                    keySelector = { it.substringBefore("/") },
                    valueTransform = { it.substringAfter("/") }
                ).ifEmpty {
                    // Fallback: treat claims as elements in the first namespace
                    ref.credential.nameSpaces.keys.firstOrNull()?.let { ns ->
                        mapOf(ns to selectedClaims)
                    } ?: emptyMap()
                }
                MDocVpTokenBuilder.build(
                    storedMDoc = ref.credential,
                    requestedElements = requestedElements,
                    clientId = authRequest.clientId,
                    responseUri = responseUri,
                    nonce = nonce,
                    signer = signer
                )
            }
        }

        val definitionId = when {
            authRequest.presentationDefinition != null ->
                authRequest.presentationDefinition["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalStateException("Missing presentation_definition id")
            authRequest.dcqlQuery != null ->
                matchResult.descriptorId  // DCQL uses the credential query id
            else -> throw IllegalStateException("No query type in authorization request")
        }

        val submission = PresentationSubmission.create(
            definitionId = definitionId,
            descriptorIds = listOf(matchResult.descriptorId)
        )

        transport.postVpResponse(responseUri, vpToken, submission.toJson(), authRequest.state)
    }
}
