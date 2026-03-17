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

        fun validate(value: String): Result<Did> {
            if (!value.startsWith("did:ssdid:")) {
                return Result.failure(IllegalArgumentException("DID must start with 'did:ssdid:'"))
            }
            val id = value.removePrefix("did:ssdid:")
            if (id.isEmpty()) {
                return Result.failure(IllegalArgumentException("DID method-specific ID must not be empty"))
            }
            if (id.length < 16) {
                return Result.failure(IllegalArgumentException("DID method-specific ID too short (minimum 16 characters)"))
            }
            val base64urlPattern = Regex("^[A-Za-z0-9_-]+$")
            if (!base64urlPattern.matches(id)) {
                return Result.failure(IllegalArgumentException("DID method-specific ID contains invalid characters"))
            }
            return Result.success(Did(value))
        }
    }
}
