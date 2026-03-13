package my.ssdid.wallet.feature.credentials

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.vault.Vault
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import my.ssdid.wallet.ui.components.truncatedDid
import my.ssdid.wallet.ui.theme.*
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CredentialsViewModel @Inject constructor(private val vault: Vault) : ViewModel() {
    private val _credentials = MutableStateFlow<List<VerifiableCredential>>(emptyList())
    val credentials = _credentials.asStateFlow()

    init { viewModelScope.launch { _credentials.value = vault.listCredentials() } }
}

@Composable
fun CredentialsScreen(
    onBack: () -> Unit,
    onCredentialClick: (String) -> Unit,
    viewModel: CredentialsViewModel = hiltViewModel()
) {
    val credentials by viewModel.credentials.collectAsState()

    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()) {
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Credentials", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(credentials) { vc ->
                val credType = vc.type.lastOrNull() ?: "Credential"
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCredentialClick(vc.id) }
                        .semantics { contentDescription = "$credType from ${vc.issuer}" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Row {
                        Box(Modifier.width(4.dp).height(100.dp).background(Accent))
                        Column(Modifier.padding(14.dp)) {
                            val isExpired = vc.expirationDate?.let {
                                try { Instant.now().isAfter(Instant.parse(it)) } catch (_: Exception) { false }
                            } ?: false
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(vc.type.lastOrNull() ?: "Credential", style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (isExpired) DangerDim else SuccessDim).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text(if (isExpired) "Expired" else "Valid", fontSize = 10.sp, color = if (isExpired) Danger else Success)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(vc.id.truncatedDid(), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Issuer: ${vc.issuer}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Issued: ${vc.issuanceDate.take(10)}", style = MaterialTheme.typography.bodySmall)
                                vc.expirationDate?.let { Text("Expires: ${it.take(10)}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }

            if (credentials.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
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
                            Text("\uD83D\uDCC4", fontSize = 28.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No credentials yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Register with a service to receive your first credential",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
