package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Presentation Exchange 2.0 response metadata.
 *
 * Describes how the VP Token maps to the requested Presentation Definition.
 */
@Serializable
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id") val definitionId: String,
    @SerialName("descriptor_map") val descriptorMap: List<DescriptorMapEntry>
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)
}

@Serializable
data class DescriptorMapEntry(
    val id: String,
    val format: String,
    val path: String
)
