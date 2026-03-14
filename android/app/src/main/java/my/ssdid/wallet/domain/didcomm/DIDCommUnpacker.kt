package my.ssdid.wallet.domain.didcomm

import my.ssdid.wallet.domain.crypto.KeyAgreementProvider
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DIDCommUnpacker(private val keyAgreement: KeyAgreementProvider) {

    fun unpack(
        packed: ByteArray,
        recipientPrivateKey: ByteArray,
        senderPublicKey: ByteArray
    ): DIDCommMessage {
        require(packed.size > 28) { "Packed data too short (${packed.size} bytes)" }

        val sharedSecret = keyAgreement.deriveSharedSecret(recipientPrivateKey, senderPublicKey)
        val aesKey = HkdfUtil.deriveKey(
            ikm = sharedSecret,
            info = "DIDComm-authcrypt".toByteArray(),
            length = 32
        )

        try {
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)

            return Json.decodeFromString(DIDCommMessage.serializer(), String(plaintext))
        } finally {
            sharedSecret.fill(0)
            aesKey.fill(0)
        }
    }
}
