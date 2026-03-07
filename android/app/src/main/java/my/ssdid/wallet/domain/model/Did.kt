package my.ssdid.wallet.domain.model

import java.security.SecureRandom
import java.util.Base64

@JvmInline
value class Did(val value: String) {
    fun keyId(keyIndex: Int = 1): String = "$value#key-$keyIndex"
    fun methodSpecificId(): String = value.removePrefix("did:ssdid:")

    companion object {
        fun generate(): Did {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return Did("did:ssdid:$id")
        }

        fun fromKeyId(keyId: String): Did {
            val didPart = keyId.substringBefore("#")
            return Did(didPart)
        }
    }
}
