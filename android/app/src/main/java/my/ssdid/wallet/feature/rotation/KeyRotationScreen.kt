package my.ssdid.wallet.feature.rotation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.rotation.KeyRotationManager
import my.ssdid.sdk.domain.rotation.RotationEntry
import my.ssdid.sdk.domain.rotation.RotationStatus
import my.ssdid.sdk.domain.vault.Vault
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class RotationState {
    object Idle : RotationState()
    object Preparing : RotationState()
    object Rotating : RotationState()
    data class Success(val message: String) : RotationState()
    data class Error(val message: String) : RotationState()
}

@HiltViewModel
class KeyRotationViewModel @Inject constructor(
    private val vault: Vault,
    private val rotationManager: KeyRotationManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _status = MutableStateFlow<RotationStatus?>(null)
    val status = _status.asStateFlow()

    private val _state = MutableStateFlow<RotationState>(RotationState.Idle)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val id = vault.getIdentity(keyId)
            _identity.value = id
            id?.let { _status.value = rotationManager.getRotationStatus(it) }
        }
    }

    fun prepareRotation() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            _state.value = RotationState.Preparing
            rotationManager.prepareRotation(id)
                .onSuccess {
                    _identity.value = vault.getIdentity(keyId)
                    _identity.value?.let { _status.value = rotationManager.getRotationStatus(it) }
                    _state.value = RotationState.Success("Pre-commitment created")
                }
                .onFailure { _state.value = RotationState.Error(it.message ?: "Failed") }
        }
    }

    fun executeRotation() {
        val id = _identity.value ?: return
        viewModelScope.launch {
            _state.value = RotationState.Rotating
            rotationManager.executeRotation(id)
                .onSuccess { newIdentity ->
                    _identity.value = newIdentity
                    _status.value = rotationManager.getRotationStatus(newIdentity)
                    _state.value = RotationState.Success("Key rotated successfully")
                }
                .onFailure { _state.value = RotationState.Error(it.message ?: "Rotation failed") }
        }
    }
}

@Composable
fun KeyRotationScreen(
    onBack: () -> Unit,
    viewModel: KeyRotationViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val status by viewModel.status.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("Key Rotation", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Current key info
            item {
                Text("CURRENT KEY", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            item {
                identity?.let { id ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("KEY ID", fontSize = 11.sp, color = TextTertiary)
                                val algColor = if (id.algorithm.isPostQuantum) Pqc else Classical
                                val algBg = if (id.algorithm.isPostQuantum) PqcDim else ClassicalDim
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(algBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        id.algorithm.name.replace("_", "-"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = algColor
                                    )
                                }
                            }
                            Text(
                                id.keyId,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("CREATED", fontSize = 11.sp, color = TextTertiary)
                            Text(id.createdAt.take(10), fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Pre-rotation status
            item {
                Spacer(Modifier.height(4.dp))
                Text("PRE-ROTATION STATUS", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        status?.let { s ->
                            if (s.hasPreCommitment) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = "Pre-committed", modifier = Modifier.size(16.dp), tint = Success)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Next key pre-committed", fontSize = 14.sp, color = Success)
                                }
                                s.nextKeyHash?.let { hash ->
                                    Spacer(Modifier.height(8.dp))
                                    Text("HASH", fontSize = 11.sp, color = TextTertiary)
                                    Text(
                                        hash.take(24) + "...",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = "Warning", modifier = Modifier.size(16.dp), tint = Warning)
                                    Spacer(Modifier.width(8.dp))
                                    Text("No pre-commitment", fontSize = 14.sp, color = Warning)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Generate a pre-committed next key for safe rotation.",
                                    fontSize = 12.sp,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                Spacer(Modifier.height(8.dp))
                val inProgress = state is RotationState.Preparing || state is RotationState.Rotating

                if (status?.hasPreCommitment != true) {
                    Button(
                        onClick = { viewModel.prepareRotation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inProgress,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        if (state is RotationState.Preparing) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("Prepare Pre-Commitment", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.executeRotation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inProgress,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        if (state is RotationState.Rotating) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rotate Now", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Warning
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningDim)
                ) {
                    Text(
                        "Current key remains valid for 5 minutes after rotation (grace period).",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        color = Warning,
                        lineHeight = 18.sp
                    )
                }
            }

            // Status messages
            if (state is RotationState.Success) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessDim)
                    ) {
                        Text(
                            (state as RotationState.Success).message,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Success
                        )
                    }
                }
            }
            if (state is RotationState.Error) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerDim)
                    ) {
                        Text(
                            (state as RotationState.Error).message,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Danger
                        )
                    }
                }
            }

            // Rotation history
            val history = status?.rotationHistory ?: emptyList()
            if (history.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("RECENT ROTATIONS", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                items(history) { entry ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PqcDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rotated", modifier = Modifier.size(14.dp), tint = Pqc)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "#${entry.oldKeyIdFragment} \u2192 #${entry.newKeyIdFragment}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                                Text(
                                    entry.timestamp.take(10),
                                    fontSize = 11.sp,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
