package my.ssdid.wallet.domain.sdjwt

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*

class Disclosure(
    val salt: String,
    val claimName: String,
    val claimValue: JsonElement,
    val encoded: String = ""
) {
    fun hash(algorithm: String = "sha-256"): String {
        require(algorithm == "sha-256") {
            "Unsupported hash algorithm: $algorithm. Only sha-256 is supported."
        }
        val input = encode()
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Disclosure) return false
        return salt == other.salt && claimName == other.claimName && claimValue == other.claimValue
    }

    override fun hashCode(): Int {
        var result = salt.hashCode()
        result = 31 * result + claimName.hashCode()
        result = 31 * result + claimValue.hashCode()
        return result
    }

    override fun toString(): String = "Disclosure(salt=$salt, claimName=$claimName, claimValue=$claimValue)"

    companion object {
        fun decode(base64url: String): Disclosure {
            val json = String(Base64.getUrlDecoder().decode(base64url), Charsets.UTF_8)
            val array = Json.parseToJsonElement(json).jsonArray
            return Disclosure(
                salt = array[0].jsonPrimitive.content,
                claimName = array[1].jsonPrimitive.content,
                claimValue = array[2],
                encoded = base64url
            )
        }
    }
}
