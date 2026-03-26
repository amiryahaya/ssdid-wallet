package my.ssdid.wallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.platform.i18n.LocalizationManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    @Deprecated("Biometric is now mandatory — always on")
    val biometricEnabled = settings.biometricEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    @Deprecated("Auto-lock timeout removed — locks on every resume")
    val autoLockMinutes = settings.autoLockMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val defaultAlgorithm = settings.defaultAlgorithm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "KAZ_SIGN_192")

    val language = settings.language()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    private val _bundleTtlDays = MutableStateFlow(7)
    val bundleTtlDays: StateFlow<Int> = _bundleTtlDays

    init {
        viewModelScope.launch {
            val tag = settings.language().first()
            LocalizationManager.setLocale(tag)
        }
        viewModelScope.launch {
            settings.bundleTtlDays().collect { days ->
                _bundleTtlDays.value = days
            }
        }
    }

    @Deprecated("Biometric is now mandatory")
    fun setBiometricEnabled(enabled: Boolean) { /* no-op */ }

    @Deprecated("Auto-lock timeout removed")
    fun setAutoLockMinutes(minutes: Int) { /* no-op */ }

    fun setDefaultAlgorithm(algorithm: String) {
        viewModelScope.launch { settings.setDefaultAlgorithm(algorithm) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setBundleTtlDays(days: Int) {
        viewModelScope.launch { settings.setBundleTtlDays(days) }
    }
}
