package my.ssdid.wallet.feature.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import my.ssdid.wallet.ui.theme.*
import java.util.Locale

@Composable
fun PresentationRequestScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: PresentationRequestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Navigate away on success after a brief moment
    LaunchedEffect(state) {
        if (state is PresentationRequestViewModel.UiState.Success) {
            kotlinx.coroutines.delay(1500)
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
            IconButton(onClick = {
                if (state is PresentationRequestViewModel.UiState.CredentialMatch) {
                    viewModel.decline()
                }
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Presentation Request", style = MaterialTheme.typography.titleLarge)
        }

        when (val current = state) {
            is PresentationRequestViewModel.UiState.Loading -> {
                LoadingContent()
            }
            is PresentationRequestViewModel.UiState.CredentialMatch -> {
                CredentialMatchContent(
                    state = current,
                    onToggleClaim = viewModel::toggleClaim,
                    onApprove = viewModel::approve,
                    onDecline = {
                        viewModel.decline()
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            is PresentationRequestViewModel.UiState.Submitting -> {
                SubmittingContent()
            }
            is PresentationRequestViewModel.UiState.Success -> {
                SuccessContent()
            }
            is PresentationRequestViewModel.UiState.Error -> {
                ErrorContent(
                    message = current.message,
                    onRetry = viewModel::retry,
                    onDismiss = onBack
                )
            }
            is PresentationRequestViewModel.UiState.NoCredentials -> {
                NoCredentialsContent(onDismiss = onBack)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text("Processing request...", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SubmittingContent() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text("Sharing...", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SuccessContent() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier.size(48.dp),
                tint = Success
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Presentation shared",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Error",
                modifier = Modifier.size(48.dp),
                tint = Danger
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun NoCredentialsContent(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No matching credentials",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You don't have any credentials that match this request.",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CredentialMatchContent(
    state: PresentationRequestViewModel.UiState.CredentialMatch,
    onToggleClaim: (String) -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            // Verifier info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Verifier",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.verifierId,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This verifier is requesting access to your credential.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Credential type badge
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CREDENTIAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentDim)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            state.credentialType,
                            fontSize = 9.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Claims section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "REQUESTED CLAIMS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }

            items(state.claims) { claim ->
                ClaimCard(
                    claim = claim,
                    onToggle = { onToggleClaim(claim.name) }
                )
            }
        }

        // Footer buttons
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Share", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ClaimCard(
    claim: PresentationRequestViewModel.ClaimUiItem,
    onToggle: () -> Unit
) {
    val claimLabel = claim.name.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            Modifier
                .clickable(enabled = !claim.required) { onToggle() }
                .semantics {
                    contentDescription = "$claimLabel, ${if (claim.selected) "selected" else "not selected"}, ${if (claim.required) "required" else "optional"}"
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = claim.selected,
                onCheckedChange = { if (!claim.required) onToggle() },
                enabled = !claim.required,
                colors = CheckboxDefaults.colors(checkedColor = Accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                claimLabel,
                fontSize = 14.sp,
                color = if (claim.available) TextPrimary else TextTertiary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (!claim.available) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DangerDim)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Unavailable", fontSize = 11.sp, color = Danger)
                }
            } else {
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
}
