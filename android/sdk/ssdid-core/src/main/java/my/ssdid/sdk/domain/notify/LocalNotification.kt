package my.ssdid.sdk.domain.notify

import kotlinx.serialization.Serializable

@Serializable
data class LocalNotification(
    val id: String,
    val mailboxId: String,
    val identityName: String?,
    val payload: String,
    val priority: String,
    val receivedAt: String,
    val isRead: Boolean = false
)
