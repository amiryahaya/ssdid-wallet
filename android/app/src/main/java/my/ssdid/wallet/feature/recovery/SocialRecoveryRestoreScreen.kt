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
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.VaultStorage
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
    private val storage: VaultStorage
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
        if (!did.startsWith("did:")) {
            _state.value = SocialRestoreState.Error("Invalid DID format")
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
            // Validate Base64 format
            try {
                JBase64.getUrlDecoder().decode(share.data.trim())
            } catch (_: IllegalArgumentException) {
                _state.value = SocialRestoreState.Error("Share ${i + 1}: invalid Base64 data")
                return
            }
            collectedShares[shareIndex] = share.data.trim()
        }

        // Check for duplicate indices
        val indices = collectedShares.keys
        if (indices.size != currentShares.size) {
            _state.value = SocialRestoreState.Error("Duplicate share indices detected")
            return
        }

        viewModelScope.launch {
            _state.value = SocialRestoreState.Restoring
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
    }

    fun resetState() {
        _state.value = SocialRestoreState.Idle
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
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Social Recovery", style = MaterialTheme.typography.titleLarge)
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
                                    .background(SuccessDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2713", fontSize = 28.sp, color = Success, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Identity Restored",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your identity has been recovered using guardian shares.",
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
                                    "Continue",
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
                            "Collect shares from your guardians to recover your identity. " +
                                "You need the minimum threshold of shares to reconstruct your recovery key.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }

                    item {
                        Text("DID", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = did,
                            onValueChange = { did = it },
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
                        Text("IDENTITY NAME", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
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
                        Text("SIGNATURE ALGORITHM", style = MaterialTheme.typography.labelMedium)
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
                                    Row(Modifier.padding(14.dp)) {
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
                        Text("GUARDIAN SHARES", style = MaterialTheme.typography.labelMedium)
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
                                        "Share ${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (shares.size > 2) {
                                        TextButton(onClick = { viewModel.removeShare(index) }) {
                                            Text("Remove", color = Danger, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = share.index,
                                    onValueChange = {
                                        viewModel.updateShare(index, share.copy(index = it))
                                    },
                                    placeholder = { Text("Share index (number)", color = TextTertiary) },
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
                                        viewModel.updateShare(index, share.copy(data = it))
                                    },
                                    placeholder = { Text("Paste Base64 share data", color = TextTertiary) },
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
                            Text("+ Add Share", color = Accent, fontSize = 14.sp)
                        }
                    }

                    // Error card
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

                // Footer button
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
                            Modifier.size(20.dp),
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Recovering...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(
                            "Recover Identity",
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
