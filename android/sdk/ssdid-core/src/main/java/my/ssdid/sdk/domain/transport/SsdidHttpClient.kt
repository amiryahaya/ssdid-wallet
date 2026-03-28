package my.ssdid.sdk.domain.transport

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap

class SsdidHttpClient(registryUrl: String, private val okHttp: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val serverApis = ConcurrentHashMap<String, ServerApi>()
    private val issuerApis = ConcurrentHashMap<String, IssuerApi>()
    private val driveApis = ConcurrentHashMap<String, DriveApi>()
    private val emailVerifyApis = ConcurrentHashMap<String, EmailVerifyApi>()
    private val notifyApis = ConcurrentHashMap<String, NotifyApi>()

    val registry: RegistryApi = buildRetrofit(registryUrl).create(RegistryApi::class.java)

    fun serverApi(serverUrl: String): ServerApi =
        serverApis.getOrPut(serverUrl.trimEnd('/')) {
            buildRetrofit(serverUrl).create(ServerApi::class.java)
        }

    fun issuerApi(baseUrl: String): IssuerApi =
        issuerApis.getOrPut(baseUrl.trimEnd('/')) {
            buildRetrofit(baseUrl).create(IssuerApi::class.java)
        }

    fun driveApi(baseUrl: String): DriveApi =
        driveApis.getOrPut(baseUrl.trimEnd('/')) {
            buildRetrofit(baseUrl).create(DriveApi::class.java)
        }

    fun emailVerifyApi(baseUrl: String): EmailVerifyApi =
        emailVerifyApis.getOrPut(baseUrl.trimEnd('/')) {
            buildRetrofit(baseUrl).create(EmailVerifyApi::class.java)
        }

    fun notifyApi(notifyUrl: String): NotifyApi =
        notifyApis.getOrPut(notifyUrl.trimEnd('/')) {
            buildRetrofit(notifyUrl).create(NotifyApi::class.java)
        }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
