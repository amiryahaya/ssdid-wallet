package my.ssdid.wallet.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.res.stringResource
import my.ssdid.wallet.R
import my.ssdid.sdk.domain.backup.BackupManager
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalView
import my.ssdid.wallet.ui.components.HapticManager
import my.ssdid.wallet.ui.theme.*
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
    private val backupManager: BackupManager,
    private val biometricAuth: BiometricAuthenticator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val restoreUri: String = savedStateHandle["restoreUri"] ?: ""

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state = _state.asStateFlow()

    var lastBackupBytes: ByteArray? = null
        private set

    private val _loadedFileBytes = MutableStateFlow<ByteArray?>(null)
    val loadedFileBytes: StateFlow<ByteArray?> = _loadedFileBytes

    suspend fun requireBiometricForBackup(activity: FragmentActivity): Boolean {
        if (!biometricAuth.canAuthenticate(activity)) return true
        return biometricAuth.authenticate(
            activity,
            "Backup Wallet",
            "Authenticate to proceed"
        ) is BiometricResult.Success
    }

    suspend fun requireBiometricForRestore(activity: FragmentActivity): Boolean {
        if (!biometricAuth.canAuthenticate(activity)) return true
        return biometricAuth.authenticate(
            activity,
            "Restore Wallet",
            "Authenticate to proceed"
        ) is BiometricResult.Success
    }

    fun createBackup(passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupState.Creating
            backupManager.createBackup(passphrase.toCharArray())
                .onSuccess { bytes ->
                    lastBackupBytes = bytes
                    _state.value = BackupState.Success(bytes)
                }
                .onFailure { _state.value = BackupState.Error(it.message ?: "Backup creation failed") }
        }
    }

    fun onBackupSaved() {
        lastBackupBytes = null
    }

    fun onBackupFileLoaded(bytes: ByteArray) {
        _loadedFileBytes.value = bytes
    }

    fun restoreBackup(passphrase: String) {
        val bytes = _loadedFileBytes.value ?: return
        viewModelScope.launch {
            _state.value = BackupState.Restoring
            backupManager.restoreBackup(bytes, passphrase.toCharArray())
                .onSuccess { count ->
                    _loadedFileBytes.value = null
                    _state.value = BackupState.RestoreSuccess(count)
                }
                .onFailure { _state.value = BackupState.Error(it.message ?: "Restore failed") }
        }
    }

    fun restoreBackup(data: ByteArray, passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupState.Restoring
            backupManager.restoreBackup(data, passphrase.toCharArray())
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
    val loadedFileBytes by viewModel.loadedFileBytes.collectAsState()
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // Haptic feedback on backup success
    LaunchedEffect(state) {
        if (state is BackupState.Success) HapticManager.success(view)
    }

    // Auto-load backup file from share intent
    LaunchedEffect(viewModel.restoreUri) {
        if (viewModel.restoreUri.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(viewModel.restoreUri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited() }
                if (bytes != null) {
                    viewModel.onBackupFileLoaded(bytes)
                }
            } catch (e: SecurityException) {
                Log.w("BackupScreen", "Permission denied reading shared backup file", e)
            } catch (e: Exception) {
                Log.w("BackupScreen", "Failed to load shared backup file", e)
            }
        }
    }

    // SAF launcher for saving backup to a file
    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            val bytes = viewModel.lastBackupBytes ?: return@let
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(bytes)
            }
            viewModel.onBackupSaved()
        }
    }

    // SAF launcher for loading backup from a file
    val loadBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { input ->
                input.readBytesLimited()
            } ?: return@let
            viewModel.onBackupFileLoaded(bytes)
        }
    }

    val passphrasesMatch = passphrase == confirmPassphrase && passphrase.length >= 8
    val canCreate = passphrasesMatch && state !is BackupState.Creating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
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
                onClick = {
                    scope.launch {
                        if (activity == null || viewModel.requireBiometricForBackup(activity)) {
                            viewModel.createBackup(passphrase)
                        }
                    }
                },
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
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val date = java.time.LocalDate.now().toString()
                                saveBackupLauncher.launch("ssdid-backup-$date.enc")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Success)
                        ) {
                            Text("Save to File", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val bytes = viewModel.lastBackupBytes ?: return@Button
                                val cacheDir = File(context.cacheDir, "backups")
                                cacheDir.mkdirs()
                                val date = java.time.LocalDate.now().toString()
                                val backupFile = File(cacheDir, "ssdid-backup-$date.enc")
                                try {
                                    backupFile.writeBytes(bytes)
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        backupFile
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        setPackage("my.ssdid.drive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(sendIntent)
                                } catch (_: android.content.ActivityNotFoundException) {
                                    backupFile.delete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(stringResource(R.string.backup_to_cloud), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
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
                onClick = {
                    scope.launch {
                        if (activity == null || viewModel.requireBiometricForRestore(activity)) {
                            loadBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        }
                    }
                },
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
            Spacer(Modifier.height(16.dp))

            // Restore passphrase input (shown after file is loaded)
            if (loadedFileBytes != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Backup file loaded",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Size: ${formatBytes(loadedFileBytes!!.size)}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = restorePassphrase,
                            onValueChange = { restorePassphrase = it },
                            label = { Text("Restore Passphrase", color = TextTertiary) },
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
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.restoreBackup(restorePassphrase) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = restorePassphrase.length >= 8 && state !is BackupState.Restoring,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text("Restore", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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

private const val MAX_BACKUP_FILE_SIZE = 10L * 1024 * 1024 // 10 MB

private fun java.io.InputStream.readBytesLimited(maxBytes: Long = MAX_BACKUP_FILE_SIZE): ByteArray? {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var totalRead = 0L
    var bytesRead: Int
    while (read(chunk).also { bytesRead = it } != -1) {
        totalRead += bytesRead
        if (totalRead > maxBytes) return null
        buffer.write(chunk, 0, bytesRead)
    }
    return buffer.toByteArray()
}
