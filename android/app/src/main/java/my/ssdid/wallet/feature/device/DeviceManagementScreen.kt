package my.ssdid.wallet.feature.device

import android.os.Build
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
import androidx.compose.ui.text.style.TextAlign
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
import my.ssdid.wallet.domain.device.DeviceInfo
import my.ssdid.wallet.domain.device.DeviceManager
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.vault.Vault
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class DeviceManagementViewModel @Inject constructor(
    private val vault: Vault,
    private val deviceManager: DeviceManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _revokeError = MutableStateFlow<String?>(null)
    val revokeError = _revokeError.asStateFlow()

    init {
        viewModelScope.launch {
            val id = vault.getIdentity(keyId)
            _identity.value = id
            id?.let { loadDevices(it) }
        }
    }

    private suspend fun loadDevices(identity: Identity) {
        deviceManager.listDevices(identity)
            .onSuccess { _devices.value = it }
    }

    fun revokeDevice(targetKeyId: String) {
        val id = _identity.value ?: return
        viewModelScope.launch {
            deviceManager.revokeDevice(id, targetKeyId)
                .onSuccess {
                    _revokeError.value = null
                    loadDevices(id)
                }
                .onFailure { _revokeError.value = it.message }
        }
    }
}

@Composable
fun DeviceManagementScreen(
    onBack: () -> Unit,
    onEnrollDevice: ((String) -> Unit)? = null,
    viewModel: DeviceManagementViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val revokeError by viewModel.revokeError.collectAsState()

    val primaryDevice = devices.find { it.isPrimary }
    val otherDevices = devices.filter { !it.isPrimary }
    var pendingRevokeKey by remember { mutableStateOf<String?>(null) }

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
            Text("Devices", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // This Device
            item {
                Text("THIS DEVICE", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Accent)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                primaryDevice?.name ?: Build.MODEL,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SuccessDim)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "Primary",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Success
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        identity?.let { id ->
                            Text("KEY ID", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.keyId,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("ALGORITHM", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.algorithm.name.replace("_", "-"),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("ENROLLED", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.createdAt.take(10),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Other Devices
            item {
                Spacer(Modifier.height(8.dp))
                Text("OTHER DEVICES", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (otherDevices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\uD83D\uDCF1", fontSize = 28.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No other devices enrolled",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Enroll additional devices for multi-device access",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(otherDevices) { device ->
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        device.platform,
                                        fontSize = 11.sp,
                                        color = TextTertiary
                                    )
                                }
                                TextButton(
                                    onClick = { pendingRevokeKey = device.keyId },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                                ) {
                                    Text("Revoke", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("KEY ID", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                device.keyId,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Revoke error
            revokeError?.let { error ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerDim)
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Danger
                        )
                    }
                }
            }

            // Enroll button
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        identity?.let { id ->
                            onEnrollDevice?.invoke(id.keyId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = onEnrollDevice != null && identity != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = BgCard
                    )
                ) {
                    Text(
                        "Enroll New Device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Info note
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentDim)
                ) {
                    Text(
                        "Only the primary device can modify the DID Document. Secondary devices can authenticate but not manage the identity.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        color = Accent,
                        lineHeight = 18.sp
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        pendingRevokeKey?.let { targetKey ->
            AlertDialog(
                onDismissRequest = { pendingRevokeKey = null },
                title = { Text("Revoke Device") },
                text = { Text("This will permanently remove this device from your identity. Continue?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.revokeDevice(targetKey)
                        pendingRevokeKey = null
                    }) { Text("Revoke", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRevokeKey = null }) { Text("Cancel") }
                }
            )
        }
    }
}
