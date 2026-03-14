package my.ssdid.wallet.domain.oid4vp

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenId4VpTransport(private val client: OkHttpClient) {

    fun fetchRequestObject(requestUri: String): Result<AuthorizationRequest> = runCatching {
        val request = Request.Builder().url(requestUri).get().build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch request object: HTTP ${response.code}")
            }
            response.body?.string()
                ?: throw RuntimeException("Empty response from request_uri")
        }
        AuthorizationRequest.parseJson(body).getOrThrow()
    }

    fun postVpResponse(
        responseUri: String,
        vpToken: String,
        presentationSubmission: PresentationSubmission?,
        state: String?
    ): Result<Unit> = runCatching {
        val formBuilder = FormBody.Builder()
            .add("vp_token", vpToken)
        presentationSubmission?.let {
            formBuilder.add("presentation_submission", it.toJson())
        }
        state?.let { formBuilder.add("state", it) }

        val request = Request.Builder()
            .url(responseUri)
            .post(formBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to post VP response: HTTP ${response.code}")
            }
        }
    }

    fun postError(
        responseUri: String,
        error: String,
        state: String?
    ): Result<Unit> = runCatching {
        val formBuilder = FormBody.Builder()
            .add("error", error)
        state?.let { formBuilder.add("state", it) }

        val request = Request.Builder()
            .url(responseUri)
            .post(formBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to post error: HTTP ${response.code}")
            }
        }
    }
}
