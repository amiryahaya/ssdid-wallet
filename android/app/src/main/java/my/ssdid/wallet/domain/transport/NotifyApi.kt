package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.*

interface NotifyApi {

    // Register a new inbox (first launch)
    @POST("api/v1/inbox/register")
    suspend fun registerInbox(@Body request: RegisterInboxRequest): RegisterInboxResponse

    // Update push device tokens for an existing inbox
    @PUT("api/v1/inbox/devices")
    suspend fun updateDevices(
        @Header("Authorization") bearerSecret: String,
        @Body request: UpdateDevicesRequest
    )

    // Create a mailbox under an inbox (one per identity)
    @POST("api/v1/mailbox/create")
    suspend fun createMailbox(
        @Header("Authorization") bearerSecret: String,
        @Body request: CreateMailboxRequest
    )

    // Delete a mailbox
    @DELETE("api/v1/mailbox/{mailboxId}")
    suspend fun deleteMailbox(
        @Header("Authorization") bearerSecret: String,
        @Path("mailboxId") mailboxId: String
    )

    // Fetch pending notifications (used on reconnect)
    @GET("api/v1/pending")
    suspend fun fetchPending(
        @Header("Authorization") bearerSecret: String
    ): PendingNotificationsResponse

    // Acknowledge (delete) a delivered notification
    @DELETE("api/v1/pending/{notificationId}")
    suspend fun ackPending(
        @Header("Authorization") bearerSecret: String,
        @Path("notificationId") notificationId: String
    )
}
