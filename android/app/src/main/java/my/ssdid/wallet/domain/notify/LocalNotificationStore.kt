package my.ssdid.wallet.domain.notify

/**
 * Platform-agnostic interface for persisting local notifications.
 */
interface LocalNotificationStore {
    suspend fun save(notification: LocalNotification)
    suspend fun markAsRead(id: String)
    suspend fun markAllAsRead()
    suspend fun delete(id: String)
}
