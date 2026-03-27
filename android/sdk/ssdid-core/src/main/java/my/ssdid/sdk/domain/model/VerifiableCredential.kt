package my.ssdid.sdk.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class VerifiableCredential(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
    val id: String,
    val type: List<String>,
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialSubject: CredentialSubject,
    val credentialStatus: CredentialStatus? = null,
    val proof: Proof
)

/**
 * Credential subject with a custom serializer that preserves unknown fields.
 * This ensures round-trip fidelity when the wallet receives a VC from a server
 * (e.g. Drive's "service" and "registeredAt" fields) and sends it back.
 */
@Serializable(with = CredentialSubjectSerializer::class)
data class CredentialSubject(
    val id: String,
    val claims: Map<String, String> = emptyMap(),
    val additionalProperties: Map<String, JsonElement> = emptyMap()
)

internal object CredentialSubjectSerializer : KSerializer<CredentialSubject> {
    override val descriptor = buildClassSerialDescriptor("CredentialSubject")

    override fun deserialize(decoder: Decoder): CredentialSubject {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        val claims = obj["claims"]?.jsonObject?.let { claimsObj ->
            claimsObj.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: v.toString() }
        } ?: emptyMap()
        val extra = obj.filterKeys { it != "id" && it != "claims" }
        return CredentialSubject(id = id, claims = claims, additionalProperties = extra)
    }

    override fun serialize(encoder: Encoder, value: CredentialSubject) {
        val jsonEncoder = encoder as JsonEncoder
        val obj = buildJsonObject {
            put("id", JsonPrimitive(value.id))
            if (value.claims.isNotEmpty()) {
                put("claims", buildJsonObject {
                    value.claims.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            value.additionalProperties.forEach { (k, v) -> put(k, v) }
        }
        jsonEncoder.encodeJsonElement(obj)
    }
}
