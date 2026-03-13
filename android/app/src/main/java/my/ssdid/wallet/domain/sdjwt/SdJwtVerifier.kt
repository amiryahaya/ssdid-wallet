package my.ssdid.wallet.domain.sdjwt

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.did.DidResolver
import my.ssdid.wallet.domain.model.Algorithm
import java.util.Base64

/**
 * Verifies SD-JWT VCs: JWT signature, disclosure hash matching,
 * expiration, and key binding.
 */
class SdJwtVerifier(
    private val didResolver: DidResolver,
    private val classicalVerify: (Algorithm, ByteArray, ByteArray, ByteArray) -> Boolean,
    private val pqcVerify: (Algorithm, ByteArray, ByteArray, ByteArray) -> Boolean
) {
    /**
     * Verification result containing the verified claims.
     */
    data class VerificationResult(
        val verified: Boolean,
        val issuer: String,
        val subject: String?,
        val disclosedClaims: Map<String, String>,
        val errors: List<String> = emptyList()
    )

    /**
     * Verifies an SD-JWT VC.
     *
     * @param sdJwtVc The parsed SD-JWT VC
     * @param expectedAudience Expected audience for KB-JWT (if present)
     * @param expectedNonce Expected nonce for KB-JWT (if present)
     */
    suspend fun verify(
        sdJwtVc: SdJwtVc,
        expectedAudience: String? = null,
        expectedNonce: String? = null
    ): VerificationResult {
        val errors = mutableListOf<String>()

        // 1. Decode JWT header and payload
        val jwtParts = sdJwtVc.issuerJwt.split(".")
        if (jwtParts.size != 3) {
            return VerificationResult(false, "", null, emptyMap(), listOf("Invalid JWT structure"))
        }

        val header = decodeJsonPart(jwtParts[0])
        val payload = decodeJsonPart(jwtParts[1])
        val signatureBytes = Base64.getUrlDecoder().decode(jwtParts[2])

        val algName = header["alg"]?.jsonPrimitive?.content
            ?: return VerificationResult(false, "", null, emptyMap(), listOf("Missing alg in header"))

        val issuerDid = payload["iss"]?.jsonPrimitive?.content
            ?: return VerificationResult(false, "", null, emptyMap(), listOf("Missing iss in payload"))

        val subject = payload["sub"]?.jsonPrimitive?.contentOrNull

        // 2. Resolve issuer DID and verify signature
        val docResult = didResolver.resolve(issuerDid)
        if (docResult.isFailure) {
            return VerificationResult(
                false, issuerDid, subject, emptyMap(),
                listOf("Failed to resolve issuer DID: ${docResult.exceptionOrNull()?.message}")
            )
        }
        val doc = docResult.getOrThrow()

        if (doc.verificationMethod.isNotEmpty()) {
            val vm = doc.verificationMethod[0]
            val algorithm = Algorithm.fromJwaName(algName)
            if (algorithm != null) {
                val publicKey = resolvePublicKey(vm.publicKeyMultibase)
                if (publicKey != null) {
                    try {
                        val signingInput = "${jwtParts[0]}.${jwtParts[1]}".toByteArray(Charsets.UTF_8)
                        val verifyFn = if (algorithm.isPostQuantum) pqcVerify else classicalVerify
                        if (!verifyFn(algorithm, publicKey, signatureBytes, signingInput)) {
                            errors.add("Signature verification failed")
                        }
                    } catch (e: Exception) {
                        errors.add("Signature verification error: ${e.message}")
                    }
                }
            }
        }

        // 3. Check expiration
        payload["exp"]?.jsonPrimitive?.longOrNull?.let { exp ->
            if (System.currentTimeMillis() / 1000 > exp) {
                errors.add("SD-JWT VC expired")
            }
        }

        // 4. Verify disclosure hashes match _sd array
        val sdArray = payload["_sd"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        val disclosedClaims = mutableMapOf<String, String>()

        for (disclosure in sdJwtVc.disclosures) {
            val hash = disclosure.hash()
            if (hash !in sdArray) {
                errors.add("Disclosure hash mismatch for claim '${disclosure.claimName}'")
            }
            disclosedClaims[disclosure.claimName] = disclosure.claimValue
        }

        // Add always-visible claims from payload
        for ((key, value) in payload) {
            if (key !in setOf("iss", "sub", "vct", "iat", "exp", "_sd", "_sd_alg", "cnf")) {
                if (value is JsonPrimitive) {
                    disclosedClaims[key] = value.content
                }
            }
        }

        // 5. Verify KB-JWT if present
        if (sdJwtVc.keyBindingJwt != null) {
            val kbParts = sdJwtVc.keyBindingJwt.split(".")
            if (kbParts.size == 3) {
                val kbPayload = decodeJsonPart(kbParts[1])

                if (expectedAudience != null) {
                    val aud = kbPayload["aud"]?.jsonPrimitive?.content
                    if (aud != expectedAudience) {
                        errors.add("KB-JWT audience mismatch: expected $expectedAudience, got $aud")
                    }
                }

                if (expectedNonce != null) {
                    val nonce = kbPayload["nonce"]?.jsonPrimitive?.content
                    if (nonce != expectedNonce) {
                        errors.add("KB-JWT nonce mismatch")
                    }
                }
            }
        }

        return VerificationResult(
            verified = errors.isEmpty(),
            issuer = issuerDid,
            subject = subject,
            disclosedClaims = disclosedClaims,
            errors = errors
        )
    }

    /**
     * Attempts to decode a multibase-encoded public key.
     * Returns null if the key string is empty or cannot be decoded.
     */
    private fun resolvePublicKey(publicKeyMultibase: String): ByteArray? {
        if (publicKeyMultibase.isEmpty()) return null
        return try {
            Multibase.decode(publicKeyMultibase)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeJsonPart(base64url: String): JsonObject {
        val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
        return Json.parseToJsonElement(json).jsonObject
    }
}
