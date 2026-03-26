package my.ssdid.wallet.feature.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.biometric.BiometricResult
import my.ssdid.wallet.platform.biometric.BiometricState
import my.ssdid.wallet.ui.theme.*

@Composable
fun BiometricSetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val biometricAuth = remember { BiometricAuthenticator() }
    var biometricState by remember { mutableStateOf(biometricAuth.getBiometricState(activity)) }
    var canAuthWithFallback by remember { mutableStateOf(biometricAuth.canAuthenticateWithFallback(activity)) }

    // Refresh biometric state when the user returns from Settings (e.g. after enrolling biometrics)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricState = biometricAuth.getBiometricState(activity)
                canAuthWithFallback = biometricAuth.canAuthenticateWithFallback(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        // Biometric icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", modifier = Modifier.size(52.dp), tint = Accent)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            "Biometric Authentication",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(12.dp))

        val bodyText = when (biometricState) {
            BiometricState.AVAILABLE ->
                "Secure your wallet with Face ID or fingerprint.\nThis adds an extra layer of protection\nfor signing transactions and accessing keys."
            BiometricState.NOT_ENROLLED ->
                "Your device has biometric hardware but no biometrics are enrolled.\nPlease set up fingerprint or face unlock in your device Settings, or use your device passcode."
            BiometricState.NO_HARDWARE ->
                "Your device does not support biometric authentication.\nYour device passcode will be used to protect the wallet."
        }

        Text(
            bodyText,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Security info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Success)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Biometric data never leaves your device", fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Success)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Keys remain in hardware-backed keystore", fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Success)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Required for transaction signing", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (biometricState) {
            BiometricState.AVAILABLE -> {
                // Primary action: enable biometric
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(
                        "Enable Biometric Authentication",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            BiometricState.NOT_ENROLLED -> {
                // Guide user to enroll, offer passcode as interim
                Text(
                    "Go to Settings > Security to enroll biometrics for the strongest protection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 12.dp)
                )
                Button(
                    onClick = { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(
                        "Open Security Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (canAuthWithFallback) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BgCard)
                    ) {
                        Text(
                            "Use Device Passcode for Now",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
            BiometricState.NO_HARDWARE -> {
                if (canAuthWithFallback) {
                    // Device passcode is available — allow proceeding
                    Text(
                        "Authentication is mandatory. Your device passcode will protect this wallet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(
                            "Continue with Device Passcode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    // No passcode set — must go to Settings
                    Text(
                        "No device passcode is set. Please set up a device passcode in Settings > Security to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(
                            "Open Settings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
