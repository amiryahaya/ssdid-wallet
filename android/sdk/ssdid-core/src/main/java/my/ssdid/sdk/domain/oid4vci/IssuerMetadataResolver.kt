package my.ssdid.sdk.domain.oid4vci

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class IssuerMetadataResolver(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, IssuerMetadata>()

    fun resolve(issuerUrl: String): Result<IssuerMetadata> = runCatching {
        cache.computeIfAbsent(issuerUrl) { key ->
            // Fetch credential issuer metadata
            val issuerMetaUrl = "${key.trimEnd('/')}/.well-known/openid-credential-issuer"
            val issuerMetaJson = fetchJson(issuerMetaUrl)

            val credentialEndpoint = issuerMetaJson["credential_endpoint"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing credential_endpoint")

            val configs = issuerMetaJson["credential_configurations_supported"]?.jsonObject
                ?.mapValues { it.value.jsonObject }
                ?: emptyMap()

            // Determine authorization server
            val explicitAuthServer = issuerMetaJson["authorization_server"]?.jsonPrimitive?.contentOrNull

            // Validate explicit authorization server URL to prevent SSRF
            if (explicitAuthServer != null) {
                validateUrl(explicitAuthServer)
            }

            val authServer = explicitAuthServer ?: key

            // Fetch OAuth authorization server metadata
            val authMetaUrl = "${authServer.trimEnd('/')}/.well-known/oauth-authorization-server"
            val authMetaJson = fetchJson(authMetaUrl)

            val tokenEndpoint = authMetaJson["token_endpoint"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing token_endpoint")

            val authorizationEndpoint = authMetaJson["authorization_endpoint"]?.jsonPrimitive?.contentOrNull

            IssuerMetadata(
                credentialIssuer = key,
                credentialEndpoint = credentialEndpoint,
                credentialConfigurationsSupported = configs,
                tokenEndpoint = tokenEndpoint,
                authorizationEndpoint = authorizationEndpoint
            )
        }
    }

    private fun validateUrl(url: String) {
        require(url.startsWith("https://")) {
            "Authorization server URL must use HTTPS: $url"
        }
        val host = java.net.URI(url).host?.lowercase()
            ?: throw IllegalArgumentException("Invalid authorization server URL: $url")
        require(host != "localhost" && host != "127.0.0.1" && host != "::1") {
            "Authorization server URL must not point to a loopback address: $url"
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun fetchJson(url: String): JsonObject {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code} fetching $url")
            val body = response.body?.string() ?: throw RuntimeException("Empty response from $url")
            Json.parseToJsonElement(body).jsonObject
        }
    }
}
