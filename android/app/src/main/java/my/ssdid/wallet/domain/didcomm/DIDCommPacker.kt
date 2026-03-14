package my.ssdid.wallet.domain.didcomm

import my.ssdid.wallet.domain.crypto.KeyAgreementProvider
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DIDCommPacker(private val keyAgreement: KeyAgreementProvider) {

    companion object {
        private val SECURE_RANDOM = SecureRandom()
    }

    fun pack(
        message: DIDCommMessage,
        senderPrivateKey: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray {
        val sharedSecret = keyAgreement.deriveSharedSecret(senderPrivateKey, recipientPublicKey)
        val aesKey = HkdfUtil.deriveKey(
            ikm = sharedSecret,
            info = "DIDComm-authcrypt".toByteArray(),
            length = 32
        )

        try {
            val plaintext = Json.encodeToString(DIDCommMessage.serializer(), message).toByteArray()

            val iv = ByteArray(12)
            SECURE_RANDOM.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext)

            return iv + ciphertext
        } finally {
            sharedSecret.fill(0)
            aesKey.fill(0)
        }
    }
}
