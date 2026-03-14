package my.ssdid.wallet.domain.didcomm

import my.ssdid.wallet.domain.crypto.X25519Provider
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DIDCommUnpacker(private val x25519: X25519Provider) {

    fun unpack(
        packed: ByteArray,
        recipientPrivateKey: ByteArray,
        senderPublicKey: ByteArray
    ): DIDCommMessage {
        // 1. ECDH shared secret
        val sharedSecret = x25519.deriveSharedSecret(recipientPrivateKey, senderPublicKey)

        // 2. HKDF-SHA256 derive AES-256 key
        val aesKey = hkdfSha256(sharedSecret, info = "DIDComm-authcrypt".toByteArray(), length = 32)

        // 3. Extract IV (first 12 bytes) and ciphertext (rest)
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)

        // 4. AES-256-GCM decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)

        // 5. Parse JSON to DIDCommMessage
        return Json.decodeFromString(DIDCommMessage.serializer(), String(plaintext))
    }

    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract phase
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand phase
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(byteArrayOf(1))
        return mac.doFinal().copyOf(length)
    }
}
