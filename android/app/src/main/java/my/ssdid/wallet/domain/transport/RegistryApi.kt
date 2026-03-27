package my.ssdid.wallet.domain.transport

import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.*

interface RegistryApi {
    @GET("api/registry/info")
    suspend fun getRegistryInfo(): RegistryInfoResponse

    @POST("api/did")
    suspend fun registerDid(@Body request: RegisterDidRequest): RegisterDidResponse

    @GET("api/did/{did}")
    suspend fun resolveDid(@Path("did") did: String): DidDocument

    @PUT("api/did/{did}")
    suspend fun updateDid(@Path("did") did: String, @Body request: UpdateDidRequest): RegisterDidResponse

    @HTTP(method = "DELETE", path = "api/did/{did}", hasBody = true)
    suspend fun deactivateDid(@Path("did") did: String, @Body request: DeactivateDidRequest)

    @POST("api/did/{did}/challenge")
    suspend fun createChallenge(@Path("did") did: String): ChallengeResponse

    @POST("api/did/{did}/pair")
    suspend fun initPairing(@Path("did") did: String, @Body request: PairingInitRequest): PairingInitResponse

    @POST("api/did/{did}/pair/{pairingId}/join")
    suspend fun joinPairing(@Path("did") did: String, @Path("pairingId") pairingId: String, @Body request: PairingJoinRequest): PairingJoinResponse

    @GET("api/did/{did}/pair/{pairingId}")
    suspend fun getPairingStatus(@Path("did") did: String, @Path("pairingId") pairingId: String): PairingStatusResponse

    @POST("api/did/{did}/pair/{pairingId}/approve")
    suspend fun approvePairing(@Path("did") did: String, @Path("pairingId") pairingId: String, @Body request: PairingApproveRequest)
}
