package my.ssdid.wallet.platform.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import javax.inject.Inject

class BundleSyncWorkerFactory @Inject constructor(
    private val bundleManager: BundleManager,
    private val credentialRepository: CredentialRepository
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            BundleSyncWorker::class.java.name -> BundleSyncWorker(
                appContext, workerParameters, bundleManager, credentialRepository
            )
            else -> null
        }
    }
}
