package my.ssdid.wallet.feature.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PresentationRequestScreen(
    onBack: () -> Unit = {},
    onComplete: () -> Unit = {},
    viewModel: PresentationRequestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is PresentationRequestViewModel.UiState.Success) onComplete()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is PresentationRequestViewModel.UiState.Loading -> {
                CircularProgressIndicator()
            }
            is PresentationRequestViewModel.UiState.CredentialMatch -> {
                Text("Verifier: ${s.verifierName}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text("Requested claims:", style = MaterialTheme.typography.bodyLarge)
                s.claims.forEach { claim ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Checkbox(
                            checked = claim.selected,
                            onCheckedChange = {
                                if (!claim.required) viewModel.toggleClaim(claim.name)
                            },
                            enabled = !claim.required
                        )
                        Column {
                            Text(claim.name)
                            Text(claim.value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.decline(); onBack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = { viewModel.approve() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share")
                    }
                }
            }
            is PresentationRequestViewModel.UiState.Submitting -> {
                CircularProgressIndicator()
                Text("Submitting...")
            }
            is PresentationRequestViewModel.UiState.Success -> {
                Text("Presentation submitted successfully")
            }
            is PresentationRequestViewModel.UiState.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
