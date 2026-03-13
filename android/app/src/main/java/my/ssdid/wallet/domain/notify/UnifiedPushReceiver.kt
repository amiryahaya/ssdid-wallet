package my.ssdid.wallet.domain.notify

import android.content.Context
import android.util.Log
import kotlinx.coroutines.launch
import my.ssdid.wallet.SsdidApp
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * Receives UnifiedPush events: new endpoint registration and incoming messages.
 *
 * When a UnifiedPush distributor grants this app an endpoint URL, we forward
 * that endpoint to the SSDID Notify service as a device token (platform = "unifiedpush").
 * Incoming push messages trigger pending-notification fetch + demux.
 */
class UnifiedPushReceiver : MessagingReceiver() {

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Log.d(TAG, "UnifiedPush endpoint received")
        val app = context.applicationContext as? SsdidApp ?: return
        app.appScope.launch {
            try {
                app.notifyManager.updateDeviceToken("unifiedpush", endpoint)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register UnifiedPush endpoint", e)
            }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.w(TAG, "UnifiedPush registration failed")
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.d(TAG, "UnifiedPush unregistered")
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        // Push payload is a wake-up signal; fetch and demux pending notifications.
        val app = context.applicationContext as? SsdidApp ?: return
        app.appScope.launch {
            try {
                app.notifyManager.fetchAndDemux()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process push message", e)
            }
        }
    }

    companion object {
        private const val TAG = "UnifiedPushReceiver"
    }
}
