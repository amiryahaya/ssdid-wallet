package my.ssdid.sdk.domain.oid4vp

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenId4VpTransport(private val client: OkHttpClient) {

    fun fetchRequestObject(requestUri: String): String {
        val request = Request.Builder().url(requestUri).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body?.string() ?: throw RuntimeException("Empty response body")
        }
    }

    fun postVpResponse(responseUri: String, vpToken: String, presentationSubmission: String, state: String?) {
        val formBuilder = FormBody.Builder()
            .add("vp_token", vpToken)
            .add("presentation_submission", presentationSubmission)
        if (state != null) formBuilder.add("state", state)
        val request = Request.Builder().url(responseUri).post(formBuilder.build()).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        }
    }

    fun postError(responseUri: String, error: String, state: String?) {
        val formBuilder = FormBody.Builder().add("error", error)
        if (state != null) formBuilder.add("state", state)
        val request = Request.Builder().url(responseUri).post(formBuilder.build()).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        }
    }
}
