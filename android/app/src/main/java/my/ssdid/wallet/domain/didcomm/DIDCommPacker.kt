package my.ssdid.wallet.domain.didcomm

import my.ssdid.wallet.domain.crypto.X25519Provider
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DIDCommPacker(private val x25519: X25519Provider) {

    fun pack(
        message: DIDCommMessage,
        senderPrivateKey: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray {
        // 1. ECDH shared secret
        val sharedSecret = x25519.deriveSharedSecret(senderPrivateKey, recipientPublicKey)

        // 2. HKDF-SHA256 derive AES-256 key
        val aesKey = hkdfSha256(sharedSecret, info = "DIDComm-authcrypt".toByteArray(), length = 32)

        // 3. Serialize message to JSON
        val plaintext = Json.encodeToString(DIDCommMessage.serializer(), message).toByteArray()

        // 4. AES-256-GCM encrypt
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // 5. Return: iv (12 bytes) || ciphertext+tag
        return iv + ciphertext
    }

    internal fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
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
