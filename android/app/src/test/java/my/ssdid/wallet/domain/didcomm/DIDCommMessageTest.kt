package my.ssdid.wallet.domain.didcomm

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class DIDCommMessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `construct message and verify fields`() {
        val body = buildJsonObject { put("content", JsonPrimitive("hello")) }
        val message = DIDCommMessage(
            id = "msg-001",
            type = "https://didcomm.org/basicmessage/2.0/message",
            from = "did:ssdid:alice",
            to = listOf("did:ssdid:bob"),
            createdTime = 1700000000L,
            body = body
        )

        assertThat(message.id).isEqualTo("msg-001")
        assertThat(message.type).isEqualTo("https://didcomm.org/basicmessage/2.0/message")
        assertThat(message.from).isEqualTo("did:ssdid:alice")
        assertThat(message.to).containsExactly("did:ssdid:bob")
        assertThat(message.createdTime).isEqualTo(1700000000L)
        assertThat(message.body["content"]?.jsonPrimitive?.content).isEqualTo("hello")
        assertThat(message.attachments).isEmpty()
    }

    @Test
    fun `serialize to JSON and verify structure`() {
        val body = buildJsonObject { put("key", JsonPrimitive("value")) }
        val message = DIDCommMessage(
            id = "msg-002",
            type = "https://didcomm.org/test/1.0/test",
            from = "did:ssdid:sender",
            to = listOf("did:ssdid:recipient1", "did:ssdid:recipient2"),
            createdTime = 1700000000L,
            body = body
        )

        val serialized = json.encodeToString(DIDCommMessage.serializer(), message)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertThat(parsed["id"]?.jsonPrimitive?.content).isEqualTo("msg-002")
        assertThat(parsed["type"]?.jsonPrimitive?.content).isEqualTo("https://didcomm.org/test/1.0/test")
        assertThat(parsed["from"]?.jsonPrimitive?.content).isEqualTo("did:ssdid:sender")
        assertThat(parsed["created_time"]?.jsonPrimitive?.content).isEqualTo("1700000000")
        assertThat(parsed.containsKey("body")).isTrue()
    }

    @Test
    fun `deserialize from JSON string`() {
        val jsonString = """
        {
            "id": "msg-003",
            "type": "https://didcomm.org/basicmessage/2.0/message",
            "from": "did:ssdid:alice",
            "to": ["did:ssdid:bob"],
            "created_time": 1700000000,
            "body": {"content": "Hello Bob!"}
        }
        """.trimIndent()

        val message = json.decodeFromString(DIDCommMessage.serializer(), jsonString)

        assertThat(message.id).isEqualTo("msg-003")
        assertThat(message.from).isEqualTo("did:ssdid:alice")
        assertThat(message.to).containsExactly("did:ssdid:bob")
        assertThat(message.createdTime).isEqualTo(1700000000L)
        assertThat(message.body["content"]?.jsonPrimitive?.content).isEqualTo("Hello Bob!")
    }

    @Test
    fun `round-trip serialization preserves message`() {
        val attachment = DIDCommAttachment(
            id = "att-1",
            mediaType = "application/json",
            data = DIDCommAttachmentData(
                json = buildJsonObject { put("doc", JsonPrimitive("credential")) }
            )
        )
        val body = buildJsonObject { put("note", JsonPrimitive("see attachment")) }
        val original = DIDCommMessage(
            id = "msg-004",
            type = "https://didcomm.org/issue/3.0/offer",
            from = "did:ssdid:issuer",
            to = listOf("did:ssdid:holder"),
            createdTime = 1700000000L,
            body = body,
            attachments = listOf(attachment)
        )

        val serialized = json.encodeToString(DIDCommMessage.serializer(), original)
        val deserialized = json.decodeFromString(DIDCommMessage.serializer(), serialized)

        assertThat(deserialized).isEqualTo(original)
    }

    @Test
    fun `message without optional fields serializes correctly`() {
        val body = buildJsonObject { put("status", JsonPrimitive("ok")) }
        val message = DIDCommMessage(
            id = "msg-005",
            type = "https://didcomm.org/ping/2.0/ping",
            to = listOf("did:ssdid:target"),
            body = body
        )

        assertThat(message.from).isNull()
        assertThat(message.createdTime).isNull()

        val serialized = json.encodeToString(DIDCommMessage.serializer(), message)
        val deserialized = json.decodeFromString(DIDCommMessage.serializer(), serialized)
        assertThat(deserialized).isEqualTo(message)
    }

    @Test
    fun `attachment with base64 data`() {
        val attachment = DIDCommAttachment(
            id = "att-b64",
            mediaType = "application/octet-stream",
            data = DIDCommAttachmentData(base64 = "SGVsbG8gV29ybGQ=")
        )

        val serialized = json.encodeToString(DIDCommAttachment.serializer(), attachment)
        val deserialized = json.decodeFromString(DIDCommAttachment.serializer(), serialized)

        assertThat(deserialized.id).isEqualTo("att-b64")
        assertThat(deserialized.mediaType).isEqualTo("application/octet-stream")
        assertThat(deserialized.data.base64).isEqualTo("SGVsbG8gV29ybGQ=")
        assertThat(deserialized.data.json).isNull()
    }
}
