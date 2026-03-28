package my.ssdid.sdk.domain.oid4vp

import my.ssdid.sdk.domain.sdjwt.KeyBindingJwt
import my.ssdid.sdk.domain.sdjwt.SdJwtParser
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc

/**
 * Assembles SD-JWT VP tokens with selective disclosure and Key Binding JWT.
 *
 * Given a stored SD-JWT VC, selected claims, and verifier parameters,
 * builds a presentation token: issuerJwt~disclosure1~...~disclosureN~kbJwt
 */
object VpTokenBuilder {

    /**
     * Builds a VP token from a stored SD-JWT VC with selective disclosure.
     *
     * @param storedSdJwtVc The stored credential
     * @param selectedClaims Claim names the holder consents to disclose
     * @param audience The verifier's identifier (aud claim in KB-JWT)
     * @param nonce Verifier-provided nonce
     * @param algorithm JWA algorithm name (e.g., "EdDSA", "ES256")
     * @param signer Function that signs data with the holder's private key
     * @param issuedAt Unix timestamp for the KB-JWT (defaults to current time)
     * @return The assembled VP token string
     */
    fun build(
        storedSdJwtVc: StoredSdJwtVc,
        selectedClaims: List<String>,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray,
        issuedAt: Long = System.currentTimeMillis() / 1000
    ): String {
        val parsed = SdJwtParser.parse(storedSdJwtVc.compact)

        val selectedDisclosures = parsed.disclosures.filter { it.claimName in selectedClaims }

        val resolvedNames = selectedDisclosures.map { it.claimName }.toSet()
        val missing = selectedClaims.toSet() - resolvedNames
        require(missing.isEmpty()) {
            "Requested claims not found in credential disclosures: $missing"
        }

        // Build the SD-JWT with selected disclosures (without KB-JWT)
        val sdJwtWithDisclosures = buildString {
            append(parsed.issuerJwt)
            append("~")
            for (disc in selectedDisclosures) {
                append(disc.encoded)
                append("~")
            }
        }

        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtWithDisclosures,
            audience = audience,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer,
            issuedAt = issuedAt
        )

        return "$sdJwtWithDisclosures$kbJwt"
    }
}
