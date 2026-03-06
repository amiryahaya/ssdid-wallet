package my.ssdid.mobile.domain.transport

import my.ssdid.mobile.domain.transport.dto.*
import retrofit2.http.Body
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
}
