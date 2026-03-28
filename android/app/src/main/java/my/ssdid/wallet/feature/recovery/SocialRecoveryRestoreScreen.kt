package my.ssdid.wallet.feature.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import my.ssdid.wallet.R
import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Did
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryManager
import my.ssdid.sdk.domain.storage.OnboardingStorage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import my.ssdid.wallet.ui.theme.*
import java.util.Base64 as JBase64
import javax.inject.Inject

data class ShareEntry(val index: String = "", val data: String = "")

sealed class SocialRestoreState {
    object Idle : SocialRestoreState()
    object Restoring : SocialRestoreState()
    object Success : SocialRestoreState()
    data class Error(val message: String) : SocialRestoreState()
}

@HiltViewModel
class SocialRecoveryRestoreViewModel @Inject constructor(
    private val socialRecoveryManager: SocialRecoveryManager,
    private val ssdidClient: SsdidClient,
    private val storage: OnboardingStorage
) : ViewModel() {

    private val _state = MutableStateFlow<SocialRestoreState>(SocialRestoreState.Idle)
    val state: StateFlow<SocialRestoreState> = _state.asStateFlow()

    private val _shares = MutableStateFlow(listOf(ShareEntry(), ShareEntry()))
    val shares: StateFlow<List<ShareEntry>> = _shares.asStateFlow()

    fun updateShare(index: Int, entry: ShareEntry) {
        _shares.value = _shares.value.toMutableList().also { it[index] = entry }
    }

    fun addShare() {
        _shares.value = _shares.value + ShareEntry()
    }

    fun removeShare(index: Int) {
        if (_shares.value.size > 2) {
            _shares.value = _shares.value.toMutableList().also { it.removeAt(index) }
        }
    }

    fun restore(did: String, name: String, algorithm: Algorithm) {
        if (did.isBlank() || name.isBlank()) {
            _state.value = SocialRestoreState.Error("DID and identity name are required")
            return
        }
        Did.validate(did).onFailure {
            _state.value = SocialRestoreState.Error("Invalid DID format: ${it.message}")
            return
        }

        val currentShares = _shares.value
        val collectedShares = mutableMapOf<Int, String>()
        for ((i, share) in currentShares.withIndex()) {
            val shareIndex = share.index.trim().toIntOrNull()
            if (shareIndex == null) {
                _state.value = SocialRestoreState.Error("Share ${i + 1}: index must be a number")
                return
            }
            if (share.data.isBlank()) {
                _state.value = SocialRestoreState.Error("Share ${i + 1}: data is required")
                return
            }
            try {
                JBase64.getUrlDecoder().decode(share.data.trim())
            } catch (_: IllegalArgumentException) {
                _state.value = SocialRestoreState.Error("Share ${i + 1}: invalid Base64 data")
                return
            }
            collectedShares[shareIndex] = share.data.trim()
        }

        if (collectedShares.keys.size != currentShares.size) {
            _state.value = SocialRestoreState.Error("Duplicate share indices detected")
            return
        }

        viewModelScope.launch {
            _state.value = SocialRestoreState.Restoring
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    socialRecoveryManager.recoverWithShares(did, collectedShares, name, algorithm)
                        .onSuccess { identity ->
                            try {
                                ssdidClient.updateDidDocument(identity.keyId)
                            } catch (_: Exception) {
                                // Best effort: registry update may fail on new device
                            }
                            storage.setOnboardingCompleted()
                            _state.value = SocialRestoreState.Success
                        }
                        .onFailure {
                            _state.value = SocialRestoreState.Error(it.message ?: "Recovery failed")
                        }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = SocialRestoreState.Error("Operation timed out. Please try again.")
            }
        }
    }

    fun resetState() {
        _state.value = SocialRestoreState.Idle
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 30_000L
        const val MAX_DID_LENGTH = 256
        const val MAX_NAME_LENGTH = 100
        const val MAX_SHARE_INDEX_LENGTH = 5
        const val MAX_SHARE_DATA_LENGTH = 10_000
    }
}

@Composable
fun SocialRecoveryRestoreScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: SocialRecoveryRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val shares by viewModel.shares.collectAsState()
    var did by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(Algorithm.KAZ_SIGN_192) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Navigate back" }
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.social_restore_title), style = MaterialTheme.typography.titleLarge)
        }

        when (state) {
            is SocialRestoreState.Success -> {
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
                                    .background(SuccessDim)
                                    .semantics { contentDescription = "Success" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Success", modifier = Modifier.size(28.dp), tint = Success)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.social_restore_success_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.social_restore_success_desc),
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
                            stringResource(R.string.social_restore_desc),
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    item {
                        Text(stringResource(R.string.social_restore_did_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = did,
                            onValueChange = { did = it.take(SocialRecoveryRestoreViewModel.MAX_DID_LENGTH) },
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
                            onValueChange = { name = it.take(SocialRecoveryRestoreViewModel.MAX_NAME_LENGTH) },
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

                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.social_restore_shares_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                    }

                    itemsIndexed(shares) { index, share ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.social_restore_share_label, index + 1),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (shares.size > 2) {
                                        TextButton(
                                            onClick = { viewModel.removeShare(index) },
                                            modifier = Modifier.semantics {
                                                contentDescription = "Remove share ${index + 1}"
                                            }
                                        ) {
                                            Text(stringResource(R.string.social_remove), color = Danger, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = share.index,
                                    onValueChange = {
                                        viewModel.updateShare(index, share.copy(index = it.take(SocialRecoveryRestoreViewModel.MAX_SHARE_INDEX_LENGTH)))
                                    },
                                    placeholder = { Text(stringResource(R.string.social_restore_share_index_hint), color = TextTertiary) },
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
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = share.data,
                                    onValueChange = {
                                        viewModel.updateShare(index, share.copy(data = it.take(SocialRecoveryRestoreViewModel.MAX_SHARE_DATA_LENGTH)))
                                    },
                                    placeholder = { Text(stringResource(R.string.social_restore_share_data_hint), color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent,
                                        unfocusedBorderColor = Border,
                                        cursorColor = Accent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }
                        }
                    }

                    item {
                        TextButton(onClick = { viewModel.addShare() }) {
                            Text(stringResource(R.string.social_restore_add_share), color = Accent, fontSize = 14.sp)
                        }
                    }

                    val errorState = state as? SocialRestoreState.Error
                    if (errorState != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DangerDim)
                            ) {
                                Text(
                                    errorState.message,
                                    color = Danger,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }

                Button(
                    onClick = { viewModel.restore(did, name, selectedAlgorithm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = did.isNotBlank() && name.isNotBlank() &&
                        shares.all { it.index.isNotBlank() && it.data.isNotBlank() } &&
                        state !is SocialRestoreState.Restoring,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (state is SocialRestoreState.Restoring) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = "Loading" },
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.social_restore_recovering), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(
                            stringResource(R.string.social_restore_recover_button),
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
