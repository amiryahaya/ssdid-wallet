package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.sentry.okhttp.SentryOkHttpInterceptor
import my.ssdid.wallet.BuildConfig
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.backup.BackupManager
import my.ssdid.wallet.domain.credential.CredentialIssuanceManager
import my.ssdid.wallet.domain.device.DeviceInfoProvider
import my.ssdid.wallet.domain.device.DeviceManager
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.PqcProvider
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.profile.ProfileMigration
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.wallet.domain.revocation.HttpStatusListFetcher
import my.ssdid.wallet.domain.revocation.RevocationManager
import my.ssdid.wallet.domain.rotation.KeyRotationManager
import my.ssdid.wallet.domain.transport.RetryInterceptor
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultImpl
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.logging.SentryLogger
import my.ssdid.wallet.domain.did.DidJwkResolver
import my.ssdid.wallet.domain.did.DidKeyResolver
import my.ssdid.wallet.domain.did.DidResolver
import my.ssdid.wallet.domain.did.MultiMethodResolver
import my.ssdid.wallet.domain.did.SsdidRegistryResolver
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.domain.verifier.VerifierImpl
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.keystore.AndroidKeystoreManager
import my.ssdid.wallet.platform.device.AndroidDeviceInfoProvider
import my.ssdid.wallet.domain.vault.KeystoreManager
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.domain.verifier.offline.CredentialRepository
import my.ssdid.wallet.platform.storage.DataStoreSettingsRepository
import my.ssdid.wallet.domain.oid4vp.DcqlMatcher
import my.ssdid.wallet.domain.oid4vp.OpenId4VpHandler
import my.ssdid.wallet.domain.oid4vp.OpenId4VpTransport
import my.ssdid.wallet.domain.oid4vp.PresentationDefinitionMatcher
import my.ssdid.wallet.domain.notify.NotifyManager
import my.ssdid.wallet.domain.notify.NotifyStorage
import my.ssdid.wallet.platform.notify.AndroidNotifyDispatcher
import my.ssdid.wallet.platform.notify.DataStoreNotifyStorage
import my.ssdid.wallet.platform.notify.LocalNotificationStorage
import my.ssdid.wallet.platform.notify.NotifyLifecycleObserver
import my.ssdid.wallet.domain.oid4vci.IssuerMetadataResolver
import my.ssdid.wallet.domain.oid4vci.NonceManager
import my.ssdid.wallet.domain.oid4vci.OpenId4VciHandler
import my.ssdid.wallet.domain.oid4vci.OpenId4VciTransport
import my.ssdid.wallet.domain.oid4vci.TokenClient
import my.ssdid.wallet.domain.transport.EmailVerifyApi
import my.ssdid.wallet.domain.transport.NotifyApi
import my.ssdid.wallet.domain.transport.RegistryApi
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        // KeystoreManager's requireBiometric affects key GENERATION, not key USE.
        // Once a key is generated without biometric, it can't be retroactively changed.
        // Biometric gating is enforced at the app level via auto-lock in MainActivity.
        return AndroidKeystoreManager()
    }

    @Provides
    @Singleton
    @Named("classical")
    fun provideClassicalProvider(): CryptoProvider = ClassicalProvider()

    @Provides
    @Singleton
    @Named("pqc")
    fun providePqcProvider(): CryptoProvider = PqcProvider()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(SentryOkHttpInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                        else HttpLoggingInterceptor.Level.NONE
            })

        if (!BuildConfig.DEBUG) {
            // Certificate SPKI SHA-256 pins for registry and notification services.
            // Pin both leaf cert and intermediate CA for rotation safety.
            val pinner = CertificatePinner.Builder()
                .add("registry.ssdid.my",
                    "sha256/6HndsMosiTeHfV+W29g33ZHsyuPe4Yo7fPdSCUWdeF0=",  // leaf
                    "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="   // intermediate CA
                )
                .add("notify.ssdid.my",
                    "sha256/6HndsMosiTeHfV+W29g33ZHsyuPe4Yo7fPdSCUWdeF0=",  // leaf
                    "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="   // intermediate CA
                )
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): SsdidHttpClient {
        return SsdidHttpClient(
            registryUrl = "https://registry.ssdid.my",
            okHttp = okHttpClient
        )
    }

    @Provides
    @Singleton
    fun provideRegistryApi(httpClient: SsdidHttpClient): RegistryApi = httpClient.registry

    @Provides
    @Singleton
    fun provideVault(
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager,
        storage: VaultStorage,
        credentialRepository: CredentialRepository
    ): Vault = VaultImpl(classical, pqc, keystoreManager, storage, credentialRepository, SentryLogger())

    @Provides
    @Singleton
    fun provideDidResolver(httpClient: SsdidHttpClient): DidResolver {
        val ssdidResolver = SsdidRegistryResolver(httpClient.registry)
        return MultiMethodResolver(ssdidResolver, DidKeyResolver(), DidJwkResolver())
    }

    @Provides
    @Singleton
    fun provideVerifier(
        didResolver: DidResolver,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider
    ): Verifier = VerifierImpl(didResolver, classical, pqc)

    @Provides
    @Singleton
    fun provideRevocationManager(okHttpClient: OkHttpClient): RevocationManager {
        val json = Json { ignoreUnknownKeys = true }
        return RevocationManager(HttpStatusListFetcher(okHttpClient, json))
    }

    @Provides
    @Singleton
    fun provideSsdidClient(
        vault: Vault,
        verifier: Verifier,
        httpClient: SsdidHttpClient,
        activityRepo: ActivityRepository,
        revocationManager: RevocationManager,
        notifyManager: NotifyManager
    ): SsdidClient = SsdidClient(vault, verifier, httpClient, activityRepo, revocationManager, notifyManager, SentryLogger())

    @Provides
    @Singleton
    fun provideRecoveryManager(
        vault: Vault,
        storage: VaultStorage,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager
    ): RecoveryManager = RecoveryManager(vault, storage, classical, pqc, keystoreManager)

    @Provides
    @Singleton
    fun provideSocialRecoveryManager(
        recoveryManager: RecoveryManager,
        storage: SocialRecoveryStorage
    ): SocialRecoveryManager = SocialRecoveryManager(recoveryManager, storage)

    @Provides
    @Singleton
    fun provideInstitutionalRecoveryManager(
        recoveryManager: RecoveryManager,
        storage: InstitutionalRecoveryStorage
    ): InstitutionalRecoveryManager = InstitutionalRecoveryManager(recoveryManager, storage)

    @Provides
    @Singleton
    fun provideKeyRotationManager(
        storage: VaultStorage,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager,
        activityRepo: ActivityRepository,
        ssdidClient: dagger.Lazy<SsdidClient>
    ): KeyRotationManager = KeyRotationManager(storage, classical, pqc, keystoreManager, activityRepo, { ssdidClient.get() })

    @Provides
    @Singleton
    fun provideBackupManager(
        vault: Vault,
        keystoreManager: KeystoreManager,
        activityRepo: ActivityRepository
    ): BackupManager = BackupManager(vault, keystoreManager, activityRepo)

    @Provides
    @Singleton
    fun provideProfileMigration(vault: Vault): ProfileMigration = ProfileMigration(vault)

    @Provides
    @Singleton
    fun provideCredentialIssuanceManager(
        vault: Vault,
        httpClient: SsdidHttpClient
    ): CredentialIssuanceManager = CredentialIssuanceManager(vault, httpClient)

    @Provides
    @Singleton
    fun provideDeviceInfoProvider(): DeviceInfoProvider = AndroidDeviceInfoProvider()

    @Provides
    @Singleton
    fun provideDeviceManager(
        vault: Vault,
        httpClient: SsdidHttpClient,
        ssdidClient: dagger.Lazy<SsdidClient>,
        deviceInfoProvider: DeviceInfoProvider
    ): DeviceManager = DeviceManager(vault, httpClient, { ssdidClient.get() }, deviceInfoProvider)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = DataStoreSettingsRepository(context)

    @Provides
    @Singleton
    fun provideEmailVerifyApi(httpClient: SsdidHttpClient): EmailVerifyApi =
        httpClient.emailVerifyApi(BuildConfig.EMAIL_VERIFY_URL)

    @Provides
    @Singleton
    fun provideNotifyApi(httpClient: SsdidHttpClient): NotifyApi =
        httpClient.notifyApi(BuildConfig.NOTIFY_URL)

    @Provides
    @Singleton
    fun provideNotifyStorage(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager
    ): NotifyStorage = DataStoreNotifyStorage(context, keystoreManager)

    @Provides
    @Singleton
    fun provideLocalNotificationStorage(
        @ApplicationContext context: Context
    ): LocalNotificationStorage = LocalNotificationStorage(context)

    @Provides
    @Singleton
    fun provideNotifyManager(
        notifyApi: NotifyApi,
        storage: NotifyStorage,
        dispatcher: AndroidNotifyDispatcher,
        localNotificationStorage: LocalNotificationStorage
    ): NotifyManager = NotifyManager(notifyApi, storage, dispatcher, localNotificationStorage)

    @Provides
    @Singleton
    fun provideNotifyDispatcher(
        @ApplicationContext context: Context
    ): AndroidNotifyDispatcher = AndroidNotifyDispatcher(context)

    @Provides
    @Singleton
    fun provideNotifyLifecycleObserver(
        @ApplicationContext context: Context,
        notifyManager: NotifyManager,
        vault: Vault
    ): NotifyLifecycleObserver = NotifyLifecycleObserver(context.applicationContext as android.app.Application, notifyManager, vault)

    @Provides
    @Singleton
    fun provideOpenId4VpTransport(okHttpClient: OkHttpClient): OpenId4VpTransport =
        OpenId4VpTransport(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenId4VpHandler(
        transport: OpenId4VpTransport,
        vault: Vault
    ): OpenId4VpHandler = OpenId4VpHandler(
        transport = transport,
        peMatcher = PresentationDefinitionMatcher(),
        dcqlMatcher = DcqlMatcher(),
        vault = vault
    )

    @Provides
    @Singleton
    fun provideIssuerMetadataResolver(okHttpClient: OkHttpClient): IssuerMetadataResolver =
        IssuerMetadataResolver(okHttpClient)

    @Provides
    @Singleton
    fun provideTokenClient(okHttpClient: OkHttpClient): TokenClient =
        TokenClient(okHttpClient)

    @Provides
    @Singleton
    fun provideNonceManager(): NonceManager = NonceManager()

    @Provides
    @Singleton
    fun provideOpenId4VciTransport(okHttpClient: OkHttpClient): OpenId4VciTransport =
        OpenId4VciTransport(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenId4VciHandler(
        metadataResolver: IssuerMetadataResolver,
        tokenClient: TokenClient,
        nonceManager: NonceManager,
        transport: OpenId4VciTransport,
        vault: Vault
    ): OpenId4VciHandler = OpenId4VciHandler(metadataResolver, tokenClient, nonceManager, transport, vault)
}
