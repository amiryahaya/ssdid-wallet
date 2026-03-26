package my.ssdid.wallet.domain.revocation

import kotlinx.serialization.json.Json
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpStatusListFetcher(
    private val client: OkHttpClient,
    private val json: Json
) : StatusListFetcher {

    override suspend fun fetch(url: String): Result<StatusListCredential> = runCatching {
        require(url.isNotEmpty()) { "Status list URL must not be empty" }
        val parsed = URL(url)
        require(parsed.protocol == "https") { "Status list URL must use HTTPS: $url" }

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to fetch status list: HTTP ${response.code}" }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
            json.decodeFromString<StatusListCredential>(body)
        }
    }
}
