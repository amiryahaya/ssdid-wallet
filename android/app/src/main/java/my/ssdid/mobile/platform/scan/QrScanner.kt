package my.ssdid.mobile.platform.scan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QrPayload(
    @SerialName("server_url") val serverUrl: String,
    @SerialName("server_did") val serverDid: String,
    val action: String,
    @SerialName("session_token") val sessionToken: String = ""
)

object QrScanner {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse a QR code JSON string into a [QrPayload].
     * Returns null if the string is not valid QR payload JSON.
     */
    fun parsePayload(raw: String): QrPayload? {
        return try {
            json.decodeFromString<QrPayload>(raw)
        } catch (_: Exception) {
            null
        }
    }
}
