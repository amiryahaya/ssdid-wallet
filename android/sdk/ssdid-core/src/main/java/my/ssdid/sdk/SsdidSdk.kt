package my.ssdid.sdk

import android.content.Context
import my.ssdid.sdk.api.*
import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.backup.BackupManager
import my.ssdid.sdk.domain.crypto.BouncyCastleInstaller
import my.ssdid.sdk.domain.crypto.ClassicalProvider
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.device.DeviceManager
import my.ssdid.sdk.domain.did.DidJwkResolver
import my.ssdid.sdk.domain.did.DidKeyResolver
import my.ssdid.sdk.domain.did.DidResolver
import my.ssdid.sdk.domain.did.MultiMethodResolver
import my.ssdid.sdk.domain.did.SsdidRegistryResolver
import my.ssdid.sdk.domain.history.ActivityRepository
import my.ssdid.sdk.domain.logging.NoOpLogger
import my.ssdid.sdk.domain.logging.SsdidLogger
import my.ssdid.sdk.domain.notify.LocalNotificationStore
import my.ssdid.sdk.domain.notify.NotifyDispatcher
import my.ssdid.sdk.domain.notify.NotifyManager
import my.ssdid.sdk.domain.notify.NotifyStorage
import my.ssdid.sdk.domain.oid4vci.IssuerMetadataResolver
import my.ssdid.sdk.domain.oid4vci.NonceManager
import my.ssdid.sdk.domain.oid4vci.OpenId4VciHandler
import my.ssdid.sdk.domain.oid4vci.OpenId4VciTransport
import my.ssdid.sdk.domain.oid4vci.TokenClient
import my.ssdid.sdk.domain.oid4vp.DcqlMatcher
import my.ssdid.sdk.domain.oid4vp.OpenId4VpHandler
import my.ssdid.sdk.domain.oid4vp.OpenId4VpTransport
import my.ssdid.sdk.domain.oid4vp.PresentationDefinitionMatcher
import my.ssdid.sdk.domain.recovery.RecoveryManager
import my.ssdid.sdk.domain.revocation.HttpStatusListFetcher
import my.ssdid.sdk.domain.revocation.RevocationManager
import my.ssdid.sdk.domain.rotation.KeyRotationManager
import my.ssdid.sdk.domain.settings.SettingsRepository
import my.ssdid.sdk.domain.settings.TtlProvider
import my.ssdid.sdk.domain.transport.RetryInterceptor
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.transport.dto.PendingNotification
import my.ssdid.sdk.domain.vault.KeystoreManager
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.sdk.domain.vault.VaultImpl
import my.ssdid.sdk.domain.vault.VaultStorage
import my.ssdid.sdk.domain.verifier.Verifier
import my.ssdid.sdk.domain.verifier.VerifierImpl
import my.ssdid.sdk.domain.verifier.offline.BundleManager
import my.ssdid.sdk.domain.verifier.offline.BundleStore
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import my.ssdid.sdk.domain.verifier.offline.OfflineVerifier
import my.ssdid.sdk.domain.verifier.offline.VerificationOrchestrator
import my.ssdid.sdk.domain.verifier.offline.sync.BundleSyncScheduler
import my.ssdid.sdk.domain.verifier.offline.sync.ConnectivityMonitor
import my.ssdid.sdk.platform.device.AndroidDeviceInfoProvider
import my.ssdid.sdk.platform.keystore.AndroidKeystoreManager
import my.ssdid.sdk.platform.notify.DataStoreNotifyStorage
import my.ssdid.sdk.platform.notify.LocalNotificationStorage
import my.ssdid.sdk.platform.storage.ActivityRepositoryImpl
import my.ssdid.sdk.platform.storage.DataStoreBundleStore
import my.ssdid.sdk.platform.storage.DataStoreCredentialRepository
import my.ssdid.sdk.platform.storage.DataStoreSettingsRepository
import my.ssdid.sdk.platform.storage.DataStoreVaultStorage
import my.ssdid.sdk.platform.sync.AndroidConnectivityMonitor
import my.ssdid.sdk.platform.sync.BundleSyncWorkerFactory
import my.ssdid.sdk.platform.sync.WorkManagerBundleSyncScheduler
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class SsdidSdk private constructor(
    // Public capability sub-objects
    val identity: IdentityApi,
    val vault: VaultApi,
    val credentials: CredentialsApi,
    val flows: FlowsApi,
    val issuance: IssuanceApi,
    val presentation: PresentationApi,
    val sdJwt: SdJwtApi,
    val verifier: VerifierApi,
    val offline: OfflineApi,
    val recovery: RecoveryApi,
    val rotation: RotationApi,
    val backup: BackupApi,
    val device: DeviceApi,
    val notifications: NotificationsApi,
    val revocation: RevocationApi,
    val history: HistoryApi,
    // Migration helpers — exposed for wallet app DI backward compatibility.
    // Prefer the public API sub-objects (identity, vault, credentials, etc.) for new code.
    val internalVault: Vault,
    val internalVerifier: Verifier,
    val internalClient: SsdidClient,
    val internalHttpClient: SsdidHttpClient,
    val internalOid4VciHandler: OpenId4VciHandler,
    val internalOid4VpHandler: OpenId4VpHandler,
    val internalNotifyManager: NotifyManager,
    val internalRecoveryManager: RecoveryManager,
    val internalKeyRotationManager: KeyRotationManager,
    val internalBackupManager: BackupManager,
    val internalDeviceManager: DeviceManager,
    val internalRevocationManager: RevocationManager,
    val internalActivityRepo: ActivityRepository,
    val internalOkHttpClient: OkHttpClient,
    val internalVaultStorage: VaultStorage,
    val internalKeystoreManager: KeystoreManager,
    val internalSettingsRepository: SettingsRepository,
    val internalBundleStore: BundleStore,
    val internalTtlProvider: TtlProvider,
    val internalBundleManager: BundleManager,
    val internalOfflineVerifier: OfflineVerifier,
    val internalVerificationOrchestrator: VerificationOrchestrator,
    val internalCredentialRepository: CredentialRepository,
    val internalBundleSyncScheduler: BundleSyncScheduler,
    val internalBundleSyncWorkerFactory: BundleSyncWorkerFactory,
    val internalConnectivityMonitor: ConnectivityMonitor,
    val internalLocalNotificationStorage: LocalNotificationStorage,
    val internalNotifyStorage: NotifyStorage
) {
    companion object {
        fun builder(context: Context): Builder = Builder(context)
    }

    class Builder(private val context: Context) {
        private var registryUrl: String? = null
        private var notifyUrl: String? = null
        private var certificatePinningEnabled: Boolean = false
        private var certificatePins: List<Pair<String, List<String>>> = emptyList()
        private var customKeystoreManager: KeystoreManager? = null
        private var customVaultStorage: VaultStorage? = null
        private var additionalCryptoProviders: MutableList<CryptoProvider> = mutableListOf()
        private var logger: SsdidLogger = NoOpLogger()
        private var customActivityRepo: ActivityRepository? = null
        private var customNotifyStorage: NotifyStorage? = null
        private var customNotifyDispatcher: NotifyDispatcher? = null
        private var customLocalNotificationStore: LocalNotificationStore? = null
        private var customSettingsRepo: SettingsRepository? = null
        private var customCredentialRepo: CredentialRepository? = null
        private var customBundleStore: BundleStore? = null

        fun registryUrl(url: String) = apply { this.registryUrl = url }
        fun notifyUrl(url: String) = apply { this.notifyUrl = url }
        fun certificatePinning(
            enabled: Boolean,
            pins: List<Pair<String, List<String>>> = emptyList()
        ) = apply {
            this.certificatePinningEnabled = enabled
            this.certificatePins = pins
        }
        fun keystoreManager(manager: KeystoreManager) = apply { this.customKeystoreManager = manager }
        fun vaultStorage(storage: VaultStorage) = apply { this.customVaultStorage = storage }
        fun addCryptoProvider(provider: CryptoProvider) = apply { this.additionalCryptoProviders.add(provider) }
        fun logger(logger: SsdidLogger) = apply { this.logger = logger }
        fun activityRepository(repo: ActivityRepository) = apply { this.customActivityRepo = repo }
        fun notifyStorage(storage: NotifyStorage) = apply { this.customNotifyStorage = storage }
        fun notifyDispatcher(dispatcher: NotifyDispatcher) = apply { this.customNotifyDispatcher = dispatcher }
        fun localNotificationStore(store: LocalNotificationStore) = apply { this.customLocalNotificationStore = store }
        fun settingsRepository(repo: SettingsRepository) = apply { this.customSettingsRepo = repo }
        fun credentialRepository(repo: CredentialRepository) = apply { this.customCredentialRepo = repo }
        fun bundleStore(store: BundleStore) = apply { this.customBundleStore = store }

        fun build(): SsdidSdk {
            val regUrl = registryUrl ?: throw IllegalStateException("registryUrl is required")

            BouncyCastleInstaller.ensureInstalled()

            // Core infrastructure
            val keystoreManager = customKeystoreManager ?: AndroidKeystoreManager()
            val vaultStorage = customVaultStorage ?: DataStoreVaultStorage(context)

            // Crypto providers
            val classical: CryptoProvider = ClassicalProvider()
            // Find PQC provider from additional providers (if any)
            val pqc: CryptoProvider = additionalCryptoProviders.firstOrNull { provider ->
                my.ssdid.sdk.domain.model.Algorithm.entries.any { it.isPostQuantum && provider.supportsAlgorithm(it) }
            } ?: classical // fallback to classical if no PQC provider

            // Vault
            val credentialRepository = customCredentialRepo ?: DataStoreCredentialRepository(context)
            val vault: Vault = VaultImpl(classical, pqc, keystoreManager, vaultStorage, credentialRepository, logger)

            // OkHttp
            val okHttpBuilder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(RetryInterceptor())
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                })

            if (certificatePinningEnabled && certificatePins.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                certificatePins.forEach { (hostname, pins) ->
                    pins.forEach { pin -> pinnerBuilder.add(hostname, pin) }
                }
                okHttpBuilder.certificatePinner(pinnerBuilder.build())
            }
            val okHttpClient = okHttpBuilder.build()

            // Transport
            val httpClient = SsdidHttpClient(registryUrl = regUrl, okHttp = okHttpClient)

            // DID resolution
            val ssdidResolver = SsdidRegistryResolver(httpClient.registry)
            val didResolver: DidResolver = MultiMethodResolver(ssdidResolver, DidKeyResolver(), DidJwkResolver())

            // Verifier
            val verifier: Verifier = VerifierImpl(didResolver, classical, pqc)

            // Revocation
            val json = Json { ignoreUnknownKeys = true }
            val revocationManager = RevocationManager(HttpStatusListFetcher(okHttpClient, json))

            // Activity
            val activityRepo = customActivityRepo ?: ActivityRepositoryImpl(context)

            // Notify
            val notifyStorage = customNotifyStorage ?: DataStoreNotifyStorage(context, keystoreManager)
            val notifyDispatcher = customNotifyDispatcher ?: NotifyDispatcher { _, _ -> }
            val localNotificationStorageImpl = LocalNotificationStorage(context)
            val localNotificationStore = customLocalNotificationStore ?: localNotificationStorageImpl
            val notifyApiInstance = httpClient.notifyApi(notifyUrl ?: regUrl)
            val notifyManager = NotifyManager(
                notifyApi = notifyApiInstance,
                storage = notifyStorage,
                dispatcher = notifyDispatcher,
                localNotificationStorage = localNotificationStore,
                logger = logger
            )

            // SsdidClient
            val client = SsdidClient(vault, verifier, httpClient, activityRepo, revocationManager, notifyManager, logger)

            // Recovery
            val recoveryManager = RecoveryManager(vault, vaultStorage, classical, pqc, keystoreManager)

            // Key rotation
            val rotationManager = KeyRotationManager(vaultStorage, classical, pqc, keystoreManager, activityRepo, { client })

            // Backup
            val backupManager = BackupManager(vault, keystoreManager, activityRepo)

            // Device
            val deviceInfoProvider = AndroidDeviceInfoProvider()
            val deviceManager = DeviceManager(vault, httpClient, { client }, deviceInfoProvider)

            // OID4VCI
            val metadataResolver = IssuerMetadataResolver(okHttpClient)
            val tokenClient = TokenClient(okHttpClient)
            val nonceManager = NonceManager()
            val oid4VciTransport = OpenId4VciTransport(okHttpClient)
            val oid4VciHandler = OpenId4VciHandler(metadataResolver, tokenClient, nonceManager, oid4VciTransport, vault)

            // OID4VP
            val oid4VpTransport = OpenId4VpTransport(okHttpClient)
            val oid4VpHandler = OpenId4VpHandler(oid4VpTransport, PresentationDefinitionMatcher(), DcqlMatcher(), vault)

            // Offline verification
            val settingsRepo = customSettingsRepo ?: DataStoreSettingsRepository(context)
            val ttlProvider = TtlProvider(settingsRepo)
            val bundleStore = customBundleStore ?: DataStoreBundleStore(context)
            val offlineVerifier = OfflineVerifier(classical, pqc, bundleStore, ttlProvider)
            val bundleManager = BundleManager(verifier, HttpStatusListFetcher(okHttpClient, json), bundleStore, ttlProvider)
            val orchestrator = VerificationOrchestrator(verifier, offlineVerifier, bundleStore)

            // Sync infrastructure
            val bundleSyncWorkerFactory = BundleSyncWorkerFactory(bundleManager, credentialRepository)
            val connectivityMonitor: ConnectivityMonitor = AndroidConnectivityMonitor(context)
            val bundleSyncScheduler: BundleSyncScheduler = WorkManagerBundleSyncScheduler(context)

            // Build API sub-objects
            return SsdidSdk(
                identity = IdentityApi(vault, client),
                vault = VaultApi(vault),
                credentials = CredentialsApi(vault),
                flows = FlowsApi(client),
                issuance = IssuanceApi(oid4VciHandler),
                presentation = PresentationApi(oid4VpHandler),
                sdJwt = SdJwtApi(vault),
                verifier = VerifierApi(verifier),
                offline = OfflineApi(offlineVerifier, bundleManager, orchestrator),
                recovery = RecoveryApi(recoveryManager),
                rotation = RotationApi(rotationManager),
                backup = BackupApi(backupManager),
                device = DeviceApi(deviceManager),
                notifications = NotificationsApi(notifyManager),
                revocation = RevocationApi(revocationManager),
                history = HistoryApi(activityRepo),
                internalVault = vault,
                internalVerifier = verifier,
                internalClient = client,
                internalHttpClient = httpClient,
                internalOid4VciHandler = oid4VciHandler,
                internalOid4VpHandler = oid4VpHandler,
                internalNotifyManager = notifyManager,
                internalRecoveryManager = recoveryManager,
                internalKeyRotationManager = rotationManager,
                internalBackupManager = backupManager,
                internalDeviceManager = deviceManager,
                internalRevocationManager = revocationManager,
                internalActivityRepo = activityRepo,
                internalOkHttpClient = okHttpClient,
                internalVaultStorage = vaultStorage,
                internalKeystoreManager = keystoreManager,
                internalSettingsRepository = settingsRepo,
                internalBundleStore = bundleStore,
                internalTtlProvider = ttlProvider,
                internalBundleManager = bundleManager,
                internalOfflineVerifier = offlineVerifier,
                internalVerificationOrchestrator = orchestrator,
                internalCredentialRepository = credentialRepository,
                internalBundleSyncScheduler = bundleSyncScheduler,
                internalBundleSyncWorkerFactory = bundleSyncWorkerFactory,
                internalConnectivityMonitor = connectivityMonitor,
                internalLocalNotificationStorage = localNotificationStorageImpl,
                internalNotifyStorage = notifyStorage
            )
        }
    }
}
