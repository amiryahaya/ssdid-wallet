package my.ssdid.wallet.platform.notify

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import my.ssdid.wallet.SsdidApp
import my.ssdid.wallet.domain.notify.NotifyManager
import my.ssdid.sdk.domain.vault.Vault

/**
 * Triggers pending notification fetch whenever the app enters the foreground.
 * Registered with ProcessLifecycleOwner in SsdidApp.
 */
class NotifyLifecycleObserver(
    private val application: Application,
    private val notifyManager: NotifyManager,
    private val vault: Vault
) : DefaultLifecycleObserver {

    private var activeJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        val scope = (application as? SsdidApp)?.appScope ?: return
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                notifyManager.updateKnownIdentities(vault.listIdentities())
                notifyManager.fetchAndDemux()
            } catch (_: Exception) {
                // Non-fatal; notifications are supplemental.
            }
        }
    }
}
