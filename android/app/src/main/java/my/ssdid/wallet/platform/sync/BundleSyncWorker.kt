package my.ssdid.wallet.platform.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import my.ssdid.wallet.domain.verifier.offline.BundleManager
import my.ssdid.wallet.domain.verifier.offline.CredentialRepository

/**
 * WorkManager worker that pre-fetches verification bundles for all held credentials.
 *
 * Dependency injection is performed via [BundleSyncWorkerFactory] (a [androidx.work.WorkerFactory]
 * sub-class wired into the Hilt [dagger.hilt.android.HiltAndroidApp] application in the DI module)
 * rather than with [androidx.hilt.work.HiltWorker] / [dagger.assisted.AssistedInject], which
 * currently trigger a KAPT bug with Kotlin 2.2 metadata under hilt-work 1.2.0.
 */
class BundleSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val bundleManager: BundleManager,
    private val credentialRepository: CredentialRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val issuerDids = credentialRepository.getUniqueIssuerDids()
            for (did in issuerDids) {
                if (!bundleManager.hasFreshBundle(did)) {
                    bundleManager.prefetchBundle(did)
                }
            }
            Result.success()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
