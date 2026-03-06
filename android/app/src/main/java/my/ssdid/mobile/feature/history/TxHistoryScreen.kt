package my.ssdid.mobile.feature.history

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.ssdid.mobile.ui.theme.*
import javax.inject.Inject

enum class ActivityType(val icon: String, val color: androidx.compose.ui.graphics.Color, val dimColor: androidx.compose.ui.graphics.Color) {
    IDENTITY_CREATED("\u2B21", Accent, AccentDim),
    REGISTERED("\uD83D\uDD17", Success, SuccessDim),
    AUTHENTICATED("\u2713", Classical, ClassicalDim),
    TRANSACTION_SIGNED("\u270E", Warning, WarningDim)
}

data class ActivityItem(
    val type: ActivityType,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val date: String
)

@HiltViewModel
class TxHistoryViewModel @Inject constructor() : ViewModel() {
    // TODO: Populate from real activity log once persistence is implemented
    private val _activities = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activities = _activities.asStateFlow()

    fun addActivity(activity: ActivityItem) {
        _activities.value = listOf(activity) + _activities.value
    }
}

@Composable
fun TxHistoryScreen(
    onBack: () -> Unit,
    viewModel: TxHistoryViewModel = hiltViewModel()
) {
    val activities by viewModel.activities.collectAsState()
    val groupedItems = activities.groupBy { it.date }

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
                    items(items) { activity ->
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
                                        .background(activity.type.dimColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(activity.type.icon, fontSize = 16.sp, color = activity.type.color)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        activity.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        activity.subtitle,
                                        fontSize = 12.sp,
                                        color = TextTertiary
                                    )
                                }
                                Text(
                                    activity.timestamp,
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
