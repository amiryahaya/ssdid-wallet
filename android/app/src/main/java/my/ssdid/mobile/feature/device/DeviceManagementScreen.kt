package my.ssdid.mobile.feature.device

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class DeviceManagementViewModel @Inject constructor(
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val keyId: String = savedStateHandle["keyId"] ?: ""
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity = _identity.asStateFlow()

    init {
        viewModelScope.launch { _identity.value = vault.getIdentity(keyId) }
    }
}

@Composable
fun DeviceManagementScreen(
    onBack: () -> Unit,
    viewModel: DeviceManagementViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsState()

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
            Text("Devices", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // This Device
            item {
                Text("THIS DEVICE", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Accent)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Build.MODEL,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SuccessDim)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "Primary",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Success
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        identity?.let { id ->
                            Text("KEY ID", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.keyId,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("ALGORITHM", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.algorithm.name.replace("_", "-"),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("ENROLLED", fontSize = 11.sp, color = TextTertiary)
                            Text(
                                id.createdAt.take(10),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Other Devices
            item {
                Spacer(Modifier.height(8.dp))
                Text("OTHER DEVICES", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No other devices enrolled", color = TextSecondary, fontSize = 14.sp)
                }
            }

            // Enroll button
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = BgCard
                    )
                ) {
                    Text(
                        "\uD83D\uDCF1  Enroll New Device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Coming Soon — requires backend multi-device protocol",
                    fontSize = 11.sp,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Info note
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentDim)
                ) {
                    Text(
                        "Only the primary device can modify the DID Document. Secondary devices can authenticate but not manage the identity.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        color = Accent,
                        lineHeight = 18.sp
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
