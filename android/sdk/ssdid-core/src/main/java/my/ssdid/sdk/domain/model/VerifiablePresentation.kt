package my.ssdid.sdk.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * W3C Verifiable Presentation wrapper for presenting one or more VCs.
 *
 * Used for JSON-LD VC presentations. For SD-JWT VCs, the presentation
 * is implicit (SD-JWT + selected disclosures + KB-JWT).
 */
@Serializable
data class VerifiablePresentation(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
    val type: List<String> = listOf("VerifiablePresentation"),
    val holder: String,
    val verifiableCredential: List<VerifiableCredential> = emptyList(),
    val proof: Proof? = null
)
