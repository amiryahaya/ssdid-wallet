package my.ssdid.wallet.feature.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.verifier.offline.UnifiedVerificationResult
import my.ssdid.wallet.domain.verifier.offline.VerificationOrchestrator
import javax.inject.Inject

@HiltViewModel
class VerificationResultViewModel @Inject constructor(
    private val orchestrator: VerificationOrchestrator
) : ViewModel() {

    private val _result = MutableStateFlow<UnifiedVerificationResult?>(null)
    val result: StateFlow<UnifiedVerificationResult?> = _result

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun verify(credential: VerifiableCredential) {
        viewModelScope.launch {
            _isLoading.value = true
            _result.value = orchestrator.verify(credential)
            _isLoading.value = false
        }
    }
}
