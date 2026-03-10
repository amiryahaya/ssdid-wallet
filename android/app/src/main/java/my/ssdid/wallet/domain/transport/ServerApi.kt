package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ServerApi {
    @POST("api/register")
    suspend fun registerStart(@Body request: RegisterStartRequest): RegisterStartResponse

    @POST("api/register/verify")
    suspend fun registerVerify(@Body request: RegisterVerifyRequest): RegisterVerifyResponse

    @POST("api/authenticate")
    suspend fun authenticate(@Body request: AuthenticateRequest): AuthenticateResponse

    @POST("api/transaction/challenge")
    suspend fun requestChallenge(@Body request: TxChallengeRequest): TxChallengeResponse

    @POST("api/transaction/submit")
    suspend fun submitTransaction(@Body request: TxSubmitRequest): TxSubmitResponse

    @GET("api/auth/challenge")
    suspend fun getAuthChallenge(): AuthChallengeResponse

    @POST("api/auth/verify")
    suspend fun verifyAuth(@Body request: AuthVerifyRequest): AuthVerifyResponse
}
