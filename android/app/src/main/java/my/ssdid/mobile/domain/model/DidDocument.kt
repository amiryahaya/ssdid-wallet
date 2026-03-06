package my.ssdid.mobile.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DidDocument(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/ns/did/v1"),
    val id: String,
    val controller: String,
    val verificationMethod: List<VerificationMethod>,
    val authentication: List<String>,
    val assertionMethod: List<String>,
    val capabilityInvocation: List<String> = emptyList()
) {
    companion object {
        fun build(did: Did, keyId: String, algorithm: Algorithm, publicKeyMultibase: String): DidDocument {
            return DidDocument(
                id = did.value,
                controller = did.value,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = keyId,
                        type = algorithm.w3cType,
                        controller = did.value,
                        publicKeyMultibase = publicKeyMultibase
                    )
                ),
                authentication = listOf(keyId),
                assertionMethod = listOf(keyId),
                capabilityInvocation = listOf(keyId)
            )
        }
    }
}

@Serializable
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyMultibase: String
)
