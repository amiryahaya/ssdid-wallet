package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.revocation.HttpStatusListFetcher
import my.ssdid.sdk.domain.revocation.StatusListFetcher
import my.ssdid.sdk.domain.settings.SettingsRepository
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.verifier.Verifier
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.BundleStore
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import my.ssdid.sdk.domain.verifier.offline.OfflineVerifier
import my.ssdid.sdk.domain.verifier.offline.VerificationOrchestrator
import my.ssdid.sdk.domain.verifier.offline.sync.BundleSyncScheduler
import my.ssdid.sdk.domain.verifier.offline.sync.ConnectivityMonitor
import my.ssdid.sdk.platform.storage.DataStoreBundleStore
import my.ssdid.sdk.platform.storage.DataStoreCredentialRepository
import my.ssdid.sdk.platform.sync.AndroidConnectivityMonitor
import my.ssdid.sdk.platform.sync.BundleSyncWorkerFactory
import my.ssdid.sdk.platform.sync.WorkManagerBundleSyncScheduler
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {

    @Provides
    @Singleton
    fun provideStatusListFetcher(okHttpClient: OkHttpClient): StatusListFetcher {
        val json = Json { ignoreUnknownKeys = true }
        return HttpStatusListFetcher(okHttpClient, json)
    }

    @Provides
    @Singleton
    fun provideTtlProvider(settings: SettingsRepository): TtlProvider = TtlProvider(settings)

    @Provides
    @Singleton
    fun provideBundleStore(@ApplicationContext context: Context): BundleStore =
        DataStoreBundleStore(context)

    @Provides
    @Singleton
    fun provideBundleManager(
        verifier: Verifier,
        statusListFetcher: StatusListFetcher,
        bundleStore: BundleStore,
        ttlProvider: TtlProvider
    ): BundleManager = BundleManager(verifier, statusListFetcher, bundleStore, ttlProvider)

    @Provides
    @Singleton
    fun provideOfflineVerifier(
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        bundleStore: BundleStore,
        ttlProvider: TtlProvider
    ): OfflineVerifier = OfflineVerifier(classical, pqc, bundleStore, ttlProvider)

    @Provides
    @Singleton
    fun provideVerificationOrchestrator(
        verifier: Verifier,
        offlineVerifier: OfflineVerifier,
        bundleStore: BundleStore
    ): VerificationOrchestrator =
        VerificationOrchestrator(verifier, offlineVerifier, bundleStore)

    @Provides
    @Singleton
    fun provideCredentialRepository(
        @ApplicationContext context: Context
    ): CredentialRepository = DataStoreCredentialRepository(context)

    @Provides
    @Singleton
    fun provideBundleSyncWorkerFactory(
        bundleManager: BundleManager,
        credentialRepository: CredentialRepository
    ): BundleSyncWorkerFactory = BundleSyncWorkerFactory(bundleManager, credentialRepository)

    @Provides
    @Singleton
    fun provideConnectivityMonitor(
        @ApplicationContext context: Context
    ): ConnectivityMonitor = AndroidConnectivityMonitor(context)

    @Provides
    @Singleton
    fun provideBundleSyncScheduler(
        @ApplicationContext context: Context
    ): BundleSyncScheduler = WorkManagerBundleSyncScheduler(context)
}
