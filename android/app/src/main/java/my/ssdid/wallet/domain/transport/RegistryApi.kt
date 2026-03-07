package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.*

interface RegistryApi {
    @POST("api/did")
    suspend fun registerDid(@Body request: RegisterDidRequest): RegisterDidResponse

    @GET("api/did/{did}")
    suspend fun resolveDid(@Path("did") did: String): DidDocument

    @PUT("api/did/{did}")
    suspend fun updateDid(@Path("did") did: String, @Body request: RegisterDidRequest): RegisterDidResponse

    @DELETE("api/did/{did}")
    suspend fun deactivateDid(@Path("did") did: String): RegisterDidResponse

    @POST("api/did/{did}/challenge")
    suspend fun createChallenge(@Path("did") did: String): ChallengeResponse
}
