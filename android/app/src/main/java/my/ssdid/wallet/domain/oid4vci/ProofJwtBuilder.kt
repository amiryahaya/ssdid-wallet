package my.ssdid.wallet.domain.oid4vci

import kotlinx.serialization.json.*
import java.util.Base64

/**
 * Builds proof-of-possession JWTs for OpenID4VCI credential requests.
 *
 * The proof JWT demonstrates that the wallet controls the key material
 * associated with the DID, as required by the OID4VCI specification.
 */
object ProofJwtBuilder {

    /**
     * Builds a proof JWT with the OpenID4VCI proof type.
     *
     * @param algorithm JWA algorithm name (e.g., "EdDSA", "ES256")
     * @param keyId DID URL identifying the key (e.g., "did:ssdid:abc#key-1")
     * @param walletDid The wallet's DID (iss claim)
     * @param issuerUrl The credential issuer URL (aud claim)
     * @param nonce The c_nonce from the token/credential response
     * @param signer Function that signs the input bytes with the holder's private key
     * @param issuedAt Unix timestamp for the iat claim
     * @return Compact JWS string (header.payload.signature)
     */
    fun build(
        algorithm: String,
        keyId: String,
        walletDid: String,
        issuerUrl: String,
        nonce: String,
        signer: (ByteArray) -> ByteArray,
        issuedAt: Long = System.currentTimeMillis() / 1000
    ): String {
        val header = buildJsonObject {
            put("typ", "openid4vci-proof+jwt")
            put("alg", algorithm)
            put("kid", keyId)
        }

        val payload = buildJsonObject {
            put("iss", walletDid)
            put("aud", issuerUrl)
            put("iat", issuedAt)
            put("exp", issuedAt + 120L)
            put("nonce", nonce)
        }

        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"
        val signature = signer(signingInput.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64UrlEncode(signature)

        return "$headerB64.$payloadB64.$signatureB64"
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
}
