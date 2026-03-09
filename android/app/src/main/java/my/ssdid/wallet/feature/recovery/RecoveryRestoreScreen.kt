package my.ssdid.wallet.feature.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import my.ssdid.wallet.R
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class RestoreState {
    object Idle : RestoreState()
    object Restoring : RestoreState()
    object Success : RestoreState()
    data class Error(val message: String) : RestoreState()
}

@HiltViewModel
class RecoveryRestoreViewModel @Inject constructor(
    private val recoveryManager: RecoveryManager,
    private val ssdidClient: SsdidClient,
    private val storage: VaultStorage
) : ViewModel() {

    private val _state = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = _state.asStateFlow()

    fun restore(did: String, recoveryKey: String, name: String, algorithm: Algorithm) {
        if (did.isBlank() || recoveryKey.isBlank() || name.isBlank()) {
            _state.value = RestoreState.Error("All fields are required")
            return
        }
        if (!did.startsWith("did:")) {
            _state.value = RestoreState.Error("Invalid DID format")
            return
        }
        viewModelScope.launch {
            _state.value = RestoreState.Restoring
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    recoveryManager.restoreWithRecoveryKey(did, recoveryKey, name, algorithm)
                        .onSuccess { identity ->
                            try {
                                ssdidClient.updateDidDocument(identity.keyId)
                            } catch (_: Exception) {
                                // Best effort: registry update may fail on new device
                            }
                            storage.setOnboardingCompleted()
                            _state.value = RestoreState.Success
                        }
                        .onFailure { _state.value = RestoreState.Error(it.message ?: "Restoration failed") }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = RestoreState.Error("Operation timed out. Please try again.")
            }
        }
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 30_000L
        const val MAX_DID_LENGTH = 256
        const val MAX_NAME_LENGTH = 100
        const val MAX_KEY_LENGTH = 10_000
    }
}

@Composable
fun RecoveryRestoreScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onNavigateToSocialRestore: () -> Unit = {},
    viewModel: RecoveryRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var did by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(Algorithm.KAZ_SIGN_192) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Navigate back" }
            ) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleLarge)
        }

        when (state) {
            is RestoreState.Success -> {
                // Success card
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
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SuccessDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2713", fontSize = 28.sp, color = Success, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.restore_success_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.restore_success_desc),
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(
                                    stringResource(R.string.done),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.restore_desc),
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            onClick = onNavigateToSocialRestore
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\uD83D\uDC65", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.restore_social_title), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.restore_social_desc), fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("\u203A", fontSize = 20.sp, color = TextTertiary)
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(Modifier.weight(1f), color = Border)
                            Text("  ${stringResource(R.string.restore_or_recovery_key)}  ", fontSize = 12.sp, color = TextTertiary)
                            HorizontalDivider(Modifier.weight(1f), color = Border)
                        }
                    }

                    item {
                        Text(stringResource(R.string.social_restore_did_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = did,
                            onValueChange = { did = it.take(RecoveryRestoreViewModel.MAX_DID_LENGTH) },
                            placeholder = { Text("did:ssdid:...", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text(stringResource(R.string.social_restore_name_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(RecoveryRestoreViewModel.MAX_NAME_LENGTH) },
                            placeholder = { Text("e.g. Personal, Work", color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    item {
                        Text(stringResource(R.string.restore_recovery_key_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = recoveryKey,
                            onValueChange = { recoveryKey = it.take(RecoveryRestoreViewModel.MAX_KEY_LENGTH) },
                            placeholder = { Text(stringResource(R.string.restore_recovery_key_hint), color = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            minLines = 3,
                            maxLines = 5
                        )
                    }

                    item {
                        Text(stringResource(R.string.social_restore_algorithm_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Algorithm.entries.forEach { algo ->
                                val isSelected = selectedAlgorithm == algo
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) AccentDim else BgCard
                                    ),
                                    onClick = { selectedAlgorithm = algo }
                                ) {
                                    Row(
                                        Modifier
                                            .padding(14.dp)
                                            .semantics { contentDescription = "Select ${algo.name.replace("_", " ")} algorithm" }
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedAlgorithm = algo },
                                            colors = RadioButtonDefaults.colors(selectedColor = Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                algo.name.replace("_", " "),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(algo.w3cType, fontSize = 11.sp, color = TextTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Error display
                if (state is RestoreState.Error) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerDim)
                    ) {
                        Text(
                            (state as RestoreState.Error).message,
                            modifier = Modifier.padding(18.dp),
                            fontSize = 13.sp,
                            color = Danger
                        )
                    }
                }

                // Footer button
                Button(
                    onClick = { viewModel.restore(did, recoveryKey, name, selectedAlgorithm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = did.isNotBlank() && recoveryKey.isNotBlank() && name.isNotBlank() &&
                        state !is RestoreState.Restoring,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (state is RestoreState.Restoring) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = "Loading" },
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.restore_restoring), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(
                            stringResource(R.string.restore_button),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
