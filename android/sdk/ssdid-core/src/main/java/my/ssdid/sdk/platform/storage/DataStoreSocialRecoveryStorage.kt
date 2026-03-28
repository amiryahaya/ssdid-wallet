package my.ssdid.sdk.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryConfig
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryStorage

private val Context.socialRecoveryStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_social_recovery")

class DataStoreSocialRecoveryStorage(
    private val context: Context
) : SocialRecoveryStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configKey(did: String) = stringPreferencesKey("social_config_$did")

    override suspend fun saveSocialRecoveryConfig(config: SocialRecoveryConfig) {
        context.socialRecoveryStore.edit { prefs ->
            prefs[configKey(config.did)] = json.encodeToString(config)
        }
    }

    override suspend fun getSocialRecoveryConfig(did: String): SocialRecoveryConfig? {
        val jsonStr = context.socialRecoveryStore.data.map { it[configKey(did)] }.first()
            ?: return null
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteSocialRecoveryConfig(did: String) {
        context.socialRecoveryStore.edit { prefs ->
            prefs.remove(configKey(did))
        }
    }
}
