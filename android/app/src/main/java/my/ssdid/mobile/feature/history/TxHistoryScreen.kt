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
import my.ssdid.mobile.ui.theme.*

private enum class ActivityType(val icon: String, val color: androidx.compose.ui.graphics.Color, val dimColor: androidx.compose.ui.graphics.Color) {
    IDENTITY_CREATED("\u2B21", Accent, AccentDim),
    REGISTERED("\uD83D\uDD17", Success, SuccessDim),
    AUTHENTICATED("\u2713", Classical, ClassicalDim),
    TRANSACTION_SIGNED("\u270E", Warning, WarningDim)
}

private data class ActivityItem(
    val type: ActivityType,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val date: String
)

private val placeholderData = listOf(
    ActivityItem(ActivityType.IDENTITY_CREATED, "Identity Created", "Personal (KAZ-Sign 192)", "14:32", "Today"),
    ActivityItem(ActivityType.REGISTERED, "Registered with Service", "demo.ssdid.my", "14:28", "Today"),
    ActivityItem(ActivityType.AUTHENTICATED, "Authenticated", "portal.university.edu.my", "11:05", "Today"),
    ActivityItem(ActivityType.TRANSACTION_SIGNED, "Transaction Signed", "1,500.00 MYR - Payment", "09:45", "Today"),
    ActivityItem(ActivityType.IDENTITY_CREATED, "Identity Created", "Work (Ed25519)", "16:20", "Yesterday"),
    ActivityItem(ActivityType.REGISTERED, "Registered with Service", "bank.example.com", "15:10", "Yesterday"),
    ActivityItem(ActivityType.AUTHENTICATED, "Authenticated", "gov.my", "10:30", "2 days ago"),
    ActivityItem(ActivityType.TRANSACTION_SIGNED, "Transaction Signed", "250.00 MYR - Transfer", "09:15", "2 days ago")
)

@Composable
fun TxHistoryScreen(onBack: () -> Unit) {
    val groupedItems = placeholderData.groupBy { it.date }

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

        if (placeholderData.isEmpty()) {
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
