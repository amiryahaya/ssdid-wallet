package my.ssdid.mobile.feature.auth

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
import androidx.compose.ui.text.font.FontWeight
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
import my.ssdid.mobile.domain.SsdidClient
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.model.VerifiableCredential
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthFlowViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _credentials = MutableStateFlow<List<VerifiableCredential>>(emptyList())
    val credentials = _credentials.asStateFlow()

    private val _selectedCredential = MutableStateFlow<VerifiableCredential?>(null)
    val selectedCredential = _selectedCredential.asStateFlow()

    init {
        viewModelScope.launch {
            _identities.value = vault.listIdentities()
            _credentials.value = vault.listCredentials()
        }
    }

    fun selectCredential(credential: VerifiableCredential) {
        _selectedCredential.value = credential
    }

    fun authenticate() {
        val credential = _selectedCredential.value ?: return
        viewModelScope.launch {
            _state.value = AuthState.Loading
            client.authenticate(credential, serverUrl)
                .onSuccess { _state.value = AuthState.Success }
                .onFailure { _state.value = AuthState.Error(it.message ?: "Authentication failed") }
        }
    }
}

@Composable
fun AuthFlowScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: AuthFlowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val credentials by viewModel.credentials.collectAsState()
    val selectedCredential by viewModel.selectedCredential.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Authentication", style = MaterialTheme.typography.titleLarge)
        }

        // Service info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("SERVICE REQUEST", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text("Server URL", fontSize = 11.sp, color = TextTertiary)
                Text(viewModel.serverUrl, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text("Action", fontSize = 11.sp, color = TextTertiary)
                Text("Authentication", fontSize = 13.sp, color = Accent)
            }
        }

        Spacer(Modifier.height(12.dp))

        when (state) {
            is AuthState.Idle, is AuthState.Loading -> {
                val isLoading = state is AuthState.Loading

                Text(
                    "SELECT CREDENTIAL",
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
                    items(credentials) { credential ->
                        val isSelected = selectedCredential?.id == credential.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) { viewModel.selectCredential(credential) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentDim else BgCard
                            )
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectCredential(credential) },
                                    enabled = !isLoading,
                                    colors = RadioButtonDefaults.colors(selectedColor = Accent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        credential.type.lastOrNull() ?: "Credential",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        credential.credentialSubject.id,
                                        fontSize = 11.sp,
                                        color = TextTertiary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (credentials.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BgCard)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No credentials available", color = TextSecondary)
                            }
                        }
                    }
                }

                // Biometric prompt trigger area
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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
                            Text("Biometric Confirmation", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("Touch sensor to confirm authentication", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.authenticate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = selectedCredential != null && !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Authenticating...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Authenticate", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            is AuthState.Success -> {
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
                        Text("\u2713", fontSize = 32.sp, color = Success, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Authentication Successful", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("You have been authenticated with the service.", color = TextSecondary, fontSize = 14.sp)
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

            is AuthState.Error -> {
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
                        Text("\u2717", fontSize = 32.sp, color = Danger, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Authentication Failed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text((state as AuthState.Error).message, color = TextSecondary, fontSize = 14.sp)
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
        }
    }
}
