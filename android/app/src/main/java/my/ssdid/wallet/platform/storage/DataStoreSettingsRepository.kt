package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.ssdid.wallet.domain.settings.SettingsRepository

private val Context.settingsStore by preferencesDataStore(name = "ssdid_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private val biometricKey = booleanPreferencesKey("biometric_enabled")
    private val autoLockKey = intPreferencesKey("auto_lock_minutes")
    private val algorithmKey = stringPreferencesKey("default_algorithm")
    private val languageKey = stringPreferencesKey("language")

    override fun biometricEnabled(): Flow<Boolean> =
        context.settingsStore.data.map { it[biometricKey] ?: true }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[biometricKey] = enabled }
    }

    override fun autoLockMinutes(): Flow<Int> =
        context.settingsStore.data.map { it[autoLockKey] ?: 5 }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        context.settingsStore.edit { it[autoLockKey] = minutes }
    }

    override fun defaultAlgorithm(): Flow<String> =
        context.settingsStore.data.map { it[algorithmKey] ?: "KAZ_SIGN_192" }

    override suspend fun setDefaultAlgorithm(algorithm: String) {
        context.settingsStore.edit { it[algorithmKey] = algorithm }
    }

    override fun language(): Flow<String> =
        context.settingsStore.data.map { it[languageKey] ?: "en" }

    override suspend fun setLanguage(language: String) {
        context.settingsStore.edit { it[languageKey] = language }
    }
}
