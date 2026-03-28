package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.sdk.domain.recovery.institutional.OrgRecoveryConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.institutionalRecoveryStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_institutional_recovery")

@Singleton
class DataStoreInstitutionalRecoveryStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : InstitutionalRecoveryStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configKey(userDid: String) = stringPreferencesKey("org_config_$userDid")

    override suspend fun saveOrgRecoveryConfig(config: OrgRecoveryConfig) {
        context.institutionalRecoveryStore.edit { prefs ->
            prefs[configKey(config.userDid)] = json.encodeToString(config)
        }
    }

    override suspend fun getOrgRecoveryConfig(userDid: String): OrgRecoveryConfig? {
        val jsonStr = context.institutionalRecoveryStore.data.map { it[configKey(userDid)] }.first()
            ?: return null
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteOrgRecoveryConfig(userDid: String) {
        context.institutionalRecoveryStore.edit { prefs ->
            prefs.remove(configKey(userDid))
        }
    }
}
