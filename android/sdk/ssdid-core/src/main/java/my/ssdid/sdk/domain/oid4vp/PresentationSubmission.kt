package my.ssdid.sdk.domain.oid4vp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id") val definitionId: String,
    @SerialName("descriptor_map") val descriptorMap: List<DescriptorMapEntry>
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { encodeDefaults = true }

        fun create(definitionId: String, descriptorIds: List<String>): PresentationSubmission {
            return PresentationSubmission(
                id = UUID.randomUUID().toString(),
                definitionId = definitionId,
                descriptorMap = descriptorIds.map { descId ->
                    DescriptorMapEntry(id = descId, format = "vc+sd-jwt", path = "$")
                }
            )
        }
    }
}

@Serializable
data class DescriptorMapEntry(
    val id: String,
    val format: String,
    val path: String
)
