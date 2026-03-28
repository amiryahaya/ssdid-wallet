package my.ssdid.wallet.platform.scan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import my.ssdid.sdk.domain.transport.dto.ClaimRequest
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
    @SerialName("accepted_algorithms") val acceptedAlgorithms: List<String> = emptyList(),
    // SSDID Drive login fields
    @SerialName("service_url") val serviceUrl: String = "",
    @SerialName("service_name") val serviceName: String = "",
    @SerialName("challenge_id") val challengeId: String = "",
    val challenge: String = "",
    @SerialName("server_key_id") val serverKeyId: String = "",
    @SerialName("server_signature") val serverSignature: String = "",
    @SerialName("registry_url") val registryUrl: String = "",
    // M1: Move resolvedClaims into constructor so equals/hashCode/copy work correctly
    @Transient val resolvedClaims: List<ClaimRequest> = emptyList()
)

object QrScanner {

    private val json = Json { ignoreUnknownKeys = true }
    private val VALID_ACTIONS = setOf("register", "authenticate", "sign", "credential-offer", "login")
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
            when (payload.action) {
                "credential-offer" -> {
                    if (payload.issuerUrl.isBlank() || payload.offerId.isBlank()) return null
                    if (!UrlValidator.isValidServerUrl(payload.issuerUrl)) return null
                }
                "login" -> {
                    val url = payload.serviceUrl.ifBlank { payload.serverUrl }
                    if (!UrlValidator.isValidServerUrl(url)) return null
                }
                else -> {
                    if (!UrlValidator.isValidServerUrl(payload.serverUrl)) return null
                }
            }
            // Parse requested_claims (supports both list and object formats)
            val claims = parseRequestedClaims(raw)
            // M1: Use copy() instead of mutating — preserves data class invariants
            payload.copy(resolvedClaims = claims)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse requested_claims from raw JSON.
     * Supports two formats:
     * - List: [{"key": "name", "required": true}]
     * - Object: {"required": ["name"], "optional": ["email"]}
     */
    private fun parseRequestedClaims(raw: String): List<ClaimRequest> {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val claimsElement = root["requested_claims"] ?: return emptyList()
            when (claimsElement) {
                is JsonArray -> {
                    json.decodeFromString<List<ClaimRequest>>(claimsElement.toString())
                }
                is JsonObject -> {
                    val obj = claimsElement.jsonObject
                    val claims = mutableListOf<ClaimRequest>()
                    // H3: Safe primitive extraction — skip non-string elements
                    obj["required"]?.jsonArray?.forEach { element ->
                        if (element is JsonPrimitive && element.isString) {
                            claims.add(ClaimRequest(element.content, required = true))
                        }
                    }
                    obj["optional"]?.jsonArray?.forEach { element ->
                        if (element is JsonPrimitive && element.isString) {
                            claims.add(ClaimRequest(element.content, required = false))
                        }
                    }
                    claims
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
