package my.ssdid.wallet.feature.credentials

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.revocation.RevocationManager
import my.ssdid.wallet.domain.revocation.RevocationStatus
import my.ssdid.wallet.domain.vault.Vault
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class CredentialDetailViewModel @Inject constructor(
    private val vault: Vault,
    private val revocationManager: RevocationManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val credentialId: String = savedStateHandle["credentialId"] ?: ""

    private val _credential = MutableStateFlow<VerifiableCredential?>(null)
    val credential = _credential.asStateFlow()

    private val _revocationStatus = MutableStateFlow<RevocationStatus?>(null)
    val revocationStatus = _revocationStatus.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()

    init {
        viewModelScope.launch {
            val vc = vault.listCredentials().find { it.id == credentialId }
            _credential.value = vc
            if (vc != null) {
                _revocationStatus.value = revocationManager.checkRevocation(vc)
            }
        }
    }

    fun deleteCredential() {
        viewModelScope.launch {
            vault.deleteCredential(credentialId)
                .onSuccess { _deleted.value = true }
        }
    }
}

@Composable
fun CredentialDetailScreen(
    credentialId: String,
    onBack: () -> Unit,
    viewModel: CredentialDetailViewModel = hiltViewModel()
) {
    val credential by viewModel.credential.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val revocationStatus by viewModel.revocationStatus.collectAsState()
    var showRawJson by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                Spacer(Modifier.width(4.dp))
                Text("Credential Details", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = { showDeleteDialog = true }) {
                Text("Delete", color = Danger, fontSize = 14.sp)
            }
        }

        credential?.let { vc ->
            LazyColumn(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main info card
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        vc.type.lastOrNull() ?: "Credential",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    if (vc.type.contains("SdJwtVerifiableCredential")) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AccentDim)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "SD-JWT VC",
                                                fontSize = 9.sp,
                                                color = Accent,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                val isExpired = vc.expirationDate?.let {
                                    try { Instant.now().isAfter(Instant.parse(it)) } catch (_: Exception) { false }
                                } ?: false
                                val isRevoked = revocationStatus == RevocationStatus.REVOKED
                                val statusUnknown = revocationStatus == RevocationStatus.UNKNOWN

                                val statusText = when {
                                    isRevoked -> "Revoked"
                                    isExpired -> "Expired"
                                    statusUnknown -> "Status Unknown"
                                    else -> "Valid"
                                }
                                val statusColor = when {
                                    isRevoked || isExpired -> Danger
                                    statusUnknown -> Warning
                                    else -> Success
                                }
                                val statusBg = when {
                                    isRevoked || isExpired -> DangerDim
                                    statusUnknown -> WarningDim
                                    else -> SuccessDim
                                }

                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusBg)
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        statusText,
                                        fontSize = 11.sp,
                                        color = statusColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            CredDetailRow("ID", vc.id, mono = true)
                            CredDetailRow("Type", vc.type.joinToString(", "))
                            CredDetailRow("Issuer", vc.issuer, mono = true)
                            CredDetailRow("Issuance Date", vc.issuanceDate)
                            vc.expirationDate?.let { CredDetailRow("Expiration Date", it) }
                        }
                    }
                }

                // Credential subject claims
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("CREDENTIAL SUBJECT", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(12.dp))

                            CredDetailRow("Subject ID", vc.credentialSubject.id, mono = true)

                            if (vc.credentialSubject.claims.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("CLAIMS", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(8.dp))
                                vc.credentialSubject.claims.forEach { (key, value) ->
                                    CredDetailRow(key, value)
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text("No additional claims", fontSize = 13.sp, color = TextTertiary)
                            }
                        }
                    }
                }

                // Proof info
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("PROOF", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(12.dp))
                            CredDetailRow("Type", vc.proof.type)
                            CredDetailRow("Created", vc.proof.created)
                            CredDetailRow("Purpose", vc.proof.proofPurpose)
                            CredDetailRow("Verification Method", vc.proof.verificationMethod, mono = true)
                        }
                    }
                }

                // Raw JSON toggle
                item {
                    TextButton(
                        onClick = { showRawJson = !showRawJson },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (showRawJson) "Hide Raw JSON" else "Show Raw JSON",
                            color = Accent,
                            fontSize = 14.sp
                        )
                    }
                }

                if (showRawJson) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgSecondary)
                        ) {
                            val prettyJson = Json { prettyPrint = true }
                            val rawJson = prettyJson.encodeToString(vc)
                            Text(
                                rawJson,
                                modifier = Modifier.padding(14.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Bottom spacer
                item { Spacer(Modifier.height(20.dp)) }
            }
        } ?: run {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Credential", color = TextPrimary) },
            text = { Text("Are you sure you want to delete this credential? This action cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCredential()
                }) {
                    Text("Delete", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgElevated
        )
    }
}

@Composable
private fun CredDetailRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Border)
    }
}
