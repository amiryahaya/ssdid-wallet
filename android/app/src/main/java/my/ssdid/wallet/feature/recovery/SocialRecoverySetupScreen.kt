package my.ssdid.wallet.feature.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

// --- State ---

data class GuardianEntry(
    val name: String = "",
    val did: String = ""
)

sealed class SocialSetupState {
    object Idle : SocialSetupState()
    object Creating : SocialSetupState()
    data class Success(
        val guardianShares: List<Pair<String, String>>  // (name, shareData)
    ) : SocialSetupState()
    data class Error(val message: String) : SocialSetupState()
}

// --- ViewModel ---

@HiltViewModel
class SocialRecoverySetupViewModel @Inject constructor(
    private val socialRecoveryManager: SocialRecoveryManager,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    private val _state = MutableStateFlow<SocialSetupState>(SocialSetupState.Idle)
    val state = _state.asStateFlow()

    private val _guardians = MutableStateFlow(listOf(GuardianEntry(), GuardianEntry()))
    val guardians = _guardians.asStateFlow()

    private val _threshold = MutableStateFlow(2)
    val threshold = _threshold.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }

    fun updateGuardian(index: Int, entry: GuardianEntry) {
        val list = _guardians.value.toMutableList()
        if (index in list.indices) {
            list[index] = entry
            _guardians.value = list
        }
    }

    fun addGuardian() {
        _guardians.value = _guardians.value + GuardianEntry()
        // Ensure threshold does not exceed new guardian count
        if (_threshold.value > _guardians.value.size) {
            _threshold.value = _guardians.value.size
        }
    }

    fun removeGuardian(index: Int) {
        if (_guardians.value.size <= 2) return
        val list = _guardians.value.toMutableList()
        list.removeAt(index)
        _guardians.value = list
        // Clamp threshold
        if (_threshold.value > list.size) {
            _threshold.value = list.size
        }
    }

    fun setThreshold(value: Int) {
        val clamped = value.coerceIn(2, _guardians.value.size)
        _threshold.value = clamped
    }

    fun createShares() {
        val id = _identity.value ?: return
        val guardianList = _guardians.value
        val thresh = _threshold.value

        // Validation
        if (guardianList.any { it.name.isBlank() || it.did.isBlank() }) {
            _state.value = SocialSetupState.Error("All guardians must have a name and DID")
            return
        }

        viewModelScope.launch {
            _state.value = SocialSetupState.Creating
            val guardianPairs = guardianList.map { it.name to it.did }
            socialRecoveryManager.setupSocialRecovery(id, guardianPairs, thresh)
                .onSuccess { sharesById ->
                    // Use stored config to match guardian IDs to shares deterministically
                    val config = socialRecoveryManager.getConfig(id.did)
                    val guardianShares = if (config != null) {
                        config.guardians.mapNotNull { guardian ->
                            sharesById[guardian.id]?.let { share -> guardian.name to share }
                        }
                    } else {
                        // Fallback: map entries preserve insertion order in Kotlin
                        sharesById.entries.zip(guardianList).map { (entry, guardian) ->
                            guardian.name to entry.value
                        }
                    }
                    _state.value = SocialSetupState.Success(guardianShares = guardianShares)
                }
                .onFailure {
                    _state.value = SocialSetupState.Error(it.message ?: "Failed to create shares")
                }
        }
    }

    fun resetState() {
        _state.value = SocialSetupState.Idle
    }
}

// --- Screen ---

@Composable
fun SocialRecoverySetupScreen(
    onBack: () -> Unit,
    viewModel: SocialRecoverySetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val guardians by viewModel.guardians.collectAsState()
    val threshold by viewModel.threshold.collectAsState()
    val clipboardManager = LocalClipboardManager.current

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
            Text("Social Recovery", style = MaterialTheme.typography.titleLarge)
        }

        when (val currentState = state) {
            is SocialSetupState.Success -> {
                SuccessContent(
                    state = currentState,
                    clipboardManager = clipboardManager,
                    onDone = onBack
                )
            }
            else -> {
                SetupFormContent(
                    guardians = guardians,
                    threshold = threshold,
                    state = state,
                    onUpdateGuardian = viewModel::updateGuardian,
                    onAddGuardian = viewModel::addGuardian,
                    onRemoveGuardian = viewModel::removeGuardian,
                    onSetThreshold = viewModel::setThreshold,
                    onCreateShares = viewModel::createShares
                )
            }
        }
    }
}

@Composable
private fun SetupFormContent(
    guardians: List<GuardianEntry>,
    threshold: Int,
    state: SocialSetupState,
    onUpdateGuardian: (Int, GuardianEntry) -> Unit,
    onAddGuardian: () -> Unit,
    onRemoveGuardian: (Int) -> Unit,
    onSetThreshold: (Int) -> Unit,
    onCreateShares: () -> Unit
) {
    val isCreating = state is SocialSetupState.Creating

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Threshold selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Threshold", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSetThreshold(threshold - 1) },
                            enabled = threshold > 2 && !isCreating,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("\u2212", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "$threshold of ${guardians.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = { onSetThreshold(threshold + 1) },
                            enabled = threshold < guardians.size && !isCreating,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Minimum $threshold guardian(s) needed to recover",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            }
        }

        // Guardian cards
        itemsIndexed(guardians) { index, guardian ->
            GuardianCard(
                index = index,
                guardian = guardian,
                canRemove = guardians.size > 2,
                isCreating = isCreating,
                onUpdate = { onUpdateGuardian(index, it) },
                onRemove = { onRemoveGuardian(index) }
            )
        }

        // Add guardian button
        item {
            OutlinedButton(
                onClick = onAddGuardian,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Add Guardian", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Error card
        if (state is SocialSetupState.Error) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DangerDim)
                ) {
                    Text(
                        state.message,
                        modifier = Modifier.padding(18.dp),
                        fontSize = 13.sp,
                        color = Danger
                    )
                }
            }
        }

        // Create shares button
        item {
            Button(
                onClick = onCreateShares,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = BgPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Creating Shares...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Create Shares", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun GuardianCard(
    index: Int,
    guardian: GuardianEntry,
    canRemove: Boolean,
    isCreating: Boolean,
    onUpdate: (GuardianEntry) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Guardian ${index + 1}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (canRemove) {
                    TextButton(
                        onClick = onRemove,
                        enabled = !isCreating
                    ) {
                        Text("Remove", fontSize = 12.sp, color = Danger)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = guardian.name,
                onValueChange = { onUpdate(guardian.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = guardian.did,
                onValueChange = { onUpdate(guardian.copy(did = it)) },
                label = { Text("DID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }
    }
}

@Composable
private fun SuccessContent(
    state: SocialSetupState.Success,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onDone: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Success banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessDim)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2713", fontSize = 18.sp, color = Success, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Social recovery created successfully",
                        fontSize = 14.sp,
                        color = Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Warning about distributing shares
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WarningDim)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WarningDim)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("Important", fontSize = 11.sp, color = Warning)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Distribute each share to the corresponding guardian securely. " +
                            "Do not store multiple shares together. " +
                            "Each guardian should only receive their own share.",
                        fontSize = 13.sp,
                        color = Warning
                    )
                }
            }
        }

        // Per-guardian share cards
        itemsIndexed(state.guardianShares) { index, (guardianName, share) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        guardianName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SHARE ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        share,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(share)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(
                            "Copy Share",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Done button
        item {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
