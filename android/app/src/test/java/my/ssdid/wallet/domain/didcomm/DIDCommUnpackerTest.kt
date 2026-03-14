package my.ssdid.wallet.domain.didcomm

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.ssdid.wallet.domain.crypto.BouncyCastleInstaller
import my.ssdid.wallet.domain.crypto.X25519Provider
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException

class DIDCommUnpackerTest {

    private lateinit var x25519: X25519Provider
    private lateinit var packer: DIDCommPacker
    private lateinit var unpacker: DIDCommUnpacker

    @Before
    fun setUp() {
        BouncyCastleInstaller.ensureInstalled()
        x25519 = X25519Provider()
        packer = DIDCommPacker(x25519)
        unpacker = DIDCommUnpacker(x25519)
    }

    private fun createTestMessage(): DIDCommMessage {
        val body = buildJsonObject { put("content", JsonPrimitive("Hello DIDComm!")) }
        return DIDCommMessage(
            id = "msg-roundtrip-001",
            type = "https://didcomm.org/basicmessage/2.0/message",
            from = "did:ssdid:alice",
            to = listOf("did:ssdid:bob"),
            createdTime = 1700000000L,
            body = body
        )
    }

    @Test
    fun `pack then unpack round-trip preserves message`() {
        val alice = x25519.generateKeyPair()
        val bob = x25519.generateKeyPair()
        val original = createTestMessage()

        val packed = packer.pack(original, alice.privateKey, bob.publicKey)
        val unpacked = unpacker.unpack(packed, bob.privateKey, alice.publicKey)

        assertThat(unpacked).isEqualTo(original)
    }

    @Test
    fun `alice packs for bob, bob unpacks — fields match`() {
        val alice = x25519.generateKeyPair()
        val bob = x25519.generateKeyPair()
        val body = buildJsonObject {
            put("greeting", JsonPrimitive("Hi Bob, this is Alice"))
            put("priority", JsonPrimitive("high"))
        }
        val original = DIDCommMessage(
            id = "msg-alice-bob",
            type = "https://didcomm.org/basicmessage/2.0/message",
            from = "did:ssdid:alice",
            to = listOf("did:ssdid:bob"),
            createdTime = 1700000000L,
            body = body,
            attachments = listOf(
                DIDCommAttachment(
                    id = "att-1",
                    mediaType = "text/plain",
                    data = DIDCommAttachmentData(base64 = "SGVsbG8=")
                )
            )
        )

        val packed = packer.pack(original, alice.privateKey, bob.publicKey)
        val unpacked = unpacker.unpack(packed, bob.privateKey, alice.publicKey)

        assertThat(unpacked.id).isEqualTo("msg-alice-bob")
        assertThat(unpacked.type).isEqualTo("https://didcomm.org/basicmessage/2.0/message")
        assertThat(unpacked.from).isEqualTo("did:ssdid:alice")
        assertThat(unpacked.to).containsExactly("did:ssdid:bob")
        assertThat(unpacked.createdTime).isEqualTo(1700000000L)
        assertThat(unpacked.body["greeting"]?.jsonPrimitive?.content).isEqualTo("Hi Bob, this is Alice")
        assertThat(unpacked.attachments).hasSize(1)
        assertThat(unpacked.attachments[0].data.base64).isEqualTo("SGVsbG8=")
    }

    @Test(expected = AEADBadTagException::class)
    fun `unpack with wrong key fails`() {
        val alice = x25519.generateKeyPair()
        val bob = x25519.generateKeyPair()
        val eve = x25519.generateKeyPair()
        val message = createTestMessage()

        val packed = packer.pack(message, alice.privateKey, bob.publicKey)

        // Eve tries to decrypt with her own private key — should fail
        unpacker.unpack(packed, eve.privateKey, alice.publicKey)
    }

    @Test
    fun `multiple round-trips with different key pairs all succeed`() {
        val pairs = (1..3).map { x25519.generateKeyPair() }
        val message = createTestMessage()

        for (i in pairs.indices) {
            for (j in pairs.indices) {
                if (i != j) {
                    val packed = packer.pack(message, pairs[i].privateKey, pairs[j].publicKey)
                    val unpacked = unpacker.unpack(packed, pairs[j].privateKey, pairs[i].publicKey)
                    assertThat(unpacked).isEqualTo(message)
                }
            }
        }
    }
}
