package my.ssdid.wallet.domain.oid4vci

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenId4VciTransport(private val client: OkHttpClient) {

    fun fetchIssuerMetadata(issuerUrl: String): String {
        val url = "${issuerUrl.trimEnd('/')}/.well-known/openid-credential-issuer"
        return get(url)
    }

    fun fetchAuthServerMetadata(authServerUrl: String): String {
        val url = "${authServerUrl.trimEnd('/')}/.well-known/oauth-authorization-server"
        return get(url)
    }

    fun postTokenRequest(tokenEndpoint: String, formBody: FormBody): String {
        val request = Request.Builder().url(tokenEndpoint).post(formBody).build()
        return execute(request)
    }

    fun postCredentialRequest(
        credentialEndpoint: String,
        accessToken: String,
        requestBody: String
    ): String {
        val body = requestBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(credentialEndpoint)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        return execute(request)
    }

    fun postDeferredRequest(
        deferredEndpoint: String,
        accessToken: String,
        transactionId: String
    ): String {
        val json = buildJsonObject { put("transaction_id", transactionId) }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(deferredEndpoint)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        return execute(request)
    }

    fun fetchCredentialOffer(offerUri: String): String = get(offerUri)

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return execute(request)
    }

    private fun execute(request: Request): String {
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body?.string() ?: throw RuntimeException("Empty response body")
        }
    }
}
