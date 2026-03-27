package my.ssdid.wallet.domain.sdjwt

import kotlinx.serialization.json.*
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.did.DidResolver
import my.ssdid.sdk.domain.model.Algorithm
import java.util.Base64

/**
 * Verifies SD-JWT VCs: JWT signature, disclosure hash matching,
 * expiration, and key binding.
 */
class SdJwtVerifier(
    private val didResolver: DidResolver,
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider
) {
    /**
     * Verification result containing the verified claims.
     */
    data class SdJwtVerificationResult(
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
    ): SdJwtVerificationResult {
        val errors = mutableListOf<String>()

        // 1. Decode JWT header and payload
        val jwtParts = sdJwtVc.issuerJwt.split(".")
        if (jwtParts.size != 3) {
            return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Invalid JWT structure"))
        }

        val header = decodeJsonPart(jwtParts[0])
            ?: return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Malformed JWT header"))
        val payload = decodeJsonPart(jwtParts[1])
            ?: return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Malformed JWT payload"))
        val signatureBytes = try {
            Base64.getUrlDecoder().decode(jwtParts[2])
        } catch (_: Exception) {
            return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Malformed JWT signature"))
        }

        val algName = header["alg"]?.jsonPrimitive?.content
            ?: return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Missing alg in header"))

        val issuerDid = payload["iss"]?.jsonPrimitive?.content
            ?: return SdJwtVerificationResult(false, "", null, emptyMap(), listOf("Missing iss in payload"))

        val subject = payload["sub"]?.jsonPrimitive?.contentOrNull

        // 2. Resolve issuer DID and verify signature
        val docResult = didResolver.resolve(issuerDid)
        if (docResult.isFailure) {
            return SdJwtVerificationResult(
                false, issuerDid, subject, emptyMap(),
                listOf("Failed to resolve issuer DID: ${docResult.exceptionOrNull()?.message}")
            )
        }
        val doc = docResult.getOrThrow()

        if (doc.verificationMethod.isEmpty()) {
            errors.add("No verification methods in issuer DID document")
        } else {
            val kid = header["kid"]?.jsonPrimitive?.contentOrNull
            val vm = if (kid != null) {
                doc.verificationMethod.find { it.id == kid }
            } else {
                doc.verificationMethod.firstOrNull()
            }
            if (vm == null) {
                errors.add("No matching verification method found")
            } else {
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
                            val provider = if (algorithm.isPostQuantum) pqcProvider else classicalProvider
                            if (!provider.verify(algorithm, publicKey, signatureBytes, signingInput)) {
                                errors.add("Signature verification failed")
                            }
                        } catch (e: Exception) {
                            errors.add("Signature verification error: ${e.message}")
                        }
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

        // 3b. Check not-before
        payload["nbf"]?.jsonPrimitive?.longOrNull?.let { nbf ->
            if (System.currentTimeMillis() / 1000 < nbf) {
                errors.add("SD-JWT VC not yet valid (nbf)")
            }
        }

        // 4. Validate _sd_alg
        val sdAlg = payload["_sd_alg"]?.jsonPrimitive?.contentOrNull
        if (sdJwtVc.disclosures.isNotEmpty() && (sdAlg == null || sdAlg != "sha-256")) {
            errors.add("Missing or unsupported _sd_alg: $sdAlg")
        }

        // 5. Verify disclosure hashes match _sd array
        val sdArray = payload["_sd"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        val disclosedClaims = mutableMapOf<String, JsonElement>()

        for (disclosure in sdJwtVc.disclosures) {
            val hash = disclosure.hash()
            if (hash !in sdArray) {
                errors.add("Disclosure hash mismatch for claim '${disclosure.claimName}'")
            } else {
                disclosedClaims[disclosure.claimName] = disclosure.claimValue
            }
        }

        // Add always-visible claims from payload
        for ((key, value) in payload) {
            if (key !in setOf("iss", "sub", "vct", "iat", "exp", "nbf", "_sd", "_sd_alg", "cnf")) {
                disclosedClaims[key] = value
            }
        }

        // 6. Verify KB-JWT if present
        if (sdJwtVc.keyBindingJwt != null) {
            val kbParts = sdJwtVc.keyBindingJwt.split(".")
            if (kbParts.size == 3) {
                val kbHeader = decodeJsonPart(kbParts[0])
                val kbPayload = decodeJsonPart(kbParts[1])

                if (kbHeader == null || kbPayload == null) {
                    errors.add("Malformed KB-JWT")
                } else {
                    val kbSignatureBytes = try {
                        Base64.getUrlDecoder().decode(kbParts[2])
                    } catch (_: Exception) {
                        errors.add("Malformed KB-JWT signature")
                        null
                    }

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

                    // KB-JWT iat freshness check
                    val kbIat = kbPayload["iat"]?.jsonPrimitive?.longOrNull
                    val nowSec = System.currentTimeMillis() / 1000
                    if (kbIat == null || kotlin.math.abs(nowSec - kbIat) > 300) {
                        errors.add("KB-JWT iat missing or outside 5-minute freshness window")
                    }

                    // Verify KB-JWT cryptographic signature using holder's key from cnf claim
                    val cnf = payload["cnf"]?.jsonObject
                    val holderJwk = cnf?.get("jwk")?.jsonObject
                    if (holderJwk != null) {
                        val kbAlgName = kbHeader["alg"]?.jsonPrimitive?.content
                        val kbAlgorithm = if (kbAlgName != null) Algorithm.fromJwaName(kbAlgName) else null
                        if (kbAlgorithm == null) {
                            errors.add("Unsupported KB-JWT algorithm: $kbAlgName")
                        } else if (kbSignatureBytes != null) {
                            val holderKey = resolvePublicKeyFromJwk(holderJwk)
                            if (holderKey == null) {
                                errors.add("Could not resolve holder public key from cnf claim")
                            } else {
                                try {
                                    val kbSigningInput = "${kbParts[0]}.${kbParts[1]}".toByteArray(Charsets.UTF_8)
                                    val provider = if (kbAlgorithm.isPostQuantum) pqcProvider else classicalProvider
                                    if (!provider.verify(kbAlgorithm, holderKey, kbSignatureBytes, kbSigningInput)) {
                                        errors.add("KB-JWT signature verification failed")
                                    }
                                } catch (e: Exception) {
                                    errors.add("KB-JWT signature verification error: ${e.message}")
                                }
                            }
                        }
                    } else {
                        errors.add("KB-JWT present but cnf/jwk missing in issuer JWT — holder binding cannot be verified")
                    }
                }
            }
        }

        return SdJwtVerificationResult(
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

    private fun decodeJsonPart(base64url: String): JsonObject? {
        return try {
            val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            null
        }
    }
}
