package my.ssdid.wallet.domain.didcomm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DIDCommTransport(private val httpClient: OkHttpClient) {

    fun send(packed: ByteArray, serviceEndpoint: String): Result<Unit> = runCatching {
        val mediaType = "application/didcomm-encrypted+json".toMediaType()
        val request = Request.Builder()
            .url(serviceEndpoint)
            .post(packed.toRequestBody(mediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw DIDCommTransportException("HTTP ${response.code}: ${response.message}")
        }
    }
}

class DIDCommTransportException(message: String) : Exception(message)
