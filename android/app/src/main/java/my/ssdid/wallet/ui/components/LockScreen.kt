package my.ssdid.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import my.ssdid.wallet.ui.theme.BgPrimary
import my.ssdid.wallet.ui.theme.TextSecondary

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val biometricAuth = remember { BiometricAuthenticator() }
    var authAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(authAttempt) {
        when (biometricAuth.authenticate(activity)) {
            is BiometricResult.Success -> onUnlock()
            is BiometricResult.Cancelled -> { /* stay locked */ }
            is BiometricResult.Error -> { /* stay locked, user can retry */ }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Button(onClick = { authAttempt++ }) {
                Text("Unlock")
            }
        }
    }
}
