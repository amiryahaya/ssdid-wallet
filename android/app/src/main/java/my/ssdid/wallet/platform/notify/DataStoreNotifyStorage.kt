package my.ssdid.wallet.platform.notify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import my.ssdid.sdk.domain.logging.NoOpLogger
import my.ssdid.sdk.domain.logging.SsdidLogger
import my.ssdid.wallet.domain.notify.NotifyStorage
import my.ssdid.sdk.domain.vault.KeystoreManager
import java.util.Base64

private val Context.notifyStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_notify")

/**
 * Persists notify inbox credentials.
 *
 * The inbox_id is non-sensitive and stored in plain text.
 * The inbox_secret is sensitive — it is encrypted with an Android Keystore AES-256-GCM key
 * before being stored in DataStore (same pattern as vault private keys).
 */
class DataStoreNotifyStorage(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val logger: SsdidLogger = NoOpLogger()
) : NotifyStorage {

    private val inboxIdKey = stringPreferencesKey("inbox_id")
    // Stored as Base64url-encoded ciphertext (IV prepended, matching AndroidKeystoreManager format)
    private val encryptedSecretKey = stringPreferencesKey("inbox_secret_enc")

    override suspend fun getInboxId(): String? =
        context.notifyStore.data.map { it[inboxIdKey] }.first()

    override suspend fun getInboxSecret(): String? {
        val encoded = context.notifyStore.data.map { it[encryptedSecretKey] }.first()
            ?: return null
        return try {
            val ciphertext = Base64.getUrlDecoder().decode(encoded)
            keystoreManager.decrypt(KEYSTORE_ALIAS, ciphertext).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to decrypt inbox_secret", e)
            null
        }
    }

    override suspend fun saveInboxCredentials(inboxId: String, inboxSecret: String) {
        keystoreManager.generateWrappingKey(KEYSTORE_ALIAS)
        val ciphertext = keystoreManager.encrypt(KEYSTORE_ALIAS, inboxSecret.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)
        context.notifyStore.edit { prefs ->
            prefs[inboxIdKey] = inboxId
            prefs[encryptedSecretKey] = encoded
        }
    }

    override suspend fun clear() {
        // Delete the Keystore key first — if we crash after this but before clearing DataStore,
        // the ciphertext becomes unreadable and re-registration will be triggered on next launch.
        keystoreManager.deleteKey(KEYSTORE_ALIAS)
        context.notifyStore.edit { prefs ->
            prefs.remove(inboxIdKey)
            prefs.remove(encryptedSecretKey)
        }
    }

    companion object {
        private const val TAG = "NotifyStorage"
        private const val KEYSTORE_ALIAS = "notify_inbox_secret"
    }
}
