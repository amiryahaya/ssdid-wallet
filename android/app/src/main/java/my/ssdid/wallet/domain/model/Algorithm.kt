package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Algorithm(
    val w3cType: String,
    val proofType: String,
    val isPostQuantum: Boolean,
    val kazSignLevel: Int? = null
) {
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
    KAZ_SIGN_128(
        w3cType = "KazSign128VerificationKey2024",
        proofType = "KazSign128Signature2024",
        isPostQuantum = true,
        kazSignLevel = 128
    ),
    KAZ_SIGN_192(
        w3cType = "KazSign192VerificationKey2024",
        proofType = "KazSign192Signature2024",
        isPostQuantum = true,
        kazSignLevel = 192
    ),
    KAZ_SIGN_256(
        w3cType = "KazSign256VerificationKey2024",
        proofType = "KazSign256Signature2024",
        isPostQuantum = true,
        kazSignLevel = 256
    );
}
