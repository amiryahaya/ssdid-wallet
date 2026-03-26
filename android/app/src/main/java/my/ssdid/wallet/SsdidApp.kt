package my.ssdid.wallet

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.Configuration
import androidx.work.WorkManager
import my.ssdid.wallet.domain.notify.NotifyLifecycleObserver
import my.ssdid.wallet.domain.notify.NotifyManager
import my.ssdid.wallet.domain.profile.ProfileMigration
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.platform.storage.DataStoreSettingsRepository
import my.ssdid.wallet.domain.verifier.offline.BundleManager
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import my.ssdid.wallet.domain.verifier.offline.sync.BundleSyncScheduler
import my.ssdid.wallet.domain.verifier.offline.sync.ConnectivityMonitor
import my.ssdid.wallet.platform.lifecycle.AppLifecycleObserver
import my.ssdid.wallet.platform.sync.BundleSyncWorkerFactory
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

@HiltAndroidApp
class SsdidApp : Application() {

    // Process-scoped coroutine context — not cancelled explicitly; OS reclaims on process death.
    // Tests should not instantiate SsdidApp directly.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var notifyManager: NotifyManager
    @Inject lateinit var notifyLifecycleObserver: NotifyLifecycleObserver
    @Inject lateinit var profileMigration: ProfileMigration
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var syncScheduler: BundleSyncScheduler
    @Inject lateinit var bundleStore: BundleStore
    @Inject lateinit var bundleManager: BundleManager
    @Inject lateinit var ttlProvider: TtlProvider
    @Inject lateinit var bundleSyncWorkerFactory: BundleSyncWorkerFactory
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()
        initSentry()

        val config = Configuration.Builder()
            .setWorkerFactory(bundleSyncWorkerFactory)
            .build()
        WorkManager.initialize(this, config)

        syncScheduler.schedulePeriodicSync(intervalHours = 12)

        // Fetch pending notifications whenever the app enters the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(notifyLifecycleObserver)

        // Trigger a foreground sync check whenever the app comes to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(bundleStore, bundleManager, ttlProvider)
        )

        appScope.launch {
            profileMigration.migrateIfNeeded()
        }

        (settingsRepository as? DataStoreSettingsRepository)?.let { repo ->
            appScope.launch {
                repo.migrateToMandatoryBiometricIfNeeded()
            }
        }

        appScope.launch {
            notifyManager.ensureInboxRegistered()
        }

        // Trigger a bundle sync whenever connectivity is restored from offline state.
        appScope.launch {
            var wasOnline = connectivityMonitor.isCurrentlyOnline()
            connectivityMonitor.isOnline.collect { isOnline ->
                if (!wasOnline && isOnline) {
                    syncScheduler.scheduleOnConnectivityRestore()
                }
                wasOnline = isOnline
            }
        }

        // Register with a UnifiedPush distributor if one is available.
        UnifiedPush.registerApp(this)
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return

        val didPattern = Regex("did:[a-z]+:[A-Za-z0-9_\\-]+")

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.environment = if (BuildConfig.DEBUG) "development" else BuildConfig.SENTRY_ENVIRONMENT
            options.isEnableNdk = true
            options.isAnrEnabled = true
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
            options.isDebug = BuildConfig.DEBUG

            options.setBeforeSend { event, _ ->
                event.user?.let { user ->
                    user.email = null
                    user.ipAddress = null
                    user.name = null
                }
                event.exceptions?.forEach { ex ->
                    ex.value = ex.value?.replace(didPattern, "[REDACTED]")
                }
                event
            }

            options.setBeforeBreadcrumb { breadcrumb, _ ->
                val sensitivePatterns = listOf(
                    "session_token", "private_key", "secret",
                    "password", "mnemonic", "seed_phrase", "did:"
                )
                val msg = breadcrumb.message ?: ""
                if (sensitivePatterns.any { msg.contains(it, ignoreCase = true) }) {
                    return@setBeforeBreadcrumb null
                }
                breadcrumb.data.keys.toList().forEach { key ->
                    val value = breadcrumb.data[key]?.toString() ?: return@forEach
                    if (sensitivePatterns.any { value.contains(it, ignoreCase = true) }) {
                        breadcrumb.data.remove(key)
                    }
                }
                breadcrumb.data["url"]?.let { url ->
                    breadcrumb.data["url"] = url.toString().substringBefore("?")
                }
                breadcrumb
            }

            options.addInAppInclude("my.ssdid.wallet")
        }
    }
}
