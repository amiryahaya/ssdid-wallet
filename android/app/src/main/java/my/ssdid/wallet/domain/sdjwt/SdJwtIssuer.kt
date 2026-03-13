package my.ssdid.wallet.domain.sdjwt

import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.util.Base64

/**
 * Issues SD-JWT VCs with selective disclosure.
 *
 * @param signer Function that signs data with the issuer's private key and returns the signature.
 * @param algorithm JWA algorithm name for the JWT header (e.g., "EdDSA", "ES256").
 */
class SdJwtIssuer(
    private val signer: (ByteArray) -> ByteArray,
    private val algorithm: String
) {

    /**
     * Issues an SD-JWT VC.
     *
     * @param issuer DID of the issuer
     * @param subject DID of the subject
     * @param type VC type array (e.g., ["VerifiableCredential", "VerifiedEmployee"])
     * @param claims All claims as key-value pairs
     * @param disclosable Claim names that should be selectively disclosable
     * @param holderKeyJwk JWK of the holder's public key for key binding (cnf claim)
     * @param issuedAt Unix timestamp
     * @param expiresAt Unix timestamp
     */
    fun issue(
        issuer: String,
        subject: String,
        type: List<String>,
        claims: Map<String, JsonElement>,
        disclosable: Set<String>,
        holderKeyJwk: JsonObject? = null,
        issuedAt: Long = System.currentTimeMillis() / 1000,
        expiresAt: Long? = null
    ): SdJwtVc {
        val disclosures = mutableListOf<Disclosure>()
        val sdHashes = mutableListOf<String>()
        val visibleClaims = mutableMapOf<String, JsonElement>()

        for ((name, value) in claims) {
            if (name in disclosable) {
                val salt = generateSalt()
                val disclosure = Disclosure(salt, name, value)
                disclosures.add(disclosure)
                sdHashes.add(disclosure.hash())
            } else {
                visibleClaims[name] = value
            }
        }

        // Build JWT payload
        val payload = buildJsonObject {
            put("iss", issuer)
            put("sub", subject)
            put("vct", type.lastOrNull() ?: "VerifiableCredential")
            put("iat", issuedAt)
            expiresAt?.let { put("exp", it) }
            put("_sd_alg", "sha-256")
            putJsonArray("_sd") {
                sdHashes.forEach { add(it) }
            }
            // Always-visible claims
            for ((name, value) in visibleClaims) {
                put(name, value)
            }

            // Key binding
            holderKeyJwk?.let { jwk ->
                putJsonObject("cnf") {
                    put("jwk", jwk)
                }
            }
        }

        // Build JWT header
        val header = buildJsonObject {
            put("alg", algorithm)
            put("typ", "vc+sd-jwt")
        }

        // Encode and sign
        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"
        val signature = signer(signingInput.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64UrlEncode(signature)
        val issuerJwt = "$headerB64.$payloadB64.$signatureB64"

        return SdJwtVc(
            issuerJwt = issuerJwt,
            disclosures = disclosures,
            keyBindingJwt = null
        )
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
}
