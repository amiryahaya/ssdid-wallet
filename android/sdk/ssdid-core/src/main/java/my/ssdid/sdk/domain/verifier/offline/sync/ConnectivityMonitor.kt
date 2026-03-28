package my.ssdid.sdk.domain.verifier.offline.sync

import kotlinx.coroutines.flow.Flow

interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
    fun isCurrentlyOnline(): Boolean
}
