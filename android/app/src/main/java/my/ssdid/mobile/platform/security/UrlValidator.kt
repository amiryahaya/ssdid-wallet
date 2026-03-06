package my.ssdid.mobile.platform.security

import android.net.Uri

object UrlValidator {

    private val ALLOWED_SCHEMES = setOf("https")

    /**
     * Validates that a server URL is safe to connect to.
     * Rejects non-HTTPS schemes, localhost, private IPs, and malformed URLs.
     */
    fun isValidServerUrl(url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }

        // Must be HTTPS
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return false

        val host = uri.host?.lowercase() ?: return false

        // Reject empty host
        if (host.isBlank()) return false

        // Reject localhost and loopback
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]") return false

        // Reject private/internal IP ranges (IPv4 and IPv6)
        if (isPrivateIp(host) || isPrivateIpv6(host)) return false

        // Reject hosts without a dot (single-label names like "intranet")
        if (!host.contains('.')) return false

        return true
    }

    private fun isPrivateIp(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false

        return when {
            // 10.0.0.0/8
            octets[0] == 10 -> true
            // 172.16.0.0/12
            octets[0] == 172 && octets[1] in 16..31 -> true
            // 192.168.0.0/16
            octets[0] == 192 && octets[1] == 168 -> true
            // 169.254.0.0/16 (link-local)
            octets[0] == 169 && octets[1] == 254 -> true
            // 0.0.0.0
            octets.all { it == 0 } -> true
            else -> false
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        // Strip bracket notation [::1] -> ::1
        val raw = host.removePrefix("[").removeSuffix("]").lowercase()
        return raw == "::1" ||
            raw.startsWith("fc") || raw.startsWith("fd") || // Unique local (fc00::/7)
            raw.startsWith("fe80") // Link-local (fe80::/10)
    }
}
