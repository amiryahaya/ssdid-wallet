package my.ssdid.wallet.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.device.DeviceManager
import my.ssdid.wallet.domain.device.PairingData
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class EnrollState {
    object Idle : EnrollState()
    data class WaitingForSecondary(val pairingData: PairingData) : EnrollState()
    data class PairingJoined(val pairingId: String, val deviceName: String?) : EnrollState()
    object Approved : EnrollState()
    data class JoinSuccess(val status: String) : EnrollState()
    data class Error(val message: String) : EnrollState()
}

@HiltViewModel
class DeviceEnrollViewModel @Inject constructor(
    private val vault: Vault,
    private val deviceManager: DeviceManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""
    val mode: String = savedStateHandle["mode"] ?: "primary"

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<EnrollState>(EnrollState.Idle)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun initiatePairing() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            deviceManager.initiatePairing(id)
                .onSuccess { data ->
                    _state.value = EnrollState.WaitingForSecondary(data)
                    pollPairingStatus(id, data.pairingId)
                }
                .onFailure { _state.value = EnrollState.Error(it.message ?: "Failed to initiate pairing") }
        }
    }

    private fun pollPairingStatus(identity: Identity, pairingId: String) {
        viewModelScope.launch {
            repeat(60) {
                delay(3000)
                deviceManager.checkPairingStatus(identity.did, pairingId)
                    .onSuccess { resp ->
                        if (resp.status == "joined") {
                            _state.value = EnrollState.PairingJoined(pairingId, resp.device_name)
                            return@launch
                        }
                    }
            }
            val current = _state.value
            if (current is EnrollState.WaitingForSecondary) {
                _state.value = EnrollState.Error("Pairing timed out")
            }
        }
    }

    fun approvePairing() {
        val id = _identity.value ?: return
        val current = _state.value
        val pairingId = when (current) {
            is EnrollState.PairingJoined -> current.pairingId
            else -> return
        }
        viewModelScope.launch {
            deviceManager.approvePairing(id, pairingId)
                .onSuccess { _state.value = EnrollState.Approved }
                .onFailure { _state.value = EnrollState.Error(it.message ?: "Failed to approve") }
        }
    }

    fun joinPairing(pairingId: String, challenge: String, deviceName: String) {
        val id = _identity.value ?: return
        viewModelScope.launch {
            deviceManager.joinPairing(
                did = id.did,
                pairingId = pairingId,
                challenge = challenge,
                identity = id,
                deviceName = deviceName
            )
                .onSuccess { status -> _state.value = EnrollState.JoinSuccess(status) }
                .onFailure { _state.value = EnrollState.Error(it.message ?: "Failed to join") }
        }
    }
}

@Composable
fun DeviceEnrollScreen(
    onBack: () -> Unit,
    viewModel: DeviceEnrollViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val state by viewModel.state.collectAsState()
    val isPrimary = viewModel.mode == "primary"

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
            Text(
                if (isPrimary) "Enroll New Device" else "Join as Device",
                style = MaterialTheme.typography.titleLarge
            )
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isPrimary) {
                item { PrimaryModeContent(state, viewModel) }
            } else {
                item { SecondaryModeContent(state, viewModel) }
            }

            // Status messages
            if (state is EnrollState.Error) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerDim)
                    ) {
                        Text(
                            (state as EnrollState.Error).message,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Danger
                        )
                    }
                }
            }

            if (state is EnrollState.Approved || state is EnrollState.JoinSuccess) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessDim)
                    ) {
                        Text(
                            if (state is EnrollState.Approved) "Device approved and enrolled successfully"
                            else "Successfully joined pairing. Waiting for approval from primary device.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Success
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun PrimaryModeContent(state: EnrollState, viewModel: DeviceEnrollViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Instructions
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AccentDim)
        ) {
            Text(
                "Start pairing to generate a code for the secondary device. The other device will need the pairing ID and challenge to join.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                color = Accent,
                lineHeight = 18.sp
            )
        }

        when (state) {
            is EnrollState.Idle -> {
                Button(
                    onClick = { viewModel.initiatePairing() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Start Pairing", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            is EnrollState.WaitingForSecondary -> {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PAIRING ID", fontSize = 11.sp, color = TextTertiary)
                        Text(
                            state.pairingData.pairingId,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("CHALLENGE", fontSize = 11.sp, color = TextTertiary)
                        Text(
                            state.pairingData.challenge,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Waiting for secondary device...", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            }
            is EnrollState.PairingJoined -> {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Device wants to join:", fontSize = 13.sp, color = TextSecondary)
                        Text(
                            state.deviceName ?: "Unknown Device",
                            fontSize = 16.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.approvePairing() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text("Approve Device", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SecondaryModeContent(state: EnrollState, viewModel: DeviceEnrollViewModel) {
    var pairingId by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(android.os.Build.MODEL) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AccentDim)
        ) {
            Text(
                "Enter the pairing ID and challenge from the primary device to join.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                color = Accent,
                lineHeight = 18.sp
            )
        }

        if (state is EnrollState.Idle || state is EnrollState.Error) {
            OutlinedTextField(
                value = pairingId,
                onValueChange = { pairingId = it },
                label = { Text("Pairing ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextTertiary,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = challenge,
                onValueChange = { challenge = it },
                label = { Text("Challenge") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextTertiary,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextTertiary,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Button(
                onClick = { viewModel.joinPairing(pairingId, challenge, deviceName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = pairingId.isNotBlank() && challenge.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Join Pairing", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
