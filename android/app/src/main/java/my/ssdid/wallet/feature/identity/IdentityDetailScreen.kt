package my.ssdid.wallet.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import my.ssdid.wallet.ui.components.HapticManager
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
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class IdentityDetailViewModel @Inject constructor(
    private val vault: Vault,
    private val ssdidClient: SsdidClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isDeactivating = MutableStateFlow(false)
    val isDeactivating = _isDeactivating.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun deactivateIdentity(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isDeactivating.value = true
            _error.value = null
            val result = ssdidClient.deactivateDid(keyId)
            _isDeactivating.value = false
            result.onSuccess {
                onComplete()
            }.onFailure { e ->
                io.sentry.Sentry.captureException(e)
                val body = if (e is retrofit2.HttpException) {
                    try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                } else null
                _error.value = listOfNotNull(e.message, body).joinToString("\n")
                    .ifBlank { "Deactivation failed" }
            }
        }
    }

    fun clearError() { _error.value = null }
}

@Composable
fun IdentityDetailScreen(
    keyId: String,
    onBack: () -> Unit,
    onCredentialClick: (String) -> Unit,
    onRecoverySetup: (String) -> Unit = {},
    onKeyRotation: (String) -> Unit = {},
    onDeviceManagement: (String) -> Unit = {},
    viewModel: IdentityDetailViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()
    val error by viewModel.error.collectAsState()
    val isDeactivating by viewModel.isDeactivating.collectAsState()
    val view = LocalView.current

    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Back" }
            ) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(4.dp))
            Text("Identity Details", style = MaterialTheme.typography.titleLarge)
        }

        var showDeactivateDialog by remember { mutableStateOf(false) }

        error?.let { msg ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DangerDim)
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    color = Danger,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        identity?.let { id ->
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(id.name, style = MaterialTheme.typography.headlineMedium)
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(SuccessDim).padding(horizontal = 10.dp, vertical = 3.dp)) {
                                    Text("Active", fontSize = 11.sp, color = Success)
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            val clipboardManager = LocalClipboardManager.current
                            var didCopied by remember { mutableStateOf(false) }

                            // M6: Reset "Copied" label after 2 seconds
                            LaunchedEffect(didCopied) {
                                if (didCopied) {
                                    kotlinx.coroutines.delay(2000)
                                    didCopied = false
                                }
                            }

                            Column(Modifier.padding(vertical = 8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("DID", style = MaterialTheme.typography.labelSmall)
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(id.did))
                                            didCopied = true
                                            HapticManager.success(view)
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = if (didCopied) "DID copied" else "Copy DID"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            if (didCopied) "Copied" else "Copy",
                                            fontSize = 12.sp,
                                            color = Accent
                                        )
                                    }
                                }
                                Text(
                                    id.did,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Border)
                            }
                            DetailRow("Key ID", id.keyId, mono = true)
                            DetailRow("Algorithm", id.algorithm.name.replace("_", " "))
                            DetailRow("W3C Type", id.algorithm.w3cType, mono = true)
                            DetailRow("Created", id.createdAt)
                            DetailRow("Key Storage", "Hardware-backed")
                            DetailRow("Public Key", id.publicKeyMultibase.take(30) + "...", mono = true)
                        }
                    }
                }

                // Action buttons
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("ACTIONS", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard("\uD83D\uDD11", "Recovery", Modifier.weight(1f)) { onRecoverySetup(keyId) }
                        ActionCard("\uD83D\uDD04", "Rotate Key", Modifier.weight(1f)) { onKeyRotation(keyId) }
                        ActionCard("\uD83D\uDCF1", "Devices", Modifier.weight(1f)) { onDeviceManagement(keyId) }
                    }
                }

                // Danger zone
                item {
                    Spacer(Modifier.height(24.dp))
                    Text("DANGER ZONE", style = MaterialTheme.typography.labelMedium, color = Danger)
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showDeactivateDialog = true },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = if (isDeactivating) "Deactivating identity" else "Deactivate identity"
                        },
                        enabled = !isDeactivating,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isDeactivating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Deactivating...", color = Color.White)
                        } else {
                            Text("Deactivate Identity", color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (showDeactivateDialog) {
            AlertDialog(
                onDismissRequest = { showDeactivateDialog = false },
                title = { Text("Deactivate Identity?") },
                text = {
                    Text(
                        "This is irreversible. Your DID will be permanently " +
                            "deactivated on the registry and all associated " +
                            "data will be deleted."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeactivateDialog = false
                            viewModel.deactivateIdentity { onBack() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Danger)
                    ) {
                        Text("Deactivate", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeactivateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ActionCard(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Accent, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Border)
    }
}
