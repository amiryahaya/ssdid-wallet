package my.ssdid.sdk.domain.transport

import my.ssdid.sdk.domain.transport.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

    @GET("api/invitations/token/{token}")
    suspend fun getInvitationByToken(@Path("token") token: String): InvitationDetailsResponse

    @POST("api/invitations/token/{token}/accept-with-wallet")
    suspend fun acceptWithWallet(
        @Path("token") token: String,
        @Body request: AcceptWithWalletRequest
    ): AcceptWithWalletResponse
}
