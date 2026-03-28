package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.notify.NotifyManager

class NotificationsApi internal constructor(private val manager: NotifyManager) {
    suspend fun ensureInboxRegistered(): String? = manager.ensureInboxRegistered()

    suspend fun createMailbox(identity: Identity): Result<Unit> = runCatching {
        manager.createMailbox(identity)
    }

    suspend fun deleteMailbox(identity: Identity): Result<Unit> = runCatching {
        manager.deleteMailbox(identity)
    }

    suspend fun fetchAndDemux(): Result<Unit> = runCatching {
        manager.fetchAndDemux()
    }

    suspend fun ackPending(notificationId: String): Result<Unit> = runCatching {
        manager.ackPending(notificationId)
    }

    suspend fun updateDeviceToken(platform: String, token: String): Result<Unit> = runCatching {
        manager.updateDeviceToken(platform, token)
    }
}
