package my.ssdid.wallet.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import my.ssdid.wallet.platform.biometric.BiometricState
import my.ssdid.wallet.ui.theme.BgPrimary
import my.ssdid.wallet.ui.theme.TextSecondary
import my.ssdid.wallet.ui.theme.TextTertiary

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val context = LocalContext.current
    val biometricAuth = remember { BiometricAuthenticator() }
    val biometricState = remember { biometricAuth.getBiometricState(activity) }
    val prefs = remember { context.getSharedPreferences("ssdid_lock_prefs", Context.MODE_PRIVATE) }
    var showWarning by remember { mutableStateOf(false) }
    var authAttempt by remember { mutableIntStateOf(0) }

    // Show one-time warning for no-hardware state
    LaunchedEffect(biometricState) {
        if (biometricState == BiometricState.NO_HARDWARE) {
            val shown = prefs.getBoolean("ssdid_biometric_warning_shown", false)
            if (!shown) {
                showWarning = true
                prefs.edit().putBoolean("ssdid_biometric_warning_shown", true).apply()
            }
        }
    }

    // Authenticate on mount and on retry
    LaunchedEffect(authAttempt) {
        val result = when (biometricState) {
            BiometricState.AVAILABLE ->
                biometricAuth.authenticate(activity)
            else ->
                biometricAuth.authenticateWithFallback(activity)
        }
        if (result is BiometricResult.Success) {
            onUnlock()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "SSDID Wallet",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Authenticate to unlock",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))

            if (showWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Your device doesn't support biometric authentication. " +
                            "Device passcode is being used instead. " +
                            "For stronger security, use a device with Face ID or fingerprint.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (biometricState == BiometricState.NOT_ENROLLED) {
                Text(
                    text = "Biometric not enrolled. Please set up fingerprint or face unlock in your device Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(onClick = { authAttempt++ }) {
                Text("Unlock")
            }
        }
    }
}
