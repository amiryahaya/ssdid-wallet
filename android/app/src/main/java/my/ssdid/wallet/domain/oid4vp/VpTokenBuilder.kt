package my.ssdid.wallet.domain.oid4vp

import my.ssdid.wallet.domain.sdjwt.KeyBindingJwt
import my.ssdid.wallet.domain.sdjwt.SdJwtVc

/**
 * Builds a VP Token from an SD-JWT VC for OpenID4VP presentation.
 *
 * Steps:
 * 1. Filter disclosures to only user-approved claims
 * 2. Build SD-JWT without KB-JWT (for sd_hash computation)
 * 3. Create KB-JWT with aud=client_id, nonce, and sd_hash
 * 4. Assemble the final presentation string
 */
object VpTokenBuilder {

    /**
     * Builds a VP Token string from the given SD-JWT VC credential.
     *
     * @param credential The SD-JWT VC to present
     * @param selectedClaimNames Set of claim names the holder approves to disclose
     * @param audience The verifier's client_id (KB-JWT aud claim)
     * @param nonce Verifier-provided nonce for freshness
     * @param algorithm JWA algorithm name (e.g., "EdDSA", "ES256")
     * @param signer Function that signs data with the holder's private key
     * @return The assembled VP Token string: issuerJwt~disclosure1~...~disclosureN~kbJwt
     */
    fun build(
        credential: SdJwtVc,
        selectedClaimNames: Set<String>,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): String {
        // Step 1: Filter disclosures to user-approved claims
        val selectedDisclosures = credential.disclosures.filter { it.claimName in selectedClaimNames }

        // Step 2: Build SD-JWT without KB-JWT (ending with ~) for sd_hash
        val sdJwtWithoutKb = credential.present(selectedDisclosures, kbJwt = null)

        // Step 3: Create KB-JWT
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtWithoutKb,
            audience = audience,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer
        )

        // Step 4: Assemble final presentation with KB-JWT
        return credential.present(selectedDisclosures, kbJwt = kbJwt)
    }
}
