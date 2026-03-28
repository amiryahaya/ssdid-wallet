package my.ssdid.sdk.domain.sdjwt

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64

/**
 * Creates Key Binding JWTs (KB-JWT) per SD-JWT VC specification.
 *
 * The KB-JWT proves the presenter controls the key referenced in the
 * SD-JWT VC's `cnf` claim.
 */
object KeyBindingJwt {

    /**
     * Creates a KB-JWT.
     *
     * @param sdJwtWithDisclosures The SD-JWT + disclosures string (without KB-JWT), ending with ~
     * @param audience The verifier's identifier (aud claim)
     * @param nonce Verifier-provided nonce
     * @param algorithm JWA algorithm name (e.g., "EdDSA", "ES256")
     * @param signer Function that signs data with the holder's private key
     * @param issuedAt Unix timestamp
     */
    suspend fun create(
        sdJwtWithDisclosures: String,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: suspend (ByteArray) -> ByteArray,
        issuedAt: Long = System.currentTimeMillis() / 1000
    ): String {
        // Compute sd_hash = Base64url(SHA-256(SD-JWT + disclosures))
        val sdHash = computeSdHash(sdJwtWithDisclosures)

        val header = buildJsonObject {
            put("alg", algorithm)
            put("typ", "kb+jwt")
        }

        val payload = buildJsonObject {
            put("aud", audience)
            put("nonce", nonce)
            put("iat", issuedAt)
            put("sd_hash", sdHash)
        }

        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"
        val signature = signer(signingInput.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64UrlEncode(signature)

        return "$headerB64.$payloadB64.$signatureB64"
    }

    private fun computeSdHash(sdJwtWithDisclosures: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sdJwtWithDisclosures.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(digest)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
}
