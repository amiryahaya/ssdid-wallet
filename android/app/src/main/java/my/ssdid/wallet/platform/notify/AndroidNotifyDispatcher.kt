package my.ssdid.wallet.platform.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import my.ssdid.wallet.R
import my.ssdid.sdk.domain.notify.NotifyDispatcher
import my.ssdid.sdk.domain.transport.dto.PendingNotification

/**
 * Dispatches demultiplexed notifications as Android system notifications.
 * Each notification is tagged with its identity name for user context.
 */
class AndroidNotifyDispatcher(
    private val context: Context
) : NotifyDispatcher {

    init {
        createChannel()
    }

    override suspend fun dispatch(identityName: String?, notification: PendingNotification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = if (identityName != null) "SSDID — $identityName" else "SSDID"
        val body = notification.payload

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(
                if (notification.priority == "high") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        // Use notification_id hash as Android notification ID for dedup
        manager.notify(notification.notificationId.hashCode(), builder.build())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSDID Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from SSDID services"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ssdid_notify"
    }
}
