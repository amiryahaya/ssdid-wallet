package my.ssdid.wallet.domain.sdjwt

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*

data class Disclosure(
    val salt: String,
    val claimName: String,
    val claimValue: String,
    val encoded: String = ""
) {
    fun hash(algorithm: String = "sha-256"): String {
        val input = encode()
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun encode(): String {
        if (encoded.isNotEmpty()) return encoded
        val array = buildJsonArray {
            add(salt)
            add(claimName)
            add(claimValue)
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(array.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        fun decode(base64url: String): Disclosure {
            val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
            val array = Json.parseToJsonElement(json).jsonArray
            return Disclosure(
                salt = array[0].jsonPrimitive.content,
                claimName = array[1].jsonPrimitive.content,
                claimValue = array[2].jsonPrimitive.content,
                encoded = base64url
            )
        }
    }
}
