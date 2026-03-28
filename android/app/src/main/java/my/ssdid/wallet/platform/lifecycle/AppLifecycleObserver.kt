package my.ssdid.wallet.platform.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.BundleStore

class AppLifecycleObserver(
    private val bundleStore: BundleStore,
    private val bundleManager: BundleManager,
    private val ttlProvider: TtlProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : DefaultLifecycleObserver {

    private var refreshJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                val bundles = bundleStore.listBundles()
                val needsRefresh = bundles.any { ttlProvider.freshnessRatio(it.fetchedAt) > 0.8 }
                if (needsRefresh) {
                    bundleManager.refreshStaleBundles()
                }
            } catch (e: Exception) {
                // Silent failure — stale bundles are acceptable
            }
        }
    }
}
