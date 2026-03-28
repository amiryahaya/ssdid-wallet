package my.ssdid.sdk.domain.verifier.offline.sync

interface BundleSyncScheduler {
    fun schedulePeriodicSync(intervalHours: Long = 12)
    fun scheduleOnConnectivityRestore()
    fun cancelAll()
}
