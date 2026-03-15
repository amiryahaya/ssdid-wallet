package my.ssdid.wallet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import my.ssdid.wallet.platform.i18n.LocalizationManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import my.ssdid.wallet.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBackupExport: () -> Unit = {},
    onProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val autoLockMinutes by viewModel.autoLockMinutes.collectAsState()
    val defaultAlgorithm by viewModel.defaultAlgorithm.collectAsState()
    val language by viewModel.language.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    val languageDisplay = LocalizationManager.localeNames[language] ?: "English"

    val algorithmDisplay = defaultAlgorithm
        .replace("_", "-")
        .replace("KAZ-SIGN", "KAZ-Sign")
        .replace("ED25519", "Ed25519")
        .replace("ECDSA-P256", "ECDSA P-256")
        .replace("ECDSA-P384", "ECDSA P-384")

    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("ACCOUNT", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Profile", "Name, email", onClick = onProfile) }
            item { Spacer(Modifier.height(16.dp)) }
            item { Text("SECURITY", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item {
                SettingsItem(
                    "Biometric Authentication",
                    "Face ID / Fingerprint",
                    toggle = true,
                    checked = biometricEnabled,
                    onToggle = { viewModel.setBiometricEnabled(it) }
                )
            }
            item { SettingsItem("Auto-Lock", "After $autoLockMinutes minutes") }
            item { SettingsItem("Change Password", "Update vault password") }
            item { SettingsItem("Backup & Export", "Encrypted backup of all identities", onClick = onBackupExport) }

            item { Spacer(Modifier.height(16.dp)); Text("NETWORK", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Registry URL", "registry.ssdid.my") }

            item { Spacer(Modifier.height(16.dp)); Text("PREFERENCES", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Appearance", "Dark") }
            item { SettingsItem("Language", languageDisplay, onClick = { showLanguageDialog = true }) }
            item { SettingsItem("Default Algorithm", algorithmDisplay) }

            item { Spacer(Modifier.height(16.dp)); Text("ABOUT", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Version", "1.0.0 (Build 1)") }
            item { SettingsItem("W3C DID 1.1", "Compliant") }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column {
                    LocalizationManager.supportedLocales.forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(tag)
                                    LocalizationManager.setLocale(tag)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = language == tag,
                                onClick = {
                                    viewModel.setLanguage(tag)
                                    LocalizationManager.setLocale(tag)
                                    showLanguageDialog = false
                                }
                            )
                            Text(
                                LocalizationManager.localeNames[tag] ?: tag,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    toggle: Boolean = false,
    checked: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, fontSize = 12.sp, color = TextTertiary)
            }
            if (toggle) {
                Switch(
                    checked = checked,
                    onCheckedChange = { onToggle?.invoke(it) },
                    modifier = Modifier.semantics {
                        contentDescription = "$title, ${if (checked) "enabled" else "disabled"}"
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextTertiary)
            }
        }
    }
}
