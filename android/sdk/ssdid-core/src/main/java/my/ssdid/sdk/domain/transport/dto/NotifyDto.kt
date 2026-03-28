package my.ssdid.sdk.domain.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Inbox ----------

@Serializable
data class NotifyDevice(
    val platform: String,
    val token: String
)

@Serializable
data class RegisterInboxRequest(
    val devices: List<NotifyDevice>
)

@Serializable
data class RegisterInboxResponse(
    @SerialName("inbox_id") val inboxId: String,
    @SerialName("inbox_secret") val inboxSecret: String
)

@Serializable
data class UpdateDevicesRequest(
    val devices: List<NotifyDevice>
)

// ---------- Mailbox ----------

@Serializable
data class CreateMailboxRequest(
    @SerialName("inbox_id") val inboxId: String,
    @SerialName("mailbox_id") val mailboxId: String
)

// ---------- Pending notifications ----------

@Serializable
data class PendingNotification(
    @SerialName("notification_id") val notificationId: String,
    @SerialName("mailbox_id") val mailboxId: String,
    val payload: String,
    val priority: String = "normal",
    @SerialName("received_at") val receivedAt: String? = null
)

@Serializable
data class PendingNotificationsResponse(
    val notifications: List<PendingNotification>
)

// ---------- Publish (service-side, included for completeness) ----------

@Serializable
data class PublishRequest(
    @SerialName("mailbox_id") val mailboxId: String,
    val payload: String,
    val priority: String = "normal"
)

// ---------- Service registration (service-side) ----------

@Serializable
data class RegisterServiceRequest(
    val name: String,
    @SerialName("public_key") val publicKey: String
)

@Serializable
data class RegisterServiceResponse(
    @SerialName("service_id") val serviceId: String,
    @SerialName("api_key") val apiKey: String
)
