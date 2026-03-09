package my.ssdid.wallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    val biometricEnabled = settings.biometricEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoLockMinutes = settings.autoLockMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val defaultAlgorithm = settings.defaultAlgorithm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "KAZ_SIGN_192")

    val language = settings.language()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { settings.setAutoLockMinutes(minutes) }
    }

    fun setDefaultAlgorithm(algorithm: String) {
        viewModelScope.launch { settings.setDefaultAlgorithm(algorithm) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }
}
