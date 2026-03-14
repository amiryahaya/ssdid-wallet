package my.ssdid.wallet.domain.didcomm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DIDCommMessage(
    val id: String,
    val type: String,
    val from: String? = null,
    val to: List<String>,
    @SerialName("created_time")
    val createdTime: Long? = null,
    val body: JsonObject,
    val attachments: List<DIDCommAttachment> = emptyList()
)

@Serializable
data class DIDCommAttachment(
    val id: String,
    @SerialName("media_type")
    val mediaType: String? = null,
    val data: DIDCommAttachmentData
)

@Serializable
data class DIDCommAttachmentData(
    val base64: String? = null,
    val json: JsonObject? = null
)
