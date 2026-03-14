package my.ssdid.wallet.domain.oid4vci

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
        cache[issuerUrl]?.let { return@runCatching it }

        // Fetch credential issuer metadata
        val issuerMetaUrl = "${issuerUrl.trimEnd('/')}/.well-known/openid-credential-issuer"
        val issuerMetaJson = fetchJson(issuerMetaUrl)

        val credentialEndpoint = issuerMetaJson["credential_endpoint"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing credential_endpoint")

        val configs = issuerMetaJson["credential_configurations_supported"]?.jsonObject
            ?.mapValues { it.value.jsonObject }
            ?: emptyMap()

        // Determine authorization server
        val authServer = issuerMetaJson["authorization_server"]?.jsonPrimitive?.contentOrNull
            ?: issuerUrl

        // Fetch OAuth authorization server metadata
        val authMetaUrl = "${authServer.trimEnd('/')}/.well-known/oauth-authorization-server"
        val authMetaJson = fetchJson(authMetaUrl)

        val tokenEndpoint = authMetaJson["token_endpoint"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing token_endpoint")

        val authorizationEndpoint = authMetaJson["authorization_endpoint"]?.jsonPrimitive?.contentOrNull

        val metadata = IssuerMetadata(
            credentialIssuer = issuerUrl,
            credentialEndpoint = credentialEndpoint,
            credentialConfigurationsSupported = configs,
            tokenEndpoint = tokenEndpoint,
            authorizationEndpoint = authorizationEndpoint
        )

        cache[issuerUrl] = metadata
        metadata
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
