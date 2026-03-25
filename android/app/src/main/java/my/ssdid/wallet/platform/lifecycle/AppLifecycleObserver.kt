package my.ssdid.wallet.platform.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.verifier.offline.BundleManager
import my.ssdid.wallet.domain.verifier.offline.BundleStore

class AppLifecycleObserver(
    private val bundleStore: BundleStore,
    private val bundleManager: BundleManager,
    private val ttlProvider: TtlProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val bundles = bundleStore.listBundles()
            val needsRefresh = bundles.any { ttlProvider.freshnessRatio(it.fetchedAt) > 0.8 }
            if (needsRefresh) {
                bundleManager.refreshStaleBundles()
            }
        }
    }
}
