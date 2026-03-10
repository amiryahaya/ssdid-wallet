package my.ssdid.wallet.platform.deeplink

import android.net.Uri
import my.ssdid.wallet.platform.security.UrlValidator
import my.ssdid.wallet.ui.navigation.Screen

data class DeepLinkAction(
    val action: String,
    val serverUrl: String,
    val serverDid: String = "",
    val sessionToken: String = "",
    val issuerUrl: String = "",
    val offerId: String = "",
    val callbackUrl: String = ""
) {
    /**
     * Returns the navigation route for this deep link action,
     * or null if the action is not recognized.
     */
    fun toNavRoute(): String? = when (action) {
        "register" -> Screen.Registration.createRoute(serverUrl, serverDid)
        "authenticate" -> Screen.AuthFlow.createRoute(serverUrl, callbackUrl)
        "sign" -> Screen.TxSigning.createRoute(serverUrl, sessionToken)
        "credential-offer" -> Screen.CredentialOffer.createRoute(issuerUrl, offerId)
        else -> null
    }
}

object DeepLinkHandler {

    /**
     * Parses a deep link URI with scheme `ssdid://`.
     * Returns null if the URI is not a valid SSDID deep link.
     */
    private val VALID_ACTIONS = setOf("register", "authenticate", "sign", "credential-offer")

    private fun isValidCallbackUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val parsed = Uri.parse(url)
        return parsed.scheme?.lowercase() == "ssdiddrive" && parsed.host == "auth"
    }

    fun parse(uri: Uri): DeepLinkAction? {
        if (uri.scheme != "ssdid") return null
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

        val callbackUrl = if (action == "authenticate") {
            val rawCallbackUrl = uri.getQueryParameter("callback_url") ?: ""
            if (isValidCallbackUrl(rawCallbackUrl)) rawCallbackUrl else ""
        } else ""

        return DeepLinkAction(
            action = action,
            serverUrl = serverUrl,
            serverDid = uri.getQueryParameter("server_did") ?: "",
            sessionToken = uri.getQueryParameter("session_token") ?: "",
            callbackUrl = callbackUrl
        )
    }
}
