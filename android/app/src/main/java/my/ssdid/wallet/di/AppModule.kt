package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.sdk.SsdidSdk
import my.ssdid.wallet.BuildConfig
import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.backup.BackupManager
import my.ssdid.sdk.domain.credential.CredentialIssuanceManager
import my.ssdid.sdk.domain.device.DeviceManager
import my.ssdid.sdk.domain.history.ActivityRepository
import my.ssdid.wallet.domain.crypto.PqcProvider
import my.ssdid.wallet.domain.profile.ProfileMigration
import my.ssdid.sdk.domain.recovery.RecoveryManager
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryManager
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.sdk.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.sdk.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.sdk.domain.revocation.RevocationManager
import my.ssdid.sdk.domain.rotation.KeyRotationManager
import my.ssdid.sdk.domain.settings.SettingsRepository
import my.ssdid.sdk.domain.transport.EmailVerifyApi
import my.ssdid.sdk.domain.transport.NotifyApi
import my.ssdid.sdk.domain.transport.RegistryApi
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.sdk.domain.vault.VaultStorage
import my.ssdid.sdk.domain.vault.KeystoreManager
import my.ssdid.sdk.domain.verifier.Verifier
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.BundleStore
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import my.ssdid.sdk.domain.verifier.offline.VerificationOrchestrator
import my.ssdid.sdk.domain.verifier.offline.sync.BundleSyncScheduler
import my.ssdid.sdk.domain.verifier.offline.sync.ConnectivityMonitor
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.notify.NotifyManager
import my.ssdid.sdk.domain.notify.NotifyStorage
import my.ssdid.sdk.domain.oid4vci.OpenId4VciHandler
import my.ssdid.sdk.domain.oid4vp.OpenId4VpHandler
import my.ssdid.sdk.platform.notify.LocalNotificationStorage
import my.ssdid.sdk.platform.sync.BundleSyncWorkerFactory
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.logging.SentryLogger
import my.ssdid.wallet.platform.notify.AndroidNotifyDispatcher
import my.ssdid.wallet.platform.notify.NotifyLifecycleObserver
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Simplified DI module that builds SsdidSdk once and provides individual
 * domain classes from it for backward-compatible ViewModel injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Core SDK instance ────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSsdidSdk(
        @ApplicationContext context: Context,
        notifyDispatcher: AndroidNotifyDispatcher
    ): SsdidSdk =
        SsdidSdk.builder(context)
            .registryUrl("https://registry.ssdid.my")
            .notifyUrl(BuildConfig.NOTIFY_URL)
            .logger(SentryLogger())
            .addCryptoProvider(PqcProvider())
            .notifyDispatcher(notifyDispatcher)
            .certificatePinning(
                enabled = !BuildConfig.DEBUG,
                pins = listOf(
                    "registry.ssdid.my" to listOf(
                        "sha256/6HndsMosiTeHfV+W29g33ZHsyuPe4Yo7fPdSCUWdeF0=",
                        "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
                    ),
                    "notify.ssdid.my" to listOf(
                        "sha256/6HndsMosiTeHfV+W29g33ZHsyuPe4Yo7fPdSCUWdeF0=",
                        "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
                    )
                )
            )
            .build()

    // ── Backward compatibility: provide domain classes from SDK ──────────

    @Provides @Singleton
    fun provideVault(sdk: SsdidSdk): Vault = sdk.internalVault

    @Provides @Singleton
    fun provideVerifier(sdk: SsdidSdk): Verifier = sdk.internalVerifier

    @Provides @Singleton
    fun provideSsdidClient(sdk: SsdidSdk): SsdidClient = sdk.internalClient

    @Provides @Singleton
    fun provideHttpClient(sdk: SsdidSdk): SsdidHttpClient = sdk.internalHttpClient

    @Provides @Singleton
    fun provideOkHttpClient(sdk: SsdidSdk): OkHttpClient = sdk.internalOkHttpClient

    @Provides @Singleton
    fun provideOid4VciHandler(sdk: SsdidSdk): OpenId4VciHandler = sdk.internalOid4VciHandler

    @Provides @Singleton
    fun provideOid4VpHandler(sdk: SsdidSdk): OpenId4VpHandler = sdk.internalOid4VpHandler

    @Provides @Singleton
    fun provideNotifyManager(sdk: SsdidSdk): NotifyManager = sdk.internalNotifyManager

    @Provides @Singleton
    fun provideRecoveryManager(sdk: SsdidSdk): RecoveryManager = sdk.internalRecoveryManager

    @Provides @Singleton
    fun provideKeyRotationManager(sdk: SsdidSdk): KeyRotationManager = sdk.internalKeyRotationManager

    @Provides @Singleton
    fun provideBackupManager(sdk: SsdidSdk): BackupManager = sdk.internalBackupManager

    @Provides @Singleton
    fun provideDeviceManager(sdk: SsdidSdk): DeviceManager = sdk.internalDeviceManager

    @Provides @Singleton
    fun provideRevocationManager(sdk: SsdidSdk): RevocationManager = sdk.internalRevocationManager

    @Provides @Singleton
    fun provideActivityRepository(sdk: SsdidSdk): ActivityRepository = sdk.internalActivityRepo

    @Provides @Singleton
    fun provideVaultStorage(sdk: SsdidSdk): VaultStorage = sdk.internalVaultStorage

    @Provides @Singleton
    fun provideKeystoreManager(sdk: SsdidSdk): KeystoreManager = sdk.internalKeystoreManager

    @Provides @Singleton
    fun provideSettingsRepository(sdk: SsdidSdk): SettingsRepository = sdk.internalSettingsRepository

    // ── Offline verification types (previously OfflineModule) ───────────

    @Provides @Singleton
    fun provideBundleStore(sdk: SsdidSdk): BundleStore = sdk.internalBundleStore

    @Provides @Singleton
    fun provideTtlProvider(sdk: SsdidSdk): TtlProvider = sdk.internalTtlProvider

    @Provides @Singleton
    fun provideBundleManager(sdk: SsdidSdk): BundleManager = sdk.internalBundleManager

    @Provides @Singleton
    fun provideVerificationOrchestrator(sdk: SsdidSdk): VerificationOrchestrator = sdk.internalVerificationOrchestrator

    @Provides @Singleton
    fun provideCredentialRepository(sdk: SsdidSdk): CredentialRepository = sdk.internalCredentialRepository

    @Provides @Singleton
    fun provideBundleSyncScheduler(sdk: SsdidSdk): BundleSyncScheduler = sdk.internalBundleSyncScheduler

    @Provides @Singleton
    fun provideBundleSyncWorkerFactory(sdk: SsdidSdk): BundleSyncWorkerFactory = sdk.internalBundleSyncWorkerFactory

    @Provides @Singleton
    fun provideConnectivityMonitor(sdk: SsdidSdk): ConnectivityMonitor = sdk.internalConnectivityMonitor

    // ── Transport APIs ──────────────────────────────────────────────────

    @Provides @Singleton
    fun provideRegistryApi(sdk: SsdidSdk): RegistryApi = sdk.internalHttpClient.registry

    @Provides @Singleton
    fun provideEmailVerifyApi(sdk: SsdidSdk): EmailVerifyApi =
        sdk.internalHttpClient.emailVerifyApi(BuildConfig.EMAIL_VERIFY_URL)

    @Provides @Singleton
    fun provideNotifyApi(sdk: SsdidSdk): NotifyApi =
        sdk.internalHttpClient.notifyApi(BuildConfig.NOTIFY_URL)

    // ── Notification platform types ─────────────────────────────────────

    @Provides @Singleton
    fun provideLocalNotificationStorage(sdk: SsdidSdk): LocalNotificationStorage =
        sdk.internalLocalNotificationStorage

    @Provides @Singleton
    fun provideLocalNotificationStore(sdk: SsdidSdk): my.ssdid.sdk.domain.notify.LocalNotificationStore =
        sdk.internalLocalNotificationStorage

    @Provides @Singleton
    fun provideNotifyStorage(sdk: SsdidSdk): NotifyStorage = sdk.internalNotifyStorage

    @Provides @Singleton
    fun provideNotifyDispatcher(
        @ApplicationContext context: Context
    ): AndroidNotifyDispatcher = AndroidNotifyDispatcher(context)

    @Provides @Singleton
    fun provideNotifyLifecycleObserver(
        @ApplicationContext context: Context,
        sdk: SsdidSdk
    ): NotifyLifecycleObserver = NotifyLifecycleObserver(
        context.applicationContext as android.app.Application,
        sdk.internalNotifyManager,
        sdk.internalVault
    )

    // ── Recovery (needs wallet-specific storage) ────────────────────────

    @Provides @Singleton
    fun provideSocialRecoveryManager(
        sdk: SsdidSdk,
        storage: SocialRecoveryStorage
    ): SocialRecoveryManager = SocialRecoveryManager(sdk.internalRecoveryManager, storage)

    @Provides @Singleton
    fun provideInstitutionalRecoveryManager(
        sdk: SsdidSdk,
        storage: InstitutionalRecoveryStorage
    ): InstitutionalRecoveryManager = InstitutionalRecoveryManager(sdk.internalRecoveryManager, storage)

    // ── Wallet-specific (not from SDK) ──────────────────────────────────

    @Provides @Singleton
    fun provideBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()

    @Provides @Singleton
    fun provideProfileMigration(vault: Vault): ProfileMigration = ProfileMigration(vault)

    @Provides @Singleton
    fun provideCredentialIssuanceManager(sdk: SsdidSdk): CredentialIssuanceManager =
        CredentialIssuanceManager(sdk.internalVault, sdk.internalHttpClient)
}
