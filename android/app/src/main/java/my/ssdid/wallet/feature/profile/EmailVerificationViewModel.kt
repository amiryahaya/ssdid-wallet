package my.ssdid.wallet.feature.profile

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.transport.ConfirmCodeRequest
import my.ssdid.wallet.domain.transport.EmailVerifyApi
import my.ssdid.wallet.domain.transport.SendCodeRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val emailVerifyApi: EmailVerifyApi,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val email: String = savedStateHandle["email"] ?: ""

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()

    private val _verifying = MutableStateFlow(false)
    val verifying = _verifying.asStateFlow()

    private val _verified = MutableStateFlow(false)
    val verified = _verified.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _cooldown = MutableStateFlow(0)
    val cooldown = _cooldown.asStateFlow()

    private var cooldownJob: Job? = null
    private var resendCount = 0

    @SuppressLint("HardwareIds")
    private val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()

    init {
        if (email.isBlank()) {
            _error.value = "Invalid email address."
        } else {
            sendCode()
        }
    }

    fun updateCode(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _code.value = value
            _error.value = null
        }
    }

    fun sendCode() {
        if (_sending.value || _cooldown.value > 0) return
        _sending.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                emailVerifyApi.sendCode(SendCodeRequest(email, deviceId))
                startCooldown()
            } catch (e: retrofit2.HttpException) {
                _error.value = if (e.code() == 429) {
                    "Too many requests. Please wait before trying again."
                } else {
                    "Failed to send code. Please try again."
                }
            } catch (e: Exception) {
                _error.value = "Network error. Check your connection."
            }
            _sending.value = false
        }
    }

    fun verify() {
        val c = _code.value
        if (c.length != 6 || _verifying.value) return
        _verifying.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val response = emailVerifyApi.confirmCode(ConfirmCodeRequest(email, c, deviceId))
                if (response.verified) {
                    _verified.value = true
                } else {
                    _error.value = "Invalid code."
                }
            } catch (e: retrofit2.HttpException) {
                _error.value = when (e.code()) {
                    429 -> "Too many failed attempts. Try again in 15 minutes."
                    400 -> "Invalid code. Please check and try again."
                    else -> "Verification failed."
                }
            } catch (e: Exception) {
                _error.value = "Network error. Check your connection."
            }
            _verifying.value = false
        }
    }

    private fun startCooldown() {
        resendCount++
        val seconds = when {
            resendCount <= 1 -> 60
            resendCount == 2 -> 120
            else -> 300
        }
        _cooldown.value = seconds
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (_cooldown.value > 0) {
                delay(1000)
                _cooldown.value = _cooldown.value - 1
            }
        }
    }
}
