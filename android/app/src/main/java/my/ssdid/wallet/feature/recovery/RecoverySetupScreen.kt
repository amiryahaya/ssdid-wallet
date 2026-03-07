package my.ssdid.wallet.feature.recovery

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class RecoverySetupState {
    object Idle : RecoverySetupState()
    object Generating : RecoverySetupState()
    data class Success(val recoveryKeyBytes: ByteArray) : RecoverySetupState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return recoveryKeyBytes.contentEquals(other.recoveryKeyBytes)
        }
        override fun hashCode(): Int = recoveryKeyBytes.contentHashCode()
    }
    data class Error(val message: String) : RecoverySetupState()
}

@HiltViewModel
class RecoverySetupViewModel @Inject constructor(
    private val recoveryManager: RecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<RecoverySetupState>(RecoverySetupState.Idle)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun generateRecoveryKey() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            _state.value = RecoverySetupState.Generating
            recoveryManager.generateRecoveryKey(id)
                .onSuccess { keyBytes ->
                    _state.value = RecoverySetupState.Success(keyBytes)
                    // Refresh identity to reflect hasRecoveryKey = true
                    _identity.value = vault.getIdentity(keyId)
                }
                .onFailure { _state.value = RecoverySetupState.Error(it.message ?: "Generation failed") }
        }
    }
}

@Composable
fun RecoverySetupScreen(
    onBack: () -> Unit,
    viewModel: RecoverySetupViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

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
            Text("Recovery Setup", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tier 1: Recovery Key
            item {
                RecoveryTierCard(
                    emoji = "\uD83D\uDD11",
                    title = "Recovery Key",
                    description = "Generate offline recovery keypair",
                    badgeText = "Recommended",
                    badgeColor = Success,
                    badgeBgColor = SuccessDim,
                    isConfigured = identity?.hasRecoveryKey == true,
                    buttonText = "Generate Recovery Key",
                    buttonEnabled = identity != null && state !is RecoverySetupState.Generating,
                    isLoading = state is RecoverySetupState.Generating,
                    onClick = { viewModel.generateRecoveryKey() }
                )
            }

            // Recovery key output card (shown after successful generation)
            if (state is RecoverySetupState.Success) {
                item {
                    val keyBytes = (state as RecoverySetupState.Success).recoveryKeyBytes
                    val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WarningDim)
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text("Important", fontSize = 11.sp, color = Warning)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Store this key offline. It cannot be recovered.",
                                fontSize = 13.sp,
                                color = Warning
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "RECOVERY KEY",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                keyBase64,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(keyBase64)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Copy to Clipboard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Error message
            if (state is RecoverySetupState.Error) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerDim)
                    ) {
                        Text(
                            (state as RecoverySetupState.Error).message,
                            modifier = Modifier.padding(18.dp),
                            fontSize = 13.sp,
                            color = Danger
                        )
                    }
                }
            }

            // Tier 2: Social Recovery
            item {
                RecoveryTierCard(
                    emoji = "\uD83D\uDC65",
                    title = "Social Recovery",
                    description = "Split recovery secret among trusted contacts",
                    badgeText = "Advanced",
                    badgeColor = Accent,
                    badgeBgColor = AccentDim,
                    isConfigured = false,
                    buttonText = "Coming Soon",
                    buttonEnabled = false,
                    isLoading = false,
                    onClick = {}
                )
            }

            // Tier 3: Institutional
            item {
                RecoveryTierCard(
                    emoji = "\uD83C\uDFE2",
                    title = "Institutional",
                    description = "Organization holds recovery authority",
                    badgeText = "Enterprise",
                    badgeColor = Pqc,
                    badgeBgColor = PqcDim,
                    isConfigured = false,
                    buttonText = "Coming Soon",
                    buttonEnabled = false,
                    isLoading = false,
                    onClick = {}
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun RecoveryTierCard(
    emoji: String,
    title: String,
    description: String,
    badgeText: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    badgeBgColor: androidx.compose.ui.graphics.Color,
    isConfigured: Boolean,
    buttonText: String,
    buttonEnabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(badgeText, fontSize = 11.sp, color = badgeColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(description, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))

            if (isConfigured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessDim)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("\u2713", fontSize = 16.sp, color = Success, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("Configured", fontSize = 14.sp, color = Success, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = buttonEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = BgElevated
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Generating...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(buttonText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
