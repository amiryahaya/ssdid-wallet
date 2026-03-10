package my.ssdid.wallet.platform.scan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.transport.dto.ClaimRequest
import my.ssdid.wallet.platform.security.UrlValidator

@Serializable
data class QrPayload(
    @SerialName("server_url") val serverUrl: String = "",
    @SerialName("server_did") val serverDid: String = "",
    val action: String,
    @SerialName("session_token") val sessionToken: String = "",
    @SerialName("issuer_url") val issuerUrl: String = "",
    @SerialName("offer_id") val offerId: String = "",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("callback_url") val callbackUrl: String = "",
    @SerialName("requested_claims") val requestedClaims: List<ClaimRequest> = emptyList(),
    @SerialName("accepted_algorithms") val acceptedAlgorithms: List<String> = emptyList()
)

object QrScanner {

    private val json = Json { ignoreUnknownKeys = true }
    private val VALID_ACTIONS = setOf("register", "authenticate", "sign", "credential-offer")
    private val MAX_PAYLOAD_LENGTH = 4096

    /**
     * Parse a QR code JSON string into a [QrPayload].
     * Returns null if the string is not valid, action is unrecognized,
     * or server_url fails security validation.
     */
    fun parsePayload(raw: String): QrPayload? {
        if (raw.length > MAX_PAYLOAD_LENGTH) return null
        return try {
            val payload = json.decodeFromString<QrPayload>(raw)
            // Validate action
            if (payload.action !in VALID_ACTIONS) return null
            // Validate URLs based on action type
            if (payload.action == "credential-offer") {
                if (payload.issuerUrl.isBlank() || payload.offerId.isBlank()) return null
                if (!UrlValidator.isValidServerUrl(payload.issuerUrl)) return null
            } else {
                if (!UrlValidator.isValidServerUrl(payload.serverUrl)) return null
            }
            payload
        } catch (_: Exception) {
            null
        }
    }
}
