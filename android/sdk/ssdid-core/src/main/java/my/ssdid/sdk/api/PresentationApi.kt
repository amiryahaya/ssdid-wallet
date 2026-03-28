package my.ssdid.sdk.api

import my.ssdid.sdk.domain.oid4vp.AuthorizationRequest
import my.ssdid.sdk.domain.oid4vp.MatchResult
import my.ssdid.sdk.domain.oid4vp.OpenId4VpHandler
import my.ssdid.sdk.domain.oid4vp.PresentationReviewResult

class PresentationApi internal constructor(private val handler: OpenId4VpHandler) {
    suspend fun processRequest(uri: String): Result<PresentationReviewResult> =
        handler.processRequest(uri)

    suspend fun submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        selectedClaims: List<String>,
        algorithm: String,
        signer: CredentialSigner
    ): Result<Unit> = handler.submitPresentation(
        authRequest, matchResult, selectedClaims, algorithm
    ) { data -> signer.sign(data) }
}
