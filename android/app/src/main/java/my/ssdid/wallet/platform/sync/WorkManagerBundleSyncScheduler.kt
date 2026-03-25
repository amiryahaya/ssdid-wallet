package my.ssdid.wallet.platform.sync

import android.content.Context
import androidx.work.*
import my.ssdid.wallet.domain.verifier.offline.sync.BundleSyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerBundleSyncScheduler(private val context: Context) : BundleSyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedulePeriodicSync(intervalHours: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BundleSyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork("bundle_periodic_sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleOnConnectivityRestore() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BundleSyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork("bundle_connectivity_sync", ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork("bundle_periodic_sync")
        workManager.cancelUniqueWork("bundle_connectivity_sync")
    }
}
