package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.transport.dto.CredentialAcceptRequest
import my.ssdid.wallet.domain.transport.dto.CredentialAcceptResponse
import my.ssdid.wallet.domain.transport.dto.CredentialOfferResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface IssuerApi {
    @GET("credential-offer/{offerId}")
    suspend fun getOffer(@Path("offerId") offerId: String): CredentialOfferResponse

    @POST("credential-offer/{offerId}/accept")
    suspend fun acceptOffer(
        @Path("offerId") offerId: String,
        @Body request: CredentialAcceptRequest
    ): CredentialAcceptResponse
}
