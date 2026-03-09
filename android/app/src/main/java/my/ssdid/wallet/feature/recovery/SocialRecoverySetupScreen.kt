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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.withTimeout
import my.ssdid.wallet.R
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

    private val _hasExistingConfig = MutableStateFlow(false)
    val hasExistingConfig = _hasExistingConfig.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = vault.getIdentity(keyId)
                _identity.value = id
                if (id != null) {
                    _hasExistingConfig.value = socialRecoveryManager.hasSocialRecovery(id.did)
                }
            } catch (_: Exception) {
                // Identity load failed — leave as null
            }
        }
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
    }

    fun removeGuardian(index: Int) {
        if (_guardians.value.size <= 2) return
        val list = _guardians.value.toMutableList()
        list.removeAt(index)
        _guardians.value = list
        if (_threshold.value > list.size) {
            _threshold.value = list.size
        }
    }

    fun setThreshold(value: Int) {
        _threshold.value = value.coerceIn(2, _guardians.value.size)
    }

    fun createShares() {
        val id = _identity.value ?: return
        val guardianList = _guardians.value
        val thresh = _threshold.value

        if (guardianList.any { it.name.isBlank() || it.did.isBlank() }) {
            _state.value = SocialSetupState.Error("All guardians must have a name and DID")
            return
        }
        if (guardianList.any { !it.did.startsWith("did:") }) {
            _state.value = SocialSetupState.Error("All guardian DIDs must be valid DID format")
            return
        }

        viewModelScope.launch {
            _state.value = SocialSetupState.Creating
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    val guardianPairs = guardianList.map { it.name to it.did }
                    socialRecoveryManager.setupSocialRecovery(id, guardianPairs, thresh)
                        .onSuccess { sharesById ->
                            val config = socialRecoveryManager.getConfig(id.did)
                            val guardianShares = if (config != null) {
                                config.guardians.mapNotNull { guardian ->
                                    sharesById[guardian.id]?.let { share -> guardian.name to share }
                                }
                            } else {
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
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = SocialSetupState.Error("Operation timed out. Please try again.")
            }
        }
    }

    fun resetState() {
        _state.value = SocialSetupState.Idle
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 30_000L
        const val MAX_NAME_LENGTH = 100
        const val MAX_DID_LENGTH = 256
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
    val hasExistingConfig by viewModel.hasExistingConfig.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.social_confirm_overwrite_title)) },
            text = { Text(stringResource(R.string.social_confirm_overwrite_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.createShares()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Navigate back" }
            ) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.social_setup_title), style = MaterialTheme.typography.titleLarge)
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
                    hasExistingConfig = hasExistingConfig,
                    onUpdateGuardian = viewModel::updateGuardian,
                    onAddGuardian = viewModel::addGuardian,
                    onRemoveGuardian = viewModel::removeGuardian,
                    onSetThreshold = viewModel::setThreshold,
                    onCreateShares = {
                        if (hasExistingConfig) showConfirmDialog = true
                        else viewModel.createShares()
                    }
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
    hasExistingConfig: Boolean,
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
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.social_threshold), fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSetThreshold(threshold - 1) },
                            enabled = threshold > 2 && !isCreating,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.semantics { contentDescription = "Decrease threshold" }
                        ) {
                            Text("\u2212", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.social_threshold_display, threshold, guardians.size),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = { onSetThreshold(threshold + 1) },
                            enabled = threshold < guardians.size && !isCreating,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.semantics { contentDescription = "Increase threshold" }
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.social_threshold_info, threshold),
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            }
        }

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

        item {
            OutlinedButton(
                onClick = onAddGuardian,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.social_add_guardian), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

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
                        Modifier.size(20.dp).semantics { contentDescription = "Loading" },
                        color = BgPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.social_creating_shares), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(stringResource(R.string.social_create_shares), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    stringResource(R.string.social_guardian_label, index + 1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (canRemove) {
                    TextButton(
                        onClick = onRemove,
                        enabled = !isCreating,
                        modifier = Modifier.semantics {
                            contentDescription = "Remove guardian ${index + 1}"
                        }
                    ) {
                        Text(stringResource(R.string.social_remove), fontSize = 12.sp, color = Danger)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = guardian.name,
                onValueChange = { onUpdate(guardian.copy(name = it.take(SocialRecoverySetupViewModel.MAX_NAME_LENGTH))) },
                label = { Text(stringResource(R.string.social_guardian_name_label)) },
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
                onValueChange = { onUpdate(guardian.copy(did = it.take(SocialRecoverySetupViewModel.MAX_DID_LENGTH))) },
                label = { Text(stringResource(R.string.social_guardian_did_label)) },
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
                        stringResource(R.string.social_success_message),
                        fontSize = 14.sp,
                        color = Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

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
                        Text(stringResource(R.string.recovery_important), fontSize = 11.sp, color = Warning)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.social_distribute_warning),
                        fontSize = 13.sp,
                        color = Warning
                    )
                }
            }
        }

        itemsIndexed(state.guardianShares) { index, (guardianName, share) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(guardianName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.social_share_label, index + 1),
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
                        Text(stringResource(R.string.social_copy_share), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(stringResource(R.string.done), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
