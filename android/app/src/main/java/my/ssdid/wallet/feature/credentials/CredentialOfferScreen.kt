package my.ssdid.wallet.feature.credentials

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.ui.components.HapticManager
import my.ssdid.wallet.ui.theme.*

@Composable
fun CredentialOfferScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: CredentialOfferViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val view = LocalView.current

    LaunchedEffect(state) {
        if (state is CredentialOfferUiState.Success) HapticManager.success(view)
    }

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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Credential Offer", style = MaterialTheme.typography.titleLarge)
        }

        when (val currentState = state) {
            is CredentialOfferUiState.Loading -> LoadingContent("Processing offer...")

            is CredentialOfferUiState.ReviewingOffer -> ReviewingOfferContent(
                issuerName = currentState.issuerName,
                credentialTypes = currentState.credentialTypes,
                identities = currentState.identities,
                selectedIdentity = currentState.selectedIdentity,
                onSelectIdentity = viewModel::selectIdentity,
                onAccept = viewModel::acceptOffer,
                onDecline = { viewModel.decline(); onBack() }
            )

            is CredentialOfferUiState.PinEntry -> PinEntryContent(
                description = currentState.description,
                length = currentState.length,
                inputMode = currentState.inputMode,
                onSubmit = viewModel::submitPin,
                onCancel = { viewModel.decline(); onBack() }
            )

            is CredentialOfferUiState.Processing -> LoadingContent("Requesting credential...")

            is CredentialOfferUiState.Success -> SuccessContent(onComplete = onComplete)

            is CredentialOfferUiState.Deferred -> DeferredContent(
                transactionId = currentState.transactionId,
                onDone = onComplete
            )

            is CredentialOfferUiState.Error -> ErrorContent(
                message = currentState.message,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ColumnScope.LoadingContent(message: String) {
    Spacer(Modifier.weight(1f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text(message, color = TextSecondary, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.ReviewingOfferContent(
    issuerName: String,
    credentialTypes: List<String>,
    identities: List<Identity>,
    selectedIdentity: Identity?,
    onSelectIdentity: (Identity) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Issuer info card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("ISSUER", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(issuerName, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
    }

    Spacer(Modifier.height(12.dp))

    // Credential types
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("CREDENTIALS OFFERED", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            credentialTypes.forEach { type ->
                Text(type, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Identity selector
    Text(
        "SELECT IDENTITY",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
    Spacer(Modifier.height(8.dp))

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(identities) { identity ->
            val isSelected = selectedIdentity?.keyId == identity.keyId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectIdentity(identity) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentDim else BgCard
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectIdentity(identity) },
                        colors = RadioButtonDefaults.colors(selectedColor = Accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(identity.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            identity.did,
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (identities.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No identities available", color = TextSecondary)
                }
            }
        }
    }

    // Accept/Decline buttons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) {
            Text("Decline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onAccept,
            modifier = Modifier.weight(1f),
            enabled = selectedIdentity != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Accept", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColumnScope.PinEntryContent(
    description: String?,
    length: Int,
    inputMode: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }

    Spacer(Modifier.weight(1f))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Transaction Code Required", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            description ?: "Please enter the transaction code provided by the issuer.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pinValue,
            onValueChange = { newValue ->
                if (length <= 0 || newValue.length <= length) {
                    pinValue = newValue
                }
            },
            label = { Text("Transaction Code") },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (inputMode == "numeric") KeyboardType.Number else KeyboardType.Text
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (length > 0) {
            Spacer(Modifier.height(4.dp))
            Text("${pinValue.length}/$length characters", fontSize = 12.sp, color = TextTertiary)
        }
    }
    Spacer(Modifier.weight(1f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) {
            Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = { onSubmit(pinValue) },
            modifier = Modifier.weight(1f),
            enabled = pinValue.isNotEmpty() && (length <= 0 || pinValue.length == length),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Submit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColumnScope.SuccessContent(onComplete: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SuccessDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = "Success", modifier = Modifier.size(32.dp), tint = Success)
        }
        Spacer(Modifier.height(20.dp))
        Text("Credential Received", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("The credential has been stored in your wallet.", color = TextSecondary, fontSize = 14.sp)
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onComplete,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Success)
    ) {
        Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.DeferredContent(transactionId: String, onDone: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WarningDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, contentDescription = "Pending", modifier = Modifier.size(32.dp), tint = Warning)
        }
        Spacer(Modifier.height(20.dp))
        Text("Credential Pending", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your credential is being processed and will be available later.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Transaction ID: $transactionId",
            fontSize = 11.sp,
            color = TextTertiary,
            fontFamily = FontFamily.Monospace
        )
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Warning)
    ) {
        Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.ErrorContent(message: String, onBack: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DangerDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Error", modifier = Modifier.size(32.dp), tint = Danger)
        }
        Spacer(Modifier.height(20.dp))
        Text("Offer Failed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextSecondary, fontSize = 14.sp)
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Danger)
    ) {
        Text("Go Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
