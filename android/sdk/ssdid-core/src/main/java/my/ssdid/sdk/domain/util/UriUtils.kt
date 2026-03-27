package my.ssdid.sdk.domain.util

import java.net.URI
import java.net.URLDecoder

/**
 * Extract a query parameter from a URI string without android.net.Uri dependency.
 */
fun parseQueryParam(uriString: String, paramName: String): String? {
    val uri = URI(uriString)
    val query = uri.rawQuery ?: return null
    return query.split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it[0] == paramName }
        ?.getOrNull(1)
        ?.let { URLDecoder.decode(it, "UTF-8") }
}
