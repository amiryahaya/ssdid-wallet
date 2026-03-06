package my.ssdid.mobile.feature.credentials

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
import my.ssdid.mobile.domain.model.VerifiableCredential
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.ui.theme.*
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
        Row(Modifier.padding(20.dp)) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Credentials", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(credentials) { vc ->
                Card(
                    Modifier.fillMaxWidth().clickable { onCredentialClick(vc.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Row {
                        Box(Modifier.width(4.dp).height(100.dp).background(Accent))
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(vc.type.lastOrNull() ?: "Credential", style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(SuccessDim).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Valid", fontSize = 10.sp, color = Success)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(vc.id.takeLast(20), style = MaterialTheme.typography.headlineSmall)
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
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No credentials yet", color = TextSecondary)
                    }
                }
            }
        }
    }
}
