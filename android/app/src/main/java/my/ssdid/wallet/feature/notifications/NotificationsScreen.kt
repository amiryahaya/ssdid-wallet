package my.ssdid.wallet.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.notify.LocalNotification
import my.ssdid.wallet.domain.notify.LocalNotificationStorage
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val storage: LocalNotificationStorage
) : ViewModel() {

    val notifications = storage.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAsRead(id: String) {
        viewModelScope.launch { storage.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { storage.markAllAsRead() }
    }

    fun delete(id: String) {
        viewModelScope.launch { storage.delete(id) }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val sorted = notifications.sortedByDescending { it.receivedAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (notifications.any { !it.isRead }) {
                TextButton(onClick = { viewModel.markAllAsRead() }) {
                    Text("Mark All Read", color = Accent, fontSize = 13.sp)
                }
            }
        }

        if (sorted.isEmpty()) {
            // Empty state
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
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Notifications from services will appear here",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sorted, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onTap = { viewModel.markAsRead(notification.id) },
                        onDelete = { viewModel.delete(notification.id) }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: LocalNotification,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Danger)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Delete", color = BgPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Unread dot
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (!notification.isRead) Accent else BgCard)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (notification.identityName != null) {
                        Text(
                            notification.identityName,
                            fontSize = 12.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        notification.payload,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 3
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    relativeTime(notification.receivedAt),
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

private fun relativeTime(isoTimestamp: String): String {
    if (isoTimestamp.isBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(isoTimestamp)
        val now = java.time.Instant.now()
        val minutes = java.time.Duration.between(instant, now).toMinutes()
        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 2880 -> "Yesterday"
            else -> "${minutes / 1440}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}
