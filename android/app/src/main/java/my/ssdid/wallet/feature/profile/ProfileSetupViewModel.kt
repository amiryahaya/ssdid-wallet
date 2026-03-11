package my.ssdid.wallet.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.auth.ClaimValidator
import my.ssdid.wallet.domain.profile.ProfileManager
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()

    private val _nameError = MutableStateFlow<String?>(null)
    val nameError = _nameError.asStateFlow()

    private val _emailError = MutableStateFlow<String?>(null)
    val emailError = _emailError.asStateFlow()

    private val _phoneError = MutableStateFlow<String?>(null)
    val phoneError = _phoneError.asStateFlow()

    private val _isValid = MutableStateFlow(false)
    val isValid = _isValid.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val existing = profileManager.getProfile()
                if (existing != null) {
                    val claims = existing.credentialSubject.claims
                    _name.value = claims["name"] ?: ""
                    _email.value = claims["email"] ?: ""
                    _phone.value = claims["phone"] ?: ""
                }
            } catch (e: Exception) {
                _error.value = "Failed to load profile"
            }
            _loading.value = false
        }
    }

    fun updateName(value: String) {
        _name.value = value
        _nameError.value = ClaimValidator.validate("name", value)
        revalidate()
    }

    fun updateEmail(value: String) {
        _email.value = value
        _emailError.value = ClaimValidator.validate("email", value)
        revalidate()
    }

    fun updatePhone(value: String) {
        _phone.value = value
        _phoneError.value = if (value.isBlank()) null else ClaimValidator.validate("phone", value)
        revalidate()
    }

    private fun revalidate() {
        val nameOk = _name.value.isNotBlank() && _nameError.value == null
        val emailOk = _email.value.isNotBlank() && _emailError.value == null
        val phoneOk = _phone.value.isBlank() || _phoneError.value == null
        _isValid.value = nameOk && emailOk && phoneOk
    }

    fun save() {
        if (_saving.value || _saved.value) return
        _saving.value = true
        viewModelScope.launch {
            _error.value = null
            val result = profileManager.saveProfile(_name.value, _email.value, _phone.value)
            if (result.isSuccess) {
                _saved.value = true
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save profile"
            }
            _saving.value = false
        }
    }
}
