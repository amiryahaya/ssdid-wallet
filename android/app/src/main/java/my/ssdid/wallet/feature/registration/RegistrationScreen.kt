package my.ssdid.wallet.feature.registration

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
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

enum class RegistrationStep(val label: String) {
    CONNECTING("Connecting"),
    MUTUAL_AUTH("Mutual Auth"),
    IDENTITY_SELECT("Identity Select"),
    COMPLETE("Complete")
}

sealed class RegistrationState {
    object Idle : RegistrationState()
    data class InProgress(val step: RegistrationStep) : RegistrationState()
    object Success : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val serverDid: String = savedStateHandle["serverDid"] ?: ""

    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    init {
        viewModelScope.launch { _identities.value = vault.listIdentities() }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
    }

    fun register() {
        val identity = _selectedIdentity.value ?: return
        viewModelScope.launch {
            _state.value = RegistrationState.InProgress(RegistrationStep.CONNECTING)
            client.registerWithService(identity, serverUrl)
                .onSuccess { _state.value = RegistrationState.Success }
                .onFailure { _state.value = RegistrationState.Error(it.message ?: "Registration failed") }
        }
    }
}

@Composable
fun RegistrationScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val selectedIdentity by viewModel.selectedIdentity.collectAsState()

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
            Text("Service Registration", style = MaterialTheme.typography.titleLarge)
        }

        // Step indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val currentStep = when (state) {
                is RegistrationState.InProgress -> (state as RegistrationState.InProgress).step
                is RegistrationState.Success -> RegistrationStep.COMPLETE
                else -> null
            }
            RegistrationStep.entries.forEach { step ->
                val isActive = currentStep != null && step.ordinal <= currentStep.ordinal
                val isCurrent = currentStep == step
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isCurrent -> Accent
                                    isActive -> Success
                                    else -> BgCard
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isActive && !isCurrent) "\u2713" else "${step.ordinal + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive || isCurrent) BgPrimary else TextTertiary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(step.label, fontSize = 10.sp, color = if (isActive) TextPrimary else TextTertiary)
                }
            }
        }

        // Server info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("SERVER INFO", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text("URL", fontSize = 11.sp, color = TextTertiary)
                Text(viewModel.serverUrl, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text("DID", fontSize = 11.sp, color = TextTertiary)
                Text(viewModel.serverDid, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Content based on state
        when (state) {
            is RegistrationState.Idle, is RegistrationState.InProgress -> {
                val inProgress = state is RegistrationState.InProgress

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
                                .clickable(enabled = !inProgress) { viewModel.selectIdentity(identity) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentDim else BgCard
                            )
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectIdentity(identity) },
                                    enabled = !inProgress,
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

                // Register button
                Button(
                    onClick = { viewModel.register() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = selectedIdentity != null && !inProgress,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Registering...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Register with Service", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            is RegistrationState.Success -> {
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
                    Text("Registration Complete", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Your identity has been registered with this service.", color = TextSecondary, fontSize = 14.sp)
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

            is RegistrationState.Error -> {
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
                    Text("Registration Failed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (state as RegistrationState.Error).message,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
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
