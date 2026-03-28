package my.ssdid.sdk.platform.notify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.notify.LocalNotification
import my.ssdid.sdk.domain.notify.LocalNotificationStore

internal val Context.localNotificationsStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_local_notifications")

class LocalNotificationStorage(
    private val context: Context
) : LocalNotificationStore {
    private val key = stringPreferencesKey("notifications_json")
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Maximum stored notifications. Oldest are evicted on save. */
        const val MAX_NOTIFICATIONS = 500
    }

    val allNotifications: Flow<List<LocalNotification>> =
        context.localNotificationsStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<LocalNotification>>(raw) }.getOrElse { emptyList() }
        }

    val unreadCount: Flow<Int> =
        allNotifications.map { list -> list.count { !it.isRead } }

    override suspend fun save(notification: LocalNotification) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<LocalNotification>>(it) }.getOrElse { emptyList() } } ?: emptyList()
            // Preserve isRead if notification already exists locally (avoids resurrecting read notifications on re-fetch)
            val existing = current.firstOrNull { it.id == notification.id }
            val merged = if (existing != null) notification.copy(isRead = existing.isRead) else notification
            val updated = (current.filter { it.id != notification.id } + merged).takeLast(MAX_NOTIFICATIONS)
            prefs[key] = json.encodeToString(updated)
        }
    }

    override suspend fun markAsRead(id: String) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<LocalNotification>>(it) }.getOrElse { emptyList() } } ?: return@edit
            val updated = current.map { if (it.id == id) it.copy(isRead = true) else it }
            prefs[key] = json.encodeToString(updated)
        }
    }

    override suspend fun markAllAsRead() {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<LocalNotification>>(it) }.getOrElse { emptyList() } } ?: return@edit
            val updated = current.map { it.copy(isRead = true) }
            prefs[key] = json.encodeToString(updated)
        }
    }

    override suspend fun delete(id: String) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<LocalNotification>>(it) }.getOrElse { emptyList() } } ?: return@edit
            val updated = current.filter { it.id != id }
            prefs[key] = json.encodeToString(updated)
        }
    }
}
