package my.ssdid.sdk.domain.notify

/**
 * Persists notify inbox credentials.
 *
 * Platform implementations handle encryption and secure storage.
 */
interface NotifyStorage {
    suspend fun getInboxId(): String?
    suspend fun getInboxSecret(): String?
    suspend fun saveInboxCredentials(inboxId: String, inboxSecret: String)
    suspend fun clear()
}
