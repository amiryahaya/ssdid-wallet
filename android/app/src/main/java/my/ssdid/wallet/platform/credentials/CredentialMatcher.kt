package my.ssdid.wallet.platform.credentials

import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.oid4vp.CredentialRef
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

/**
 * Matches stored credentials (SD-JWT VCs and mdocs) against a Digital Credentials
 * API request. The request typically contains a protocol (e.g., "openid4vp") and
 * a JSON request body that may include presentation_definition or dcql_query.
 *
 * This class extracts the matching logic from the CredentialProviderService so it
 * can be unit tested without Android system dependencies.
 */
class CredentialMatcher {

    /**
     * A matched credential entry for display in the system credential selector.
     */
    data class MatchedCredential(
        val id: String,
        val title: String,
        val subtitle: String?,
        val credentialRef: CredentialRef
    )

    /**
     * Matches SD-JWT VC credentials against the requested credential type.
     *
     * @param sdJwtVcs All stored SD-JWT VCs
     * @param requestedType Optional vct value to filter by (null matches all)
     * @return List of matched credentials
     */
    fun matchSdJwtCredentials(
        sdJwtVcs: List<StoredSdJwtVc>,
        requestedType: String? = null
    ): List<MatchedCredential> {
        return sdJwtVcs
            .filter { requestedType == null || it.type == requestedType }
            .map { vc ->
                MatchedCredential(
                    id = vc.id,
                    title = vc.type.substringAfterLast('/').substringAfterLast(':'),
                    subtitle = vc.issuer,
                    credentialRef = CredentialRef.SdJwt(vc)
                )
            }
    }

    /**
     * Matches mdoc credentials against the requested document type.
     *
     * @param mdocs All stored mdocs
     * @param requestedDocType Optional docType to filter by (null matches all)
     * @return List of matched credentials
     */
    fun matchMDocCredentials(
        mdocs: List<StoredMDoc>,
        requestedDocType: String? = null
    ): List<MatchedCredential> {
        return mdocs
            .filter { requestedDocType == null || it.docType == requestedDocType }
            .map { mdoc ->
                MatchedCredential(
                    id = mdoc.id,
                    title = mdoc.docType.substringAfterLast('.'),
                    subtitle = mdoc.docType,
                    credentialRef = CredentialRef.MDoc(mdoc)
                )
            }
    }

    /**
     * Matches all stored credentials against a Digital Credentials API request.
     *
     * Parses the request JSON to determine the credential format and type,
     * then returns all matching credentials from both SD-JWT VC and mdoc stores.
     *
     * @param sdJwtVcs All stored SD-JWT VCs
     * @param mdocs All stored mdocs
     * @param protocol The credential protocol (e.g., "openid4vp")
     * @param requestJson The JSON request body (may contain dcql_query or presentation_definition)
     * @return List of all matched credentials across both formats
     */
    fun matchAll(
        sdJwtVcs: List<StoredSdJwtVc>,
        mdocs: List<StoredMDoc>,
        protocol: String? = null,
        requestJson: String? = null
    ): List<MatchedCredential> {
        // For now, return all credentials as candidates.
        // When the protocol is "openid4vp" and the request contains a
        // presentation_definition or dcql_query, we could parse and filter
        // more precisely using PresentationDefinitionMatcher or DcqlMatcher.
        // That refinement will come once the end-to-end DC API flow is wired.
        val sdJwtMatches = matchSdJwtCredentials(sdJwtVcs)
        val mdocMatches = matchMDocCredentials(mdocs)
        return sdJwtMatches + mdocMatches
    }
}
