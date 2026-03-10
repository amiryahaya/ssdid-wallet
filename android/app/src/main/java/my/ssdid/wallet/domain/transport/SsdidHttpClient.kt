package my.ssdid.wallet.domain.transport

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SsdidHttpClient(registryUrl: String, private val okHttp: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val registry: RegistryApi = buildRetrofit(registryUrl).create(RegistryApi::class.java)

    fun serverApi(serverUrl: String): ServerApi {
        return buildRetrofit(serverUrl).create(ServerApi::class.java)
    }

    fun issuerApi(baseUrl: String): IssuerApi {
        return buildRetrofit(baseUrl).create(IssuerApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
