package my.ssdid.wallet.feature.auth

import android.content.Intent
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.ui.theme.*
import java.util.Locale

@Composable
fun ConsentScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCreateIdentity: (String) -> Unit = {},
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val selectedIdentity by viewModel.selectedIdentity.collectAsState()
    val selectedClaims by viewModel.selectedClaims.collectAsState()
    val serverName by viewModel.serverName.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val isSubmitting = state is ConsentState.Submitting

    // Handle success
    LaunchedEffect(state) {
        if (state is ConsentState.Success) {
            val token = (state as ConsentState.Success).sessionToken
            if (viewModel.hasCallback) {
                val uri = viewModel.buildCallbackUri(token)
                if (uri != null) context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("\u2190", color = TextPrimary, fontSize = 20.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text("Sign In Request", style = MaterialTheme.typography.titleLarge)
        }

        // Error card
        if (state is ConsentState.Error) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DangerDim)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2717", fontSize = 16.sp, color = Danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (state as ConsentState.Error).message,
                        fontSize = 13.sp,
                        color = Danger
                    )
                }
            }
        }

        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            // Service info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            serverName.ifEmpty { "Service" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            viewModel.serverUrl,
                            fontSize = 12.sp,
                            color = TextTertiary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This service is requesting to verify your identity and access selected information.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Identity section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "IDENTITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }

            if (identities.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(
                            Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No compatible identities found",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onCreateIdentity(viewModel.serverUrl) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(
                                    "Create New Identity",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                items(identities) { identity ->
                    IdentityCard(
                        identity = identity,
                        isSelected = selectedIdentity?.keyId == identity.keyId,
                        enabled = !isSubmitting,
                        onClick = { viewModel.selectIdentity(identity) }
                    )
                }
            }

            // Requested information section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "REQUESTED INFORMATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }

            items(viewModel.requestedClaims) { claim ->
                val isSelected = selectedClaims[claim.key] == true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Row(
                        Modifier
                            .clickable(enabled = !claim.required && !isSubmitting) {
                                viewModel.toggleClaim(claim.key)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (!claim.required) viewModel.toggleClaim(claim.key)
                            },
                            enabled = !claim.required && !isSubmitting,
                            colors = CheckboxDefaults.colors(checkedColor = Accent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            claim.key.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            },
                            fontSize = 14.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (claim.required) AccentDim else BgPrimary)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (claim.required) "Required" else "Optional",
                                fontSize = 11.sp,
                                color = if (claim.required) Accent else TextTertiary
                            )
                        }
                    }
                }
            }

            // Authentication section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "AUTHENTICATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\uD83D\uDD13", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Biometric + Hardware Key",
                                fontSize = 14.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Your identity will be confirmed using biometric authentication and a hardware-backed cryptographic key.",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        }

        // Footer
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val bioUsed = viewModel.requireBiometric(activity!!)
                        if (bioUsed) viewModel.approve(biometricUsed = true)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedIdentity != null && !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = BgPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Approving...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Approve", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (viewModel.hasCallback) {
                        val uri = viewModel.buildDeclineCallbackUri()
                        if (uri != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun IdentityCard(
    identity: Identity,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentDim else BgCard
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = Accent)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    identity.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    identity.did,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (identity.algorithm.isPostQuantum) SuccessDim else BgPrimary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    identity.algorithm.name.replace("_", "-"),
                    fontSize = 10.sp,
                    color = if (identity.algorithm.isPostQuantum) Success else TextTertiary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
