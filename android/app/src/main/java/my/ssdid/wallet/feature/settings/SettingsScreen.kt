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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.ssdid.wallet.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit, onBackupExport: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()) {
        Row(Modifier.padding(20.dp)) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("SECURITY", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Biometric Authentication", "Face ID / Fingerprint", toggle = true) }
            item { SettingsItem("Auto-Lock", "After 5 minutes") }
            item { SettingsItem("Change Password", "Update vault password") }
            item { SettingsItem("Backup & Export", "Encrypted backup of all identities", onClick = onBackupExport) }

            item { Spacer(Modifier.height(16.dp)); Text("NETWORK", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Registry URL", "registry.ssdid.my") }

            item { Spacer(Modifier.height(16.dp)); Text("PREFERENCES", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Appearance", "Dark") }
            item { SettingsItem("Language", "English") }
            item { SettingsItem("Default Algorithm", "KAZ-Sign 192") }

            item { Spacer(Modifier.height(16.dp)); Text("ABOUT", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)) }
            item { SettingsItem("Version", "1.0.0 (Build 1)") }
            item { SettingsItem("W3C DID 1.1", "Compliant") }
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, toggle: Boolean = false, onClick: (() -> Unit)? = null) {
    var isOn by remember { mutableStateOf(true) }
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
                Switch(checked = isOn, onCheckedChange = { isOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            } else {
                Text("\u203A", color = TextTertiary, fontSize = 18.sp)
            }
        }
    }
}
