package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.notify.NotifyManager

class NotificationsApi internal constructor(private val manager: NotifyManager) {
    suspend fun ensureInboxRegistered(): String? = manager.ensureInboxRegistered()
    suspend fun createMailbox(identity: Identity) = manager.createMailbox(identity)
    suspend fun deleteMailbox(identity: Identity) = manager.deleteMailbox(identity)
    suspend fun fetchAndDemux() = manager.fetchAndDemux()
    suspend fun ackPending(notificationId: String) = manager.ackPending(notificationId)
    suspend fun updateDeviceToken(platform: String, token: String) =
        manager.updateDeviceToken(platform, token)
}
