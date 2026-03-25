package my.ssdid.wallet.domain.verifier.offline.sync

interface BundleSyncScheduler {
    fun schedulePeriodicSync(intervalHours: Long = 12)
    fun scheduleOnConnectivityRestore()
    fun cancelAll()
}
