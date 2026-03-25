package my.ssdid.wallet.feature.verification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.ssdid.wallet.domain.verifier.offline.*
import my.ssdid.wallet.ui.theme.*

@Composable
fun VerificationResultScreen(
    result: UnifiedVerificationResult,
    onBack: () -> Unit
) {
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Verification Result", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Traffic light section
            item {
                TrafficLightCard(
                    result = result,
                    detailsExpanded = detailsExpanded,
                    onToggleDetails = { detailsExpanded = !detailsExpanded }
                )
            }

            // Offline badge
            if (result.source == VerificationSource.OFFLINE) {
                item { OfflineBadge() }
            }

            // Expandable details
            item {
                AnimatedVisibility(visible = detailsExpanded) {
                    VerificationDetailsSection(checks = result.checks)
                }
            }

            // Verification source row
            item {
                VerificationInfoRow(
                    label = "Source",
                    value = when (result.source) {
                        VerificationSource.ONLINE -> "Online"
                        VerificationSource.OFFLINE -> "Offline"
                    }
                )
            }

            // Bundle age
            result.bundleAge?.let { age ->
                item {
                    val hours = age.toHours()
                    val displayAge = when {
                        hours < 1 -> "Less than 1 hour"
                        hours == 1L -> "1 hour"
                        hours < 24 -> "$hours hours"
                        else -> "${age.toDays()} day${if (age.toDays() != 1L) "s" else ""}"
                    }
                    VerificationInfoRow(label = "Bundle age", value = displayAge)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TrafficLightCard(
    result: UnifiedVerificationResult,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit
) {
    val (icon, tint, label) = when (result.status) {
        VerificationStatus.VERIFIED ->
            Triple(Icons.Filled.CheckCircle, Success, "Credential verified")
        VerificationStatus.VERIFIED_OFFLINE ->
            Triple(Icons.Filled.CheckCircle, Success, "Credential verified offline")
        VerificationStatus.DEGRADED ->
            Triple(Icons.Filled.Warning, Warning, "Verified with limitations — tap for details")
        VerificationStatus.FAILED ->
            Triple(Icons.Filled.Cancel, Danger, "Verification failed — tap for details")
    }

    val isClickable = result.status == VerificationStatus.DEGRADED ||
            result.status == VerificationStatus.FAILED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onToggleDetails) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (isClickable) {
                    Text(
                        text = if (detailsExpanded) "Tap to hide details" else "Tap to view details",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineBadge() {
    Row {
        SuggestionChip(
            onClick = {},
            label = { Text("OFFLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = WarningDim,
                labelColor = Warning
            ),
            border = SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = Warning.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun VerificationDetailsSection(checks: List<VerificationCheck>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Verification Checks",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = Border)
            checks.forEach { check ->
                CheckRow(check = check)
            }
        }
    }
}

@Composable
private fun CheckRow(check: VerificationCheck) {
    val (icon, tint) = when (check.status) {
        CheckStatus.PASS -> Icons.Filled.CheckCircle to Success
        CheckStatus.FAIL -> Icons.Filled.Cancel to Danger
        CheckStatus.UNKNOWN -> Icons.Filled.Warning to Warning
    }

    val typeName = when (check.type) {
        CheckType.SIGNATURE -> "Signature"
        CheckType.EXPIRY -> "Expiry"
        CheckType.REVOCATION -> "Revocation"
        CheckType.BUNDLE_FRESHNESS -> "Bundle Freshness"
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(typeName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(check.message, fontSize = 12.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun VerificationInfoRow(label: String, value: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
