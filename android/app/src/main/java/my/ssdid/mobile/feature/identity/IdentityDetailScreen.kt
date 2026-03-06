package my.ssdid.mobile.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class IdentityDetailViewModel @Inject constructor(
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }
}

@Composable
fun IdentityDetailScreen(
    keyId: String,
    onBack: () -> Unit,
    onCredentialClick: (String) -> Unit,
    viewModel: IdentityDetailViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()

    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()) {
        Row(Modifier.padding(20.dp)) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Identity Details", style = MaterialTheme.typography.titleLarge)
        }

        identity?.let { id ->
            LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(id.name, style = MaterialTheme.typography.headlineMedium)
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(SuccessDim).padding(horizontal = 10.dp, vertical = 3.dp)) {
                                    Text("Active", fontSize = 11.sp, color = Success)
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            DetailRow("DID", id.did, mono = true)
                            DetailRow("Key ID", id.keyId, mono = true)
                            DetailRow("Algorithm", id.algorithm.name.replace("_", " "))
                            DetailRow("W3C Type", id.algorithm.w3cType, mono = true)
                            DetailRow("Created", id.createdAt)
                            DetailRow("Key Storage", "Hardware-backed")
                            DetailRow("Public Key", id.publicKeyMultibase.take(30) + "...", mono = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Border)
    }
}
