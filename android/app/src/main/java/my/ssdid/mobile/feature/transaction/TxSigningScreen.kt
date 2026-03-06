package my.ssdid.mobile.feature.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.ssdid.mobile.domain.SsdidClient
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

sealed class TxState {
    object Idle : TxState()
    object Loading : TxState()
    object Confirmed : TxState()
    data class Failed(val message: String) : TxState()
}

@HiltViewModel
class TxSigningViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val serverUrl: String = savedStateHandle["serverUrl"] ?: ""
    val sessionToken: String = savedStateHandle["sessionToken"] ?: ""

    private val _state = MutableStateFlow<TxState>(TxState.Idle)
    val state = _state.asStateFlow()

    private val _timerSeconds = MutableStateFlow(120)
    val timerSeconds = _timerSeconds.asStateFlow()

    private val _transactionDetails = MutableStateFlow<Map<String, String>>(emptyMap())
    val transactionDetails = _transactionDetails.asStateFlow()

    init {
        // Fetch transaction details from server
        viewModelScope.launch {
            client.fetchTransactionDetails(sessionToken, serverUrl)
                .onSuccess { _transactionDetails.value = it }
                .onFailure { _state.value = TxState.Failed("Failed to load transaction: ${it.message}") }
        }
        // Start visual countdown timer
        viewModelScope.launch {
            while (_timerSeconds.value > 0 && _state.value is TxState.Idle) {
                delay(1000)
                _timerSeconds.update { it - 1 }
            }
            if (_timerSeconds.value == 0 && _state.value is TxState.Idle) {
                _state.value = TxState.Failed("Challenge expired. Please scan QR again.")
            }
        }
    }

    fun signTransaction() {
        viewModelScope.launch {
            _state.value = TxState.Loading
            val identities = vault.listIdentities()
            val identity = identities.firstOrNull()
            if (identity == null) {
                _state.value = TxState.Failed("No identity available for signing")
                return@launch
            }
            val transaction = _transactionDetails.value
            if (transaction.isEmpty()) {
                _state.value = TxState.Failed("No transaction details available")
                return@launch
            }
            client.signTransaction(sessionToken, identity, transaction, serverUrl)
                .onSuccess { _state.value = TxState.Confirmed }
                .onFailure { _state.value = TxState.Failed(it.message ?: "Transaction signing failed") }
        }
    }
}

@Composable
fun TxSigningScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: TxSigningViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val txDetails by viewModel.transactionDetails.collectAsState()

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
            Text("Sign Transaction", style = MaterialTheme.typography.titleLarge)
        }

        when (state) {
            is TxState.Idle, is TxState.Loading -> {
                val isLoading = state is TxState.Loading

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Transaction details card
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("TRANSACTION DETAILS", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(14.dp))

                                if (txDetails.isEmpty()) {
                                    Text("Loading transaction details...", fontSize = 13.sp, color = TextSecondary)
                                } else {
                                    txDetails.entries.forEachIndexed { index, (key, value) ->
                                        val isAmount = key.equals("amount", ignoreCase = true)
                                        val isDid = value.startsWith("did:")
                                        TxDetailRow(
                                            label = key.replaceFirstChar { it.uppercase() },
                                            value = value,
                                            highlight = isAmount,
                                            mono = isDid
                                        )
                                        if (index < txDetails.size - 1) {
                                            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                                    TxDetailRow("Server", viewModel.serverUrl, mono = true)
                                }
                            }
                        }
                    }

                    // Security info section
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("SECURITY", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(10.dp))

                                // Challenge timer
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Challenge expires in", fontSize = 13.sp, color = TextSecondary)
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (timerSeconds > 30) SuccessDim else if (timerSeconds > 10) WarningDim else DangerDim
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "${timerSeconds / 60}:${String.format("%02d", timerSeconds % 60)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (timerSeconds > 30) Success else if (timerSeconds > 10) Warning else Danger
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(Success))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Transaction bound to challenge hash", fontSize = 12.sp, color = TextTertiary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(Success))
                                    Spacer(Modifier.width(8.dp))
                                    Text("SHA3-256 integrity verification", fontSize = 12.sp, color = TextTertiary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(Pqc))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Post-quantum signature (if available)", fontSize = 12.sp, color = TextTertiary)
                                }
                            }
                        }
                    }

                    // Biometric trigger area
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
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
                                        .background(WarningDim),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("\uD83D\uDD13", fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Biometric Required", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("Touch sensor to authorize signing", fontSize = 12.sp, color = TextTertiary)
                                }
                            }
                        }
                    }
                }

                // Sign button
                Button(
                    onClick = { viewModel.signTransaction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    enabled = !isLoading && timerSeconds > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Warning)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Signing...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BgPrimary)
                    } else {
                        Text("Sign Transaction", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BgPrimary)
                    }
                }
            }

            is TxState.Confirmed -> {
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
                    Text("Transaction Confirmed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Your transaction has been signed and submitted.", color = TextSecondary, fontSize = 14.sp)
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

            is TxState.Failed -> {
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
                    Text("Transaction Failed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text((state as TxState.Failed).message, color = TextSecondary, fontSize = 14.sp)
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

@Composable
private fun TxDetailRow(label: String, value: String, highlight: Boolean = false, mono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(
            value,
            fontSize = if (highlight) 16.sp else 13.sp,
            color = if (highlight) Warning else TextPrimary,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1
        )
    }
}
