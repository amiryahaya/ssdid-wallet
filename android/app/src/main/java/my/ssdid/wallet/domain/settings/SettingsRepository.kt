package my.ssdid.wallet.domain.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun biometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun autoLockMinutes(): Flow<Int>
    suspend fun setAutoLockMinutes(minutes: Int)
    fun defaultAlgorithm(): Flow<String>
    suspend fun setDefaultAlgorithm(algorithm: String)
    fun language(): Flow<String>
    suspend fun setLanguage(language: String)
}
