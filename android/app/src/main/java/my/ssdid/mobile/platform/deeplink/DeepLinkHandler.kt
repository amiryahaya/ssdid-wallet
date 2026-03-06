package my.ssdid.mobile.platform.deeplink

import android.net.Uri
import my.ssdid.mobile.platform.security.UrlValidator
import my.ssdid.mobile.ui.navigation.Screen

data class DeepLinkAction(
    val action: String,
    val serverUrl: String,
    val serverDid: String = "",
    val sessionToken: String = ""
) {
    /**
     * Returns the navigation route for this deep link action,
     * or null if the action is not recognized.
     */
    fun toNavRoute(): String? = when (action) {
        "register" -> Screen.Registration.createRoute(serverUrl, serverDid)
        "authenticate" -> Screen.AuthFlow.createRoute(serverUrl)
        "sign" -> Screen.TxSigning.createRoute(serverUrl, sessionToken)
        else -> null
    }
}

object DeepLinkHandler {

    /**
     * Parses a deep link URI with scheme `ssdid://`.
     * Returns null if the URI is not a valid SSDID deep link.
     */
    private val VALID_ACTIONS = setOf("register", "authenticate", "sign")

    fun parse(uri: Uri): DeepLinkAction? {
        if (uri.scheme != "ssdid") return null
        val action = uri.host ?: return null
        if (action !in VALID_ACTIONS) return null
        val serverUrl = uri.getQueryParameter("server_url") ?: return null
        if (!UrlValidator.isValidServerUrl(serverUrl)) return null
        return DeepLinkAction(
            action = action,
            serverUrl = serverUrl,
            serverDid = uri.getQueryParameter("server_did") ?: "",
            sessionToken = uri.getQueryParameter("session_token") ?: ""
        )
    }
}
