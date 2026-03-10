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
import my.ssdid.wallet.domain.credential.CredentialIssuanceManager
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.dto.CredentialOfferResponse
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

sealed class CredentialOfferState {
    object Loading : CredentialOfferState()
    data class OfferLoaded(val offer: CredentialOfferResponse) : CredentialOfferState()
    object Accepting : CredentialOfferState()
    object Success : CredentialOfferState()
    data class Error(val message: String) : CredentialOfferState()
}

@HiltViewModel
class CredentialOfferViewModel @Inject constructor(
    private val issuanceManager: CredentialIssuanceManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val issuerUrl: String = savedStateHandle["issuerUrl"] ?: ""
    val offerId: String = savedStateHandle["offerId"] ?: ""

    private val _state = MutableStateFlow<CredentialOfferState>(CredentialOfferState.Loading)
    val state = _state.asStateFlow()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    private val _selectedIdentity = MutableStateFlow<Identity?>(null)
    val selectedIdentity = _selectedIdentity.asStateFlow()

    init {
        viewModelScope.launch {
            _identities.value = vault.listIdentities()
            fetchOffer()
        }
    }

    private suspend fun fetchOffer() {
        _state.value = CredentialOfferState.Loading
        issuanceManager.fetchOffer(issuerUrl, offerId)
            .onSuccess { _state.value = CredentialOfferState.OfferLoaded(it) }
            .onFailure { _state.value = CredentialOfferState.Error(it.message ?: "Failed to fetch offer") }
    }

    fun selectIdentity(identity: Identity) {
        _selectedIdentity.value = identity
    }

    fun accept() {
        val identity = _selectedIdentity.value ?: return
        viewModelScope.launch {
            _state.value = CredentialOfferState.Accepting
            issuanceManager.acceptOffer(issuerUrl, offerId, identity)
                .onSuccess { _state.value = CredentialOfferState.Success }
                .onFailure { _state.value = CredentialOfferState.Error(it.message ?: "Failed to accept offer") }
        }
    }
}

@Composable
fun CredentialOfferScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: CredentialOfferViewModel = hiltViewModel()
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
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(4.dp))
            Text("Credential Offer", style = MaterialTheme.typography.titleLarge)
        }

        when (val currentState = state) {
            is CredentialOfferState.Loading -> {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(16.dp))
                        Text("Fetching offer details...", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            is CredentialOfferState.OfferLoaded -> {
                OfferDetailsContent(
                    offer = currentState.offer,
                    identities = identities,
                    selectedIdentity = selectedIdentity,
                    onSelectIdentity = viewModel::selectIdentity,
                    onAccept = viewModel::accept,
                    onReject = onBack
                )
            }

            is CredentialOfferState.Accepting -> {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(16.dp))
                        Text("Accepting credential...", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            is CredentialOfferState.Success -> {
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
                    Text("Credential Accepted", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
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

            is CredentialOfferState.Error -> {
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
                    Text("Offer Failed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(currentState.message, color = TextSecondary, fontSize = 14.sp)
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
private fun ColumnScope.OfferDetailsContent(
    offer: CredentialOfferResponse,
    identities: List<Identity>,
    selectedIdentity: Identity?,
    onSelectIdentity: (Identity) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    // Offer info card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("OFFER DETAILS", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text("Issuer", fontSize = 11.sp, color = TextTertiary)
            Text(offer.issuer_did, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text("Credential Type", fontSize = 11.sp, color = TextTertiary)
            Text(offer.credential_type, fontSize = 13.sp, color = Accent)
            if (offer.expires_at != null) {
                Spacer(Modifier.height(6.dp))
                Text("Expires", fontSize = 11.sp, color = TextTertiary)
                Text(offer.expires_at, fontSize = 13.sp, color = Warning)
            }
        }
    }

    // Claims preview
    if (offer.claims.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("CLAIMS", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                offer.claims.forEach { (key, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(key, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.weight(1f))
                        Text(value, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(2f))
                    }
                }
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

    // Accept/Reject buttons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) {
            Text("Reject", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
