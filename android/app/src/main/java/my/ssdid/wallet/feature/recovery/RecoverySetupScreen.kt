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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.withTimeout
import my.ssdid.wallet.R
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryConfig
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.OrgRecoveryConfig
import my.ssdid.wallet.domain.vault.Vault
import androidx.compose.ui.platform.LocalView
import my.ssdid.wallet.ui.components.HapticManager
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
    private val socialRecoveryManager: SocialRecoveryManager,
    private val institutionalManager: InstitutionalRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<RecoverySetupState>(RecoverySetupState.Idle)
    val state = _state.asStateFlow()

    private val _hasSocialRecovery = MutableStateFlow(false)
    val hasSocialRecovery = _hasSocialRecovery.asStateFlow()

    private val _socialConfig = MutableStateFlow<SocialRecoveryConfig?>(null)
    val socialConfig = _socialConfig.asStateFlow()

    private val _hasInstitutionalRecovery = MutableStateFlow(false)
    val hasInstitutionalRecovery = _hasInstitutionalRecovery.asStateFlow()

    private val _orgConfig = MutableStateFlow<OrgRecoveryConfig?>(null)
    val orgConfig = _orgConfig.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = vault.getIdentity(keyId)
                _identity.value = id
                if (id != null) {
                    _hasSocialRecovery.value = socialRecoveryManager.hasSocialRecovery(id.did)
                    _socialConfig.value = socialRecoveryManager.getConfig(id.did)
                    _hasInstitutionalRecovery.value = institutionalManager.hasOrgRecovery(id.did)
                    _orgConfig.value = institutionalManager.getConfig(id.did)
                }
            } catch (_: Exception) {
                // Identity load failed — leave as null
            }
        }
    }

    fun generateRecoveryKey() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            _state.value = RecoverySetupState.Generating
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    recoveryManager.generateRecoveryKey(id)
                        .onSuccess { keyBytes ->
                            _state.value = RecoverySetupState.Success(keyBytes)
                            _identity.value = vault.getIdentity(keyId)
                        }
                        .onFailure { _state.value = RecoverySetupState.Error(it.message ?: "Generation failed") }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = RecoverySetupState.Error("Operation timed out. Please try again.")
            }
        }
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 30_000L
    }
}

@Composable
fun RecoverySetupScreen(
    onBack: () -> Unit,
    onNavigateToSocialSetup: (String) -> Unit = {},
    onNavigateToInstitutionalSetup: (String) -> Unit = {},
    viewModel: RecoverySetupViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val state by viewModel.state.collectAsState()
    val hasSocialRecovery by viewModel.hasSocialRecovery.collectAsState()
    val socialConfig by viewModel.socialConfig.collectAsState()
    val hasInstitutionalRecovery by viewModel.hasInstitutionalRecovery.collectAsState()
    val orgConfig by viewModel.orgConfig.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current

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
            Text(stringResource(R.string.recovery_setup_title), style = MaterialTheme.typography.titleLarge)
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
                    title = stringResource(R.string.recovery_key_title),
                    description = stringResource(R.string.recovery_key_desc),
                    badgeText = stringResource(R.string.recovery_key_badge),
                    badgeColor = Success,
                    badgeBgColor = SuccessDim,
                    isConfigured = identity?.hasRecoveryKey == true,
                    buttonText = stringResource(R.string.recovery_generate_button),
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
                                    Text(stringResource(R.string.recovery_important), fontSize = 11.sp, color = Warning)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.recovery_key_store_offline),
                                fontSize = 13.sp,
                                color = Warning
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.recovery_key_label),
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
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(keyBase64))
                                    HapticManager.success(view)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(stringResource(R.string.recovery_copy_clipboard), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    title = stringResource(R.string.recovery_social_title),
                    description = if (hasSocialRecovery) {
                        val config = socialConfig
                        stringResource(R.string.recovery_social_config, config?.threshold ?: 0, config?.totalShares ?: 0)
                    } else {
                        stringResource(R.string.recovery_social_desc)
                    },
                    badgeText = stringResource(R.string.recovery_social_badge),
                    badgeColor = Accent,
                    badgeBgColor = AccentDim,
                    isConfigured = hasSocialRecovery,
                    buttonText = stringResource(R.string.recovery_social_button),
                    buttonEnabled = identity?.hasRecoveryKey == true,
                    isLoading = false,
                    onClick = { identity?.keyId?.let { onNavigateToSocialSetup(it) } }
                )
            }

            // Tier 3: Institutional
            item {
                RecoveryTierCard(
                    emoji = "\uD83C\uDFE2",
                    title = stringResource(R.string.recovery_institutional_title),
                    description = if (hasInstitutionalRecovery) {
                        orgConfig?.orgName ?: stringResource(R.string.recovery_institutional_desc)
                    } else {
                        stringResource(R.string.recovery_institutional_desc)
                    },
                    badgeText = stringResource(R.string.recovery_institutional_badge),
                    badgeColor = Pqc,
                    badgeBgColor = PqcDim,
                    isConfigured = hasInstitutionalRecovery,
                    buttonText = stringResource(R.string.recovery_institutional_button),
                    buttonEnabled = identity?.hasRecoveryKey == true,
                    isLoading = false,
                    onClick = { identity?.keyId?.let { onNavigateToInstitutionalSetup(it) } }
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
                    Text(stringResource(R.string.recovery_configured), fontSize = 14.sp, color = Success, fontWeight = FontWeight.SemiBold)
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
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = "Loading" },
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.recovery_generating), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(buttonText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
