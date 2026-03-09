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

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
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
                val encryptedKeyBytes = JBase64.getDecoder().decode(encryptedKeyBase64.trim())
                institutionalRecoveryManager.enrollOrganization(id, orgDid, orgName, encryptedKeyBytes)
                    .onSuccess { _state.value = InstitutionalSetupState.Success(orgName) }
                    .onFailure { _state.value = InstitutionalSetupState.Error(it.message ?: "Enrollment failed") }
            } catch (e: IllegalArgumentException) {
                _state.value = InstitutionalSetupState.Error("Invalid Base64 input: ${e.message}")
            }
        }
    }

    fun resetState() {
        _state.value = InstitutionalSetupState.Idle
    }
}

@Composable
fun InstitutionalSetupScreen(
    onBack: () -> Unit,
    viewModel: InstitutionalSetupViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Institutional Recovery", style = MaterialTheme.typography.titleLarge)
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
                        viewModel.enroll(orgName, orgDid, encryptedKey)
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
                        .background(SuccessDim),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u2713", fontSize = 24.sp, color = Success, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Organization Enrolled",
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
                    Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        // Info text
        Text(
            "Enroll an organization as a recovery custodian. The organization will hold " +
                "an encrypted copy of your recovery key and can assist with identity " +
                "recovery after verifying your identity through their KYC process.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        // Warning if no recovery key
        if (!hasRecoveryKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WarningDim)
            ) {
                Text(
                    "A recovery key must be generated before enrolling an organization. " +
                        "Go to Recovery Setup to generate one first.",
                    modifier = Modifier.padding(18.dp),
                    fontSize = 13.sp,
                    color = Warning
                )
            }
        }

        // Organization Name field
        OutlinedTextField(
            value = orgName,
            onValueChange = { orgName = it },
            label = { Text("Organization Name") },
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

        // Organization DID field
        OutlinedTextField(
            value = orgDid,
            onValueChange = { orgDid = it },
            label = { Text("Organization DID") },
            placeholder = { Text("did:ssdid:...", color = TextTertiary) },
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

        // Encrypted Recovery Key field
        OutlinedTextField(
            value = encryptedKey,
            onValueChange = { encryptedKey = it },
            label = { Text("Encrypted Recovery Key") },
            placeholder = { Text("Paste encrypted recovery key", color = TextTertiary) },
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

        // Error card
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

        // Enroll button
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
                CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Enrolling...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Enroll Organization", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
