package my.ssdid.sdk.domain.notify

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic interface for persisting local notifications.
 */
interface LocalNotificationStore {
    val allNotifications: Flow<List<LocalNotification>>
    val unreadCount: Flow<Int>
    suspend fun save(notification: LocalNotification)
    suspend fun markAsRead(id: String)
    suspend fun markAllAsRead()
    suspend fun delete(id: String)
}
