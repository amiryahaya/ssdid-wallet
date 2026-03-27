package my.ssdid.sdk.domain.crypto

import java.util.Base64

object Multibase {
    private const val BASE64URL_PREFIX = 'u'

    fun encode(data: ByteArray): String {
        return "$BASE64URL_PREFIX${Base64.getUrlEncoder().withoutPadding().encodeToString(data)}"
    }

    fun decode(encoded: String): ByteArray {
        require(encoded.isNotEmpty() && encoded[0] == BASE64URL_PREFIX) {
            "Only base64url multibase (u prefix) is supported"
        }
        return Base64.getUrlDecoder().decode(encoded.substring(1))
    }
}
