package my.ssdid.wallet.feature.bundles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import my.ssdid.wallet.ui.theme.*

@Composable
fun BundleManagementScreen(
    onBack: () -> Unit,
    viewModel: BundleManagementViewModel = hiltViewModel()
) {
    val bundles by viewModel.bundles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Prepare for Offline",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refreshAll() }, enabled = !isRefreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh all", tint = Accent)
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add issuer", tint = Accent)
            }
        }

        // Loading indicator
        if (isRefreshing) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = BgCard
            )
        }

        // Error message
        error?.let { msg ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DangerDim)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = Danger, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss", color = Danger, fontSize = 12.sp)
                    }
                }
            }
        }

        if (bundles.isEmpty() && !isRefreshing) {
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No cached bundles",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add an issuer DID to enable offline verification",
                        fontSize = 13.sp,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Issuer")
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(bundles, key = { it.issuerDid }) { item ->
                    BundleCard(
                        item = item,
                        onDelete = { viewModel.deleteBundle(item.issuerDid) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddIssuerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { did ->
                viewModel.addByDid(did)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BundleCard(
    item: BundleUiItem,
    onDelete: () -> Unit
) {
    val (freshnessLabel, freshnessColor, freshnessBg) = when {
        item.freshnessRatio < 0.5 -> Triple("Fresh", Success, SuccessDim)
        item.freshnessRatio < 1.0 -> Triple("Aging", Warning, WarningDim)
        else -> Triple("Expired", Danger, DangerDim)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Fetched: ${item.fetchedAt}",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
            // Freshness badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = freshnessBg
            ) {
                Text(
                    freshnessLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = freshnessColor
                )
            }
            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete bundle",
                    tint = Danger,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddIssuerDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var didText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("Add Issuer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the issuer's DID to pre-fetch their verification bundle.",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
                OutlinedTextField(
                    value = didText,
                    onValueChange = { didText = it },
                    label = { Text("Issuer DID", color = TextTertiary) },
                    placeholder = { Text("did:ssdid:...", color = TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (didText.isNotBlank()) onAdd(didText) },
                enabled = didText.isNotBlank()
            ) {
                Text("Add", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextTertiary)
            }
        }
    )
}
