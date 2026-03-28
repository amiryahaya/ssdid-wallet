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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.notify.LocalNotificationStore
import my.ssdid.sdk.domain.vault.Vault
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import my.ssdid.wallet.ui.components.AlgorithmBadge
import my.ssdid.wallet.ui.components.truncatedDid
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class WalletHomeViewModel @Inject constructor(
    private val vault: Vault,
    private val localNotificationStorage: LocalNotificationStore
) : ViewModel() {
    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _credentialCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val credentialCounts = _credentialCounts.asStateFlow()

    val unreadNotificationCount = localNotificationStorage.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val identitiesList = vault.listIdentities()
            _identities.value = identitiesList
            val counts = mutableMapOf<String, Int>()
            for (id in identitiesList) {
                counts[id.did] = vault.getCredentialsForDid(id.did).size
            }
            _credentialCounts.value = counts
            _isLoading.value = false
        }
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
    onNotifications: () -> Unit,
    viewModel: WalletHomeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val credentialCounts by viewModel.credentialCounts.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("IDENTITY WALLET", style = MaterialTheme.typography.labelMedium)
                Text("Self-Sovereign Digital ID", style = MaterialTheme.typography.headlineLarge)
            }
            val unreadCount by viewModel.unreadNotificationCount.collectAsState()
            // Notifications bell
            Box {
                IconButton(
                    onClick = onNotifications,
                    modifier = Modifier.semantics { contentDescription = "Notifications" }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextSecondary)
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BgPrimary
                        )
                    }
                }
            }
            // Settings gear
            IconButton(
                onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = "Settings" }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
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
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics { contentDescription = "Create new identity" }
                    ) {
                        Text("+ New", color = Accent, fontSize = 13.sp)
                    }
                }
            }

            items(identities, key = { it.keyId }) { identity ->
                IdentityCard(
                    identity = identity,
                    credentialCount = credentialCounts[identity.did] ?: 0,
                    onClick = { onIdentityClick(identity.keyId) }
                )
            }

            if (isLoading && identities.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            } else if (identities.isEmpty()) {
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
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(28.dp), tint = Accent)
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
                    QuickActionCard("Scan QR", Icons.Default.QrCodeScanner, Modifier.weight(1f), onScanQr)
                    QuickActionCard("Credentials", Icons.Default.Description, Modifier.weight(1f), onCredentials)
                    QuickActionCard("History", Icons.Default.History, Modifier.weight(1f), onHistory)
                }
            }
        }
    }
}

@Composable
fun IdentityCard(identity: Identity, credentialCount: Int = 0, onClick: () -> Unit) {
    val algColor = if (identity.algorithm.isPostQuantum) Pqc else Classical

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
                AlgorithmBadge(
                    algorithmName = identity.algorithm.name,
                    isPostQuantum = identity.algorithm.isPostQuantum
                )
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
            identity.email?.let { email ->
                Text(email, fontSize = 12.sp, color = TextTertiary)
            }
            if (credentialCount > 0) {
                Text(
                    "$credentialCount service${if (credentialCount != 1) "s" else ""} connected",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))
            Text("Created ${identity.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun QuickActionCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = Accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
        }
    }
}
