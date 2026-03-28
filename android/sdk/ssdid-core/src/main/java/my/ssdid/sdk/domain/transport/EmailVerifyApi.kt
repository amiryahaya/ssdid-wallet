package my.ssdid.sdk.domain.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface EmailVerifyApi {

    @POST("api/email/verify/send")
    suspend fun sendCode(@Body request: SendCodeRequest): SendCodeResponse

    @POST("api/email/verify/confirm")
    suspend fun confirmCode(@Body request: ConfirmCodeRequest): ConfirmCodeResponse
}

@Serializable
data class SendCodeRequest(
    val email: String,
    val deviceId: String
)

@Serializable
data class SendCodeResponse(
    val expiresIn: Int
)

@Serializable
data class ConfirmCodeRequest(
    val email: String,
    val code: String,
    val deviceId: String
)

@Serializable
data class ConfirmCodeResponse(
    val verified: Boolean
)
