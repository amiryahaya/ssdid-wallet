package my.ssdid.mobile.feature.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.mobile.domain.backup.BackupManager
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    object Creating : BackupState()
    data class Success(val backupBytes: ByteArray) : BackupState() {
        override fun equals(other: Any?): Boolean =
            other is Success && backupBytes.contentEquals(other.backupBytes)
        override fun hashCode(): Int = backupBytes.contentHashCode()
    }
    object Restoring : BackupState()
    data class RestoreSuccess(val count: Int) : BackupState()
    data class Error(val message: String) : BackupState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state = _state.asStateFlow()

    fun createBackup(passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupState.Creating
            backupManager.createBackup(passphrase)
                .onSuccess { _state.value = BackupState.Success(it) }
                .onFailure { _state.value = BackupState.Error(it.message ?: "Backup creation failed") }
        }
    }

    fun restoreBackup(data: ByteArray, passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupState.Restoring
            backupManager.restoreBackup(data, passphrase)
                .onSuccess { _state.value = BackupState.RestoreSuccess(it) }
                .onFailure { _state.value = BackupState.Error(it.message ?: "Restore failed") }
        }
    }

    fun resetState() {
        _state.value = BackupState.Idle
    }
}

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }

    val passphrasesMatch = passphrase == confirmPassphrase && passphrase.isNotEmpty()
    val canCreate = passphrasesMatch && state !is BackupState.Creating

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
            Text("Backup & Export", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // CREATE ENCRYPTED BACKUP section
            Text(
                "CREATE ENCRYPTED BACKUP",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            // Passphrase field
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase", color = TextTertiary) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )
            Spacer(Modifier.height(8.dp))

            // Confirm passphrase field
            OutlinedTextField(
                value = confirmPassphrase,
                onValueChange = { confirmPassphrase = it },
                label = { Text("Confirm Passphrase", color = TextTertiary) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )
            Spacer(Modifier.height(8.dp))

            // Passphrase strength indicator
            val strengthColor = when {
                passphrase.length >= 12 -> Success
                passphrase.length >= 8 -> Warning
                else -> Danger
            }
            val strengthFraction = when {
                passphrase.isEmpty() -> 0f
                passphrase.length >= 12 -> 1f
                passphrase.length >= 8 -> 0.66f
                else -> 0.33f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BgCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(strengthFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(strengthColor)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    passphrase.isEmpty() -> ""
                    passphrase.length >= 12 -> "Strong passphrase"
                    passphrase.length >= 8 -> "Moderate passphrase"
                    else -> "Weak passphrase"
                },
                fontSize = 11.sp,
                color = strengthColor
            )
            Spacer(Modifier.height(16.dp))

            // Create Backup button
            Button(
                onClick = { viewModel.createBackup(passphrase) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canCreate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (state is BackupState.Creating) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Creating Backup...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Create Backup", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Success card
            if (state is BackupState.Success) {
                val backupSize = (state as BackupState.Success).backupBytes.size
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessDim)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Backup Created Successfully",
                            fontWeight = FontWeight.SemiBold,
                            color = Success,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Size: ${formatBytes(backupSize)}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Error card
            if (state is BackupState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DangerDim)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Error",
                            fontWeight = FontWeight.SemiBold,
                            color = Danger,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (state as BackupState.Error).message,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Restore success card
            if (state is BackupState.RestoreSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessDim)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Restore Successful",
                            fontWeight = FontWeight.SemiBold,
                            color = Success,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(state as BackupState.RestoreSuccess).count} identities restored",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // RESTORE section
            Spacer(Modifier.height(8.dp))
            Text(
                "RESTORE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* File picker integration handled by navigation task */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
            ) {
                if (state is BackupState.Restoring) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Restoring...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Import Backup File", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Row(Modifier.padding(16.dp)) {
                    Text(
                        "Backups are encrypted with AES-256-GCM. Your passphrase is never stored.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
