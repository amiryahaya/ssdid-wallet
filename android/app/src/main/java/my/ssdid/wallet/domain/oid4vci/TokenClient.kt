package my.ssdid.wallet.domain.oid4vci

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val cNonce: String?,
    val cNonceExpiresIn: Int?
) {
    override fun toString() = "TokenResponse(accessToken=<redacted>, tokenType=$tokenType, cNonce=$cNonce, cNonceExpiresIn=$cNonceExpiresIn)"
}

class TokenClient(private val client: OkHttpClient) {

    fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null
    ): Result<TokenResponse> = runCatching {
        val formBuilder = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            .add("pre-authorized_code", preAuthorizedCode)
        if (txCode != null) formBuilder.add("tx_code", txCode)

        postTokenRequest(tokenEndpoint, formBuilder.build())
    }

    fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): Result<TokenResponse> = runCatching {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .build()

        postTokenRequest(tokenEndpoint, form)
    }

    private fun postTokenRequest(tokenEndpoint: String, formBody: FormBody): TokenResponse {
        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(formBody)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Token request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw RuntimeException("Empty token response")
            val json = Json.parseToJsonElement(body).jsonObject

            TokenResponse(
                accessToken = json["access_token"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Missing access_token"),
                tokenType = json["token_type"]?.jsonPrimitive?.content ?: "Bearer",
                cNonce = json["c_nonce"]?.jsonPrimitive?.contentOrNull,
                cNonceExpiresIn = json["c_nonce_expires_in"]?.jsonPrimitive?.intOrNull
            )
        }
    }
}
