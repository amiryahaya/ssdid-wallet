package my.ssdid.wallet.feature.recovery

import java.util.Base64 as JBase64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import my.ssdid.wallet.R
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class InstitutionalSetupState {
    object Idle : InstitutionalSetupState()
    object Enrolling : InstitutionalSetupState()
    data class Success(val orgName: String) : InstitutionalSetupState()
    data class Error(val message: String) : InstitutionalSetupState()
}

@HiltViewModel
class InstitutionalSetupViewModel @Inject constructor(
    private val institutionalRecoveryManager: InstitutionalRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<InstitutionalSetupState>(InstitutionalSetupState.Idle)
    val state = _state.asStateFlow()

    private val _hasExistingConfig = MutableStateFlow(false)
    val hasExistingConfig = _hasExistingConfig.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = vault.getIdentity(keyId)
                _identity.value = id
                if (id != null) {
                    _hasExistingConfig.value = institutionalRecoveryManager.hasOrgRecovery(id.did)
                }
            } catch (_: Exception) {
                // Identity load failed — leave as null
            }
        }
    }

    fun enroll(orgName: String, orgDid: String, encryptedKeyBase64: String) {
        val id = _identity.value ?: return
        if (orgName.isBlank() || orgDid.isBlank() || encryptedKeyBase64.isBlank()) {
            _state.value = InstitutionalSetupState.Error("All fields are required")
            return
        }
        if (!orgDid.startsWith("did:")) {
            _state.value = InstitutionalSetupState.Error("Invalid DID format")
            return
        }

        viewModelScope.launch {
            _state.value = InstitutionalSetupState.Enrolling
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    val encryptedKeyBytes = JBase64.getUrlDecoder().decode(encryptedKeyBase64.trim())
                    institutionalRecoveryManager.enrollOrganization(id, orgDid, orgName, encryptedKeyBytes)
                        .onSuccess { _state.value = InstitutionalSetupState.Success(orgName) }
                        .onFailure { _state.value = InstitutionalSetupState.Error(it.message ?: "Enrollment failed") }
                }
            } catch (_: IllegalArgumentException) {
                _state.value = InstitutionalSetupState.Error("Invalid Base64 input")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = InstitutionalSetupState.Error("Operation timed out. Please try again.")
            }
        }
    }

    fun resetState() {
        _state.value = InstitutionalSetupState.Idle
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 30_000L
        const val MAX_NAME_LENGTH = 200
        const val MAX_DID_LENGTH = 256
        const val MAX_BASE64_LENGTH = 10_000
    }
}

@Composable
fun InstitutionalSetupScreen(
    onBack: () -> Unit,
    viewModel: InstitutionalSetupViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val state by viewModel.state.collectAsState()
    val hasExistingConfig by viewModel.hasExistingConfig.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingEnroll by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.institutional_confirm_overwrite_title)) },
            text = { Text(stringResource(R.string.institutional_confirm_overwrite_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    pendingEnroll?.let { (name, did, key) -> viewModel.enroll(name, did, key) }
                    pendingEnroll = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    pendingEnroll = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Navigate back" }
            ) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.institutional_title), style = MaterialTheme.typography.titleLarge)
        }

        when (state) {
            is InstitutionalSetupState.Success -> {
                SuccessContent(
                    orgName = (state as InstitutionalSetupState.Success).orgName,
                    onDone = onBack
                )
            }
            else -> {
                FormContent(
                    identity = identity,
                    state = state,
                    onEnroll = { orgName, orgDid, encryptedKey ->
                        if (hasExistingConfig) {
                            pendingEnroll = Triple(orgName, orgDid, encryptedKey)
                            showConfirmDialog = true
                        } else {
                            viewModel.enroll(orgName, orgDid, encryptedKey)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(orgName: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(SuccessDim)
                        .semantics { contentDescription = "Success" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u2713", fontSize = 24.sp, color = Success, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.institutional_enrolled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(orgName, fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(stringResource(R.string.done), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FormContent(
    identity: Identity?,
    state: InstitutionalSetupState,
    onEnroll: (orgName: String, orgDid: String, encryptedKey: String) -> Unit
) {
    var orgName by remember { mutableStateOf("") }
    var orgDid by remember { mutableStateOf("") }
    var encryptedKey by remember { mutableStateOf("") }

    val hasRecoveryKey = identity?.hasRecoveryKey == true
    val isEnrolling = state is InstitutionalSetupState.Enrolling
    val enrollEnabled = hasRecoveryKey &&
        orgName.isNotBlank() &&
        orgDid.isNotBlank() &&
        encryptedKey.isNotBlank() &&
        !isEnrolling

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.institutional_desc),
            fontSize = 13.sp,
            color = TextSecondary
        )

        if (!hasRecoveryKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WarningDim)
            ) {
                Text(
                    stringResource(R.string.institutional_no_recovery_key),
                    modifier = Modifier.padding(18.dp),
                    fontSize = 13.sp,
                    color = Warning
                )
            }
        }

        OutlinedTextField(
            value = orgName,
            onValueChange = { orgName = it.take(InstitutionalSetupViewModel.MAX_NAME_LENGTH) },
            label = { Text(stringResource(R.string.institutional_org_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextTertiary,
                cursorColor = Accent
            )
        )

        OutlinedTextField(
            value = orgDid,
            onValueChange = { orgDid = it.take(InstitutionalSetupViewModel.MAX_DID_LENGTH) },
            label = { Text(stringResource(R.string.institutional_org_did_label)) },
            placeholder = { Text(stringResource(R.string.institutional_org_did_hint), color = TextTertiary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextTertiary,
                cursorColor = Accent
            )
        )

        OutlinedTextField(
            value = encryptedKey,
            onValueChange = { encryptedKey = it.take(InstitutionalSetupViewModel.MAX_BASE64_LENGTH) },
            label = { Text(stringResource(R.string.institutional_encrypted_key_label)) },
            placeholder = { Text(stringResource(R.string.institutional_encrypted_key_hint), color = TextTertiary) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextTertiary,
                cursorColor = Accent
            )
        )

        if (state is InstitutionalSetupState.Error) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DangerDim)
            ) {
                Text(
                    state.message,
                    modifier = Modifier.padding(18.dp),
                    fontSize = 13.sp,
                    color = Danger
                )
            }
        }

        Button(
            onClick = { onEnroll(orgName, orgDid, encryptedKey) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enrollEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = BgElevated
            )
        ) {
            if (isEnrolling) {
                CircularProgressIndicator(
                    Modifier.size(20.dp).semantics { contentDescription = "Loading" },
                    color = BgPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.institutional_enrolling), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(stringResource(R.string.institutional_enroll_button), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
