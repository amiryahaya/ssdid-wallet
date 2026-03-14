package my.ssdid.wallet.platform.deeplink

import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.transport.dto.ClaimRequest
import my.ssdid.wallet.platform.security.UrlValidator
import my.ssdid.wallet.ui.navigation.Screen

data class DeepLinkAction(
    val action: String,
    val serverUrl: String,
    val serverDid: String = "",
    val sessionToken: String = "",
    val issuerUrl: String = "",
    val offerId: String = "",
    val callbackUrl: String = "",
    val sessionId: String = "",
    val token: String = "",
    val requestedClaims: List<ClaimRequest> = emptyList(),
    val acceptedAlgorithms: List<String> = emptyList()
) {
    /**
     * Returns the navigation route for this deep link action,
     * or null if the action is not recognized.
     */
    fun toNavRoute(): String? = when (action) {
        "register" -> Screen.Registration.createRoute(serverUrl, serverDid)
        "authenticate" -> {
            if (requestedClaims.isNotEmpty()) {
                val claimsJson = Json.encodeToString(requestedClaims)
                val algosJson = if (acceptedAlgorithms.isNotEmpty())
                    Json.encodeToString(acceptedAlgorithms) else ""
                Screen.Consent.createRoute(serverUrl, callbackUrl, sessionId, claimsJson, algosJson)
            } else {
                Screen.AuthFlow.createRoute(serverUrl, callbackUrl)
            }
        }
        "sign" -> Screen.TxSigning.createRoute(serverUrl, sessionToken)
        "credential-offer" -> Screen.CredentialOffer.createRoute(issuerUrl, offerId)
        "openid-credential-offer" -> Screen.CredentialOffer.createRoute("", callbackUrl)
        "invite" -> Screen.InviteAccept.createRoute(serverUrl, token, callbackUrl)
        "openid4vp" -> Screen.PresentationRequest.createRoute(callbackUrl)
        else -> null
    }
}

object DeepLinkHandler {

    /**
     * Parses a deep link URI with scheme `ssdid://`.
     * Returns null if the URI is not a valid SSDID deep link.
     */
    private val VALID_ACTIONS = setOf("register", "authenticate", "sign", "credential-offer", "invite")
    private val json = Json { ignoreUnknownKeys = true }

    private val ALLOWED_CALLBACK_SCHEMES = setOf("https")

    fun isValidCallbackUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val parsed = try { Uri.parse(url) } catch (_: Exception) { return false }
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme in ALLOWED_CALLBACK_SCHEMES) {
            return !parsed.host.isNullOrBlank()
        }
        // Allow registered custom app schemes (letters, digits, +, -, .)
        // but reject anything that looks like a well-known dangerous scheme
        return scheme.matches(Regex("^[a-z][a-z0-9+\\-.]*$"))
            && scheme !in setOf("javascript", "data", "file", "blob", "vbscript", "intent", "android-app", "content", "http")
    }

    fun parse(uri: Uri): DeepLinkAction? {
        return when (uri.scheme) {
            "ssdid" -> parseSsdid(uri)
            "openid4vp" -> parseOpenId4Vp(uri)
            "openid-credential-offer" -> parseCredentialOfferScheme(uri)
            else -> null
        }
    }

    private fun parseSsdid(uri: Uri): DeepLinkAction? {
        val action = uri.host ?: return null
        if (action !in VALID_ACTIONS) return null

        if (action == "credential-offer") {
            val issuerUrl = uri.getQueryParameter("issuer_url") ?: return null
            val offerId = uri.getQueryParameter("offer_id") ?: return null
            if (!UrlValidator.isValidServerUrl(issuerUrl)) return null
            return DeepLinkAction(
                action = action,
                serverUrl = "",
                issuerUrl = issuerUrl,
                offerId = offerId
            )
        }

        val serverUrl = uri.getQueryParameter("server_url") ?: return null
        if (!UrlValidator.isValidServerUrl(serverUrl)) return null

        val callbackUrl = if (action in setOf("authenticate", "invite")) {
            val rawCallbackUrl = uri.getQueryParameter("callback_url") ?: ""
            if (isValidCallbackUrl(rawCallbackUrl)) rawCallbackUrl else ""
        } else ""

        val sessionId = uri.getQueryParameter("session_id") ?: ""
        val token = uri.getQueryParameter("token") ?: ""

        val requestedClaims = try {
            val raw = uri.getQueryParameter("requested_claims") ?: ""
            if (raw.isNotEmpty()) json.decodeFromString<List<ClaimRequest>>(raw) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val acceptedAlgorithms = try {
            val raw = uri.getQueryParameter("accepted_algorithms") ?: ""
            if (raw.isNotEmpty()) json.decodeFromString<List<String>>(raw) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return DeepLinkAction(
            action = action,
            serverUrl = serverUrl,
            serverDid = uri.getQueryParameter("server_did") ?: "",
            sessionToken = uri.getQueryParameter("session_token") ?: "",
            callbackUrl = callbackUrl,
            sessionId = sessionId,
            token = token,
            requestedClaims = requestedClaims,
            acceptedAlgorithms = acceptedAlgorithms
        )
    }

    private fun parseOpenId4Vp(uri: Uri): DeepLinkAction? {
        // Pass full URI to be parsed by OpenId4VpHandler later
        return DeepLinkAction(
            action = "openid4vp",
            serverUrl = "",
            callbackUrl = uri.toString()  // store raw URI for handler
        )
    }

    private fun parseCredentialOfferScheme(uri: Uri): DeepLinkAction? {
        val offerJson = uri.getQueryParameter("credential_offer")
        val offerUri = uri.getQueryParameter("credential_offer_uri")
        if (offerJson == null && offerUri == null) return null
        if (offerUri != null && !offerUri.startsWith("https://")) return null
        return DeepLinkAction(
            action = "openid-credential-offer",
            serverUrl = "",
            callbackUrl = offerJson ?: offerUri ?: ""
        )
    }
}
