package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Algorithm(
    val w3cType: String,
    val proofType: String,
    val isPostQuantum: Boolean,
    val kazSignLevel: Int? = null
) {
    // --- Classical ---
    ED25519(
        w3cType = "Ed25519VerificationKey2020",
        proofType = "Ed25519Signature2020",
        isPostQuantum = false
    ),
    ECDSA_P256(
        w3cType = "EcdsaSecp256r1VerificationKey2019",
        proofType = "EcdsaSecp256r1Signature2019",
        isPostQuantum = false
    ),
    ECDSA_P384(
        w3cType = "EcdsaSecp384VerificationKey2019",
        proofType = "EcdsaSecp384Signature2019",
        isPostQuantum = false
    ),

    // --- KAZ-Sign (hybrid PQC) ---
    KAZ_SIGN_128(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 128
    ),
    KAZ_SIGN_192(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 192
    ),
    KAZ_SIGN_256(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 256
    ),

    // --- ML-DSA (FIPS 204) ---
    ML_DSA_44(
        w3cType = "MlDsa44VerificationKey2024",
        proofType = "MlDsa44Signature2024",
        isPostQuantum = true
    ),
    ML_DSA_65(
        w3cType = "MlDsa65VerificationKey2024",
        proofType = "MlDsa65Signature2024",
        isPostQuantum = true
    ),
    ML_DSA_87(
        w3cType = "MlDsa87VerificationKey2024",
        proofType = "MlDsa87Signature2024",
        isPostQuantum = true
    ),

    // --- SLH-DSA (FIPS 205) — SHA-2 variants ---
    SLH_DSA_SHA2_128S(
        w3cType = "SlhDsaSha2128sVerificationKey2024",
        proofType = "SlhDsaSha2128sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHA2_128F(
        w3cType = "SlhDsaSha2128fVerificationKey2024",
        proofType = "SlhDsaSha2128fSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHA2_192S(
        w3cType = "SlhDsaSha2192sVerificationKey2024",
        proofType = "SlhDsaSha2192sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHA2_192F(
        w3cType = "SlhDsaSha2192fVerificationKey2024",
        proofType = "SlhDsaSha2192fSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHA2_256S(
        w3cType = "SlhDsaSha2256sVerificationKey2024",
        proofType = "SlhDsaSha2256sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHA2_256F(
        w3cType = "SlhDsaSha2256fVerificationKey2024",
        proofType = "SlhDsaSha2256fSignature2024",
        isPostQuantum = true
    ),

    // --- SLH-DSA (FIPS 205) — SHAKE variants ---
    SLH_DSA_SHAKE_128S(
        w3cType = "SlhDsaShake128sVerificationKey2024",
        proofType = "SlhDsaShake128sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHAKE_128F(
        w3cType = "SlhDsaShake128fVerificationKey2024",
        proofType = "SlhDsaShake128fSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHAKE_192S(
        w3cType = "SlhDsaShake192sVerificationKey2024",
        proofType = "SlhDsaShake192sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHAKE_192F(
        w3cType = "SlhDsaShake192fVerificationKey2024",
        proofType = "SlhDsaShake192fSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHAKE_256S(
        w3cType = "SlhDsaShake256sVerificationKey2024",
        proofType = "SlhDsaShake256sSignature2024",
        isPostQuantum = true
    ),
    SLH_DSA_SHAKE_256F(
        w3cType = "SlhDsaShake256fVerificationKey2024",
        proofType = "SlhDsaShake256fSignature2024",
        isPostQuantum = true
    );

    val isKazSign: Boolean get() = kazSignLevel != null
    val isMlDsa: Boolean get() = name.startsWith("ML_DSA")
    val isSlhDsa: Boolean get() = name.startsWith("SLH_DSA")

    companion object {
        /**
         * Reverse lookup from W3C verification method type.
         * For KAZ-Sign (shared w3cType), returns KAZ_SIGN_128 as default;
         * the actual level is detected from key size at runtime.
         */
        fun fromW3cType(type: String): Algorithm? =
            entries.firstOrNull { it.w3cType == type }
    }
}
