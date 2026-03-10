package my.ssdid.wallet.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ConsentScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCreateIdentity: (String) -> Unit = {},
) {
    // Stub — will be replaced in Task 8
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Consent Screen (stub)")
    }
}
