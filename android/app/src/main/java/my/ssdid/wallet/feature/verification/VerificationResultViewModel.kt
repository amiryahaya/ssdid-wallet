package my.ssdid.wallet.feature.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.verifier.offline.CheckStatus
import my.ssdid.sdk.domain.verifier.offline.CheckType
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import my.ssdid.sdk.domain.verifier.offline.UnifiedVerificationResult
import my.ssdid.sdk.domain.verifier.offline.VerificationCheck
import my.ssdid.sdk.domain.verifier.offline.VerificationOrchestrator
import my.ssdid.sdk.domain.verifier.offline.VerificationSource
import my.ssdid.sdk.domain.verifier.offline.VerificationStatus
import javax.inject.Inject

@HiltViewModel
class VerificationResultViewModel @Inject constructor(
    private val orchestrator: VerificationOrchestrator,
    private val credentialRepository: CredentialRepository
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

    fun verifyById(credentialId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val credentials = credentialRepository.getHeldCredentials()
            val credential = credentials.firstOrNull { it.id == credentialId }
            if (credential != null) {
                _result.value = orchestrator.verify(credential)
            } else {
                _result.value = UnifiedVerificationResult(
                    status = VerificationStatus.FAILED,
                    checks = listOf(
                        VerificationCheck(CheckType.SIGNATURE, CheckStatus.FAIL, "Credential not found")
                    ),
                    source = VerificationSource.ONLINE
                )
            }
            _isLoading.value = false
        }
    }
}
