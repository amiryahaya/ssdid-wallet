package my.ssdid.wallet.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import androidx.compose.foundation.shape.CircleShape
import my.ssdid.wallet.ui.components.truncatedDid
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class WalletHomeViewModel @Inject constructor(private val vault: Vault) : ViewModel() {
    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _identities.value = vault.listIdentities() }
    }
}

@Composable
fun WalletHomeScreen(
    onCreateIdentity: () -> Unit,
    onIdentityClick: (String) -> Unit,
    onScanQr: () -> Unit,
    onCredentials: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: WalletHomeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("IDENTITY WALLET", style = MaterialTheme.typography.labelMedium)
                Text("Self-Sovereign Digital ID", style = MaterialTheme.typography.headlineLarge)
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = "Settings" }
            ) {
                Text("\u2699", fontSize = 22.sp, color = TextSecondary)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section: My Identities
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MY IDENTITIES", style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = onCreateIdentity,
                        modifier = Modifier.semantics { contentDescription = "Create new identity" }
                    ) {
                        Text("+ New", color = Accent, fontSize = 13.sp)
                    }
                }
            }

            items(identities) { identity ->
                IdentityCard(identity = identity, onClick = { onIdentityClick(identity.keyId) })
            }

            if (identities.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgCard)
                            .clickable(onClick = onCreateIdentity)
                            .semantics { contentDescription = "Create your first identity" }
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
                            Text("\uD83D\uDC64", fontSize = 28.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No identities yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create your first identity to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Quick Actions
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("QUICK ACTIONS", style = MaterialTheme.typography.labelMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("Scan QR", Modifier.weight(1f), onScanQr)
                    QuickActionCard("Credentials", Modifier.weight(1f), onCredentials)
                    QuickActionCard("History", Modifier.weight(1f), onHistory)
                }
            }
        }
    }
}

@Composable
fun IdentityCard(identity: Identity, onClick: () -> Unit) {
    val algColor = if (identity.algorithm.isPostQuantum) Pqc else Classical
    val algBgColor = if (identity.algorithm.isPostQuantum) PqcDim else ClassicalDim

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Identity: ${identity.name}, ${identity.algorithm.name.replace("_", "-")}" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        // Top accent line
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(algColor)
        )
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(algBgColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        identity.algorithm.name.replace("_", "-"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = algColor
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(SuccessDim)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Success)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(identity.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                identity.did.truncatedDid(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))
            Text("Created ${identity.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun QuickActionCard(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2B21", color = Accent, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
        }
    }
}
