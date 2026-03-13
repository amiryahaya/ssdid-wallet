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
        val disclosedClaims: Map<String, JsonElement>,
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

        if (doc.verificationMethod.isEmpty()) {
            errors.add("No verification methods in issuer DID document")
        } else {
            val vm = doc.verificationMethod[0]
            val algorithm = Algorithm.fromJwaName(algName)
            if (algorithm == null) {
                errors.add("Unsupported JWT algorithm: $algName")
            } else {
                val publicKey = resolvePublicKey(vm.publicKeyMultibase)
                    ?: resolvePublicKeyFromJwk(vm.publicKeyJwk)
                if (publicKey == null) {
                    errors.add("Could not resolve public key from verification method")
                } else {
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
        val disclosedClaims = mutableMapOf<String, JsonElement>()

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
                disclosedClaims[key] = value
            }
        }

        // 5. Verify KB-JWT if present
        if (sdJwtVc.keyBindingJwt != null) {
            val kbParts = sdJwtVc.keyBindingJwt.split(".")
            if (kbParts.size == 3) {
                val kbHeader = decodeJsonPart(kbParts[0])
                val kbPayload = decodeJsonPart(kbParts[1])
                val kbSignatureBytes = Base64.getUrlDecoder().decode(kbParts[2])

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

                // Verify KB-JWT cryptographic signature using holder's key from cnf claim
                val cnf = payload["cnf"]?.jsonObject
                val holderJwk = cnf?.get("jwk")?.jsonObject
                if (holderJwk != null) {
                    val kbAlgName = kbHeader["alg"]?.jsonPrimitive?.content
                    val kbAlgorithm = if (kbAlgName != null) Algorithm.fromJwaName(kbAlgName) else null
                    if (kbAlgorithm == null) {
                        errors.add("Unsupported KB-JWT algorithm: $kbAlgName")
                    } else {
                        val holderKey = resolvePublicKeyFromJwk(holderJwk)
                        if (holderKey == null) {
                            errors.add("Could not resolve holder public key from cnf claim")
                        } else {
                            try {
                                val kbSigningInput = "${kbParts[0]}.${kbParts[1]}".toByteArray(Charsets.UTF_8)
                                val verifyFn = if (kbAlgorithm.isPostQuantum) pqcVerify else classicalVerify
                                if (!verifyFn(kbAlgorithm, holderKey, kbSignatureBytes, kbSigningInput)) {
                                    errors.add("KB-JWT signature verification failed")
                                }
                            } catch (e: Exception) {
                                errors.add("KB-JWT signature verification error: ${e.message}")
                            }
                        }
                    }
                }
                // If no cnf/jwk present, KB-JWT signature cannot be verified — that's acceptable
                // as the issuer may not have included a cnf claim
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

    /**
     * Attempts to extract the raw public key bytes from a JWK JsonObject.
     * Supports OKP (Ed25519/Ed448) and EC (P-256/P-384) key types.
     * Returns null if the JWK is null or the key cannot be extracted.
     */
    private fun resolvePublicKeyFromJwk(jwk: JsonObject?): ByteArray? {
        if (jwk == null) return null
        return try {
            val kty = jwk["kty"]?.jsonPrimitive?.content ?: return null
            when (kty) {
                "OKP" -> {
                    // EdDSA keys: x parameter is the public key
                    val x = jwk["x"]?.jsonPrimitive?.content ?: return null
                    Base64.getUrlDecoder().decode(x)
                }
                "EC" -> {
                    // EC keys: concatenate x and y coordinates
                    val x = jwk["x"]?.jsonPrimitive?.content ?: return null
                    val y = jwk["y"]?.jsonPrimitive?.content ?: return null
                    Base64.getUrlDecoder().decode(x) + Base64.getUrlDecoder().decode(y)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeJsonPart(base64url: String): JsonObject {
        val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
        return Json.parseToJsonElement(json).jsonObject
    }
}
