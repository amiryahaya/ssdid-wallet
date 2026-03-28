package my.ssdid.sdk.domain.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.transport.dto.RegisterStartRequest
import my.ssdid.sdk.domain.transport.dto.RegisterStartResponse
import my.ssdid.sdk.domain.transport.dto.RegisterVerifyRequest
import my.ssdid.sdk.domain.transport.dto.RegisterVerifyResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DriveApi {
    @POST("api/auth/ssdid/register")
    suspend fun register(@Body request: RegisterStartRequest): RegisterStartResponse

    @POST("api/auth/ssdid/register/verify")
    suspend fun registerVerify(@Body request: RegisterVerifyRequest): RegisterVerifyResponse

    @POST("api/auth/ssdid/authenticate")
    suspend fun authenticate(@Body request: DriveAuthenticateRequest): DriveAuthenticateResponse
}

@Serializable
data class DriveAuthenticateRequest(
    val credential: VerifiableCredential,
    @SerialName("challenge_id") val challengeId: String? = null
)

@Serializable
data class DriveAuthenticateResponse(
    @SerialName("session_token") val sessionToken: String,
    val did: String? = null,
    @SerialName("server_did") val serverDid: String,
    @SerialName("server_signature") val serverSignature: String? = null
)
