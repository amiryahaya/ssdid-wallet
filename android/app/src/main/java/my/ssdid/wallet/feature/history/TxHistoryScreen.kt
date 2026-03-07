package my.ssdid.wallet.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.model.ActivityRecord
import my.ssdid.wallet.domain.model.ActivityType
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

private data class ActivityStyle(val icon: String, val color: Color, val dimColor: Color)

private fun ActivityType.style(): ActivityStyle = when (this) {
    ActivityType.IDENTITY_CREATED -> ActivityStyle("\u2B21", Accent, AccentDim)
    ActivityType.SERVICE_REGISTERED -> ActivityStyle("\uD83D\uDD17", Success, SuccessDim)
    ActivityType.AUTHENTICATED -> ActivityStyle("\u2713", Classical, ClassicalDim)
    ActivityType.TX_SIGNED -> ActivityStyle("\u270E", Warning, WarningDim)
    ActivityType.KEY_ROTATED -> ActivityStyle("\uD83D\uDD04", Pqc, PqcDim)
    ActivityType.CREDENTIAL_RECEIVED -> ActivityStyle("\uD83D\uDCC4", Success, SuccessDim)
    ActivityType.BACKUP_CREATED -> ActivityStyle("\uD83D\uDCBE", Accent, AccentDim)
    else -> ActivityStyle("\u2022", TextSecondary, BgCard)
}

@HiltViewModel
class TxHistoryViewModel @Inject constructor(
    private val activityRepository: ActivityRepository
) : ViewModel() {
    private val _activities = MutableStateFlow<List<ActivityRecord>>(emptyList())
    val activities = _activities.asStateFlow()

    init {
        viewModelScope.launch {
            _activities.value = activityRepository.listActivities()
        }
    }
}

@Composable
fun TxHistoryScreen(
    onBack: () -> Unit,
    viewModel: TxHistoryViewModel = hiltViewModel()
) {
    val activities by viewModel.activities.collectAsState()
    val groupedItems = activities.groupBy { it.timestamp.take(10) }

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
            Text("Activity History", style = MaterialTheme.typography.titleLarge)
        }

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\uD83D\uDCCB", fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No activity yet", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Your identity and transaction history will appear here.", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedItems.forEach { (date, items) ->
                    item {
                        Text(
                            date.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    items(items) { record ->
                        val style = record.type.style()
                        val title = record.type.name
                            .replace("_", " ")
                            .lowercase()
                            .replaceFirstChar { it.uppercase() }
                        val subtitle = record.serviceUrl ?: record.did
                        val time = if (record.timestamp.length > 11) {
                            record.timestamp.substring(11).take(5)
                        } else {
                            ""
                        }

                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Type icon
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(style.dimColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(style.icon, fontSize = 16.sp, color = style.color)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        subtitle,
                                        fontSize = 12.sp,
                                        color = TextTertiary
                                    )
                                }
                                Text(
                                    time,
                                    fontSize = 12.sp,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }

                // Bottom spacer
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
