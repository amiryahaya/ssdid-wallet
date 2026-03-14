package my.ssdid.wallet.domain.didcomm

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import my.ssdid.wallet.domain.crypto.BouncyCastleInstaller
import my.ssdid.wallet.domain.crypto.X25519Provider
import org.junit.Before
import org.junit.Test

class DIDCommPackerTest {

    private lateinit var x25519: X25519Provider
    private lateinit var packer: DIDCommPacker

    @Before
    fun setUp() {
        BouncyCastleInstaller.ensureInstalled()
        x25519 = X25519Provider()
        packer = DIDCommPacker(x25519)
    }

    private fun createTestMessage(): DIDCommMessage {
        val body = buildJsonObject { put("content", JsonPrimitive("Hello DIDComm!")) }
        return DIDCommMessage(
            id = "msg-pack-001",
            type = "https://didcomm.org/basicmessage/2.0/message",
            from = "did:ssdid:alice",
            to = listOf("did:ssdid:bob"),
            createdTime = 1700000000L,
            body = body
        )
    }

    @Test
    fun `pack produces non-empty output`() {
        val sender = x25519.generateKeyPair()
        val recipient = x25519.generateKeyPair()
        val message = createTestMessage()

        val packed = packer.pack(message, sender.privateKey, recipient.publicKey)

        assertThat(packed).isNotEmpty()
    }

    @Test
    fun `pack produces different output each time due to random IV`() {
        val sender = x25519.generateKeyPair()
        val recipient = x25519.generateKeyPair()
        val message = createTestMessage()

        val packed1 = packer.pack(message, sender.privateKey, recipient.publicKey)
        val packed2 = packer.pack(message, sender.privateKey, recipient.publicKey)

        assertThat(packed1).isNotEqualTo(packed2)
    }

    @Test
    fun `packed output is longer than plaintext`() {
        val sender = x25519.generateKeyPair()
        val recipient = x25519.generateKeyPair()
        val message = createTestMessage()

        val plaintext = kotlinx.serialization.json.Json.encodeToString(
            DIDCommMessage.serializer(), message
        ).toByteArray()
        val packed = packer.pack(message, sender.privateKey, recipient.publicKey)

        // packed = 12 bytes IV + ciphertext + 16 bytes GCM tag
        assertThat(packed.size).isGreaterThan(plaintext.size)
        assertThat(packed.size).isEqualTo(12 + plaintext.size + 16)
    }

    @Test
    fun `packed output starts with 12-byte IV`() {
        val sender = x25519.generateKeyPair()
        val recipient = x25519.generateKeyPair()
        val message = createTestMessage()

        val packed = packer.pack(message, sender.privateKey, recipient.publicKey)

        // Must be at least 12 bytes (IV) + some ciphertext
        assertThat(packed.size).isGreaterThan(12)
    }
}
