package my.ssdid.mobile.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.mobile.domain.SsdidClient
import my.ssdid.mobile.domain.model.Algorithm
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class CreateIdentityViewModel @Inject constructor(private val client: SsdidClient) : ViewModel() {
    private val _isCreating = MutableStateFlow(false)
    val isCreating = _isCreating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun createIdentity(name: String, algorithm: Algorithm, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null
            client.initIdentity(name, algorithm)
                .onSuccess { onSuccess() }
                .onFailure { _error.value = it.message ?: "Failed to create identity" }
            _isCreating.value = false
        }
    }
}

@Composable
fun CreateIdentityScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateIdentityViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var selectedAlgo by remember { mutableStateOf(Algorithm.KAZ_SIGN_192) }
    val isCreating by viewModel.isCreating.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp)) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Create Identity", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("IDENTITY NAME", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Personal, Work", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }

            item {
                Text("SIGNATURE ALGORITHM", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            // Algorithm options
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Algorithm.entries.forEach { algo ->
                        val isSelected = selectedAlgo == algo
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentDim else BgCard
                            ),
                            onClick = { selectedAlgo = algo }
                        ) {
                            Row(Modifier.padding(14.dp)) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedAlgo = algo },
                                    colors = RadioButtonDefaults.colors(selectedColor = Accent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(algo.name.replace("_", " "), style = MaterialTheme.typography.titleMedium)
                                    Text(algo.w3cType, fontSize = 11.sp, color = TextTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error display
        error?.let { msg ->
            Text(
                msg,
                color = Danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Footer
        Button(
            onClick = { if (name.isNotBlank()) viewModel.createIdentity(name, selectedAlgo, onCreated) },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            enabled = name.isNotBlank() && !isCreating,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (isCreating) {
                CircularProgressIndicator(Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
            } else {
                Text("Create Identity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
