import Foundation

/// Lightweight dependency container for app-wide services.
/// Instantiated once at app launch and injected via SwiftUI environment.
@MainActor
final class ServiceContainer: ObservableObject {

    let keychainManager: KeychainManager
    let storage: VaultStorage
    let activityRepo: ActivityRepository
    let vault: Vault
    let classicalProvider: CryptoProvider
    let pqcProvider: CryptoProvider
    let backupManager: BackupManager
    let biometricAuthenticator: BiometricAuthenticator
    let httpClient: SsdidHttpClient
    let revocationManager: RevocationManager
    let ssdidClient: SsdidClient
    let notifyManager: NotifyManager
    let localNotificationStorage: LocalNotificationStorage
    let ttlProvider: TtlProvider
    let bundleStore: BundleStore
    let credentialRepository: CredentialRepository
    let connectivityMonitor: ConnectivityMonitor
    let offlineVerifier: OfflineVerifier
    let verificationOrchestrator: VerificationOrchestrator
    let bundleSyncManager: BundleSyncManager

    /// Base URL for the SSDID Notify service. Override in debug builds or via configuration.
    static let notifyBaseURL: String = "https://notify.ssdid.my"

    init() {
        let keychain = KeychainManager()
        let fileStorage = FileVaultStorage()
        let activityRepository = UserDefaultsActivityRepository()

        self.keychainManager = keychain
        self.storage = fileStorage
        self.activityRepo = activityRepository
        self.biometricAuthenticator = BiometricAuthenticator()

        let classical = ClassicalProvider()
        let pqc = PqcProvider()

        self.classicalProvider = classical
        self.pqcProvider = pqc

        let vaultImpl = VaultImpl(
            classicalProvider: classical,
            pqcProvider: pqc,
            keychainManager: keychain,
            storage: fileStorage
        )

        self.vault = vaultImpl
        self.backupManager = BackupManager(
            vault: vaultImpl,
            storage: fileStorage,
            keychainManager: keychain,
            activityRepo: activityRepository
        )

        self.httpClient = SsdidHttpClient()
        let httpClient = self.httpClient
        let ssdidRegistryResolver = SsdidRegistryResolver(registryApi: httpClient.registry)
        let didResolver = MultiMethodResolver(
            ssdidResolver: ssdidRegistryResolver,
            keyResolver: DidKeyResolver(),
            jwkResolver: DidJwkResolver()
        )
        let verifier = VerifierImpl(
            didResolver: didResolver,
            classicalProvider: classical,
            pqcProvider: pqc
        )

        let localNotifStorage = LocalNotificationStorage()
        self.localNotificationStorage = localNotifStorage

        let notifyMgr = NotifyManager(
            api: httpClient.notifyApi(baseURL: Self.notifyBaseURL),
            keychainManager: keychain,
            localNotificationStorage: localNotifStorage
        )
        self.notifyManager = notifyMgr

        let revocationMgr = RevocationManager(fetcher: HttpStatusListFetcher())
        self.revocationManager = revocationMgr

        self.ssdidClient = SsdidClient(
            vault: vaultImpl,
            verifier: verifier,
            httpClient: httpClient,
            activityRepo: activityRepository,
            revocationManager: revocationMgr,
            notifyManager: notifyMgr
        )

        // Offline verification stack
        let ttl = TtlProvider()
        self.ttlProvider = ttl

        let fileBundleStore = FileBundleStore()
        self.bundleStore = fileBundleStore

        let fileCredentialRepository = FileCredentialRepository()
        self.credentialRepository = fileCredentialRepository

        self.connectivityMonitor = ConnectivityMonitor()

        let offlineVerifierImpl = OfflineVerifier(
            classicalProvider: classical,
            pqcProvider: pqc,
            bundleStore: fileBundleStore
        )
        self.offlineVerifier = offlineVerifierImpl

        self.verificationOrchestrator = VerificationOrchestrator(
            onlineVerifier: verifier,
            offlineVerifier: offlineVerifierImpl,
            bundleStore: fileBundleStore,
            ttlProvider: ttl
        )

        let bundleFetcher = BundleFetcher(
            registryApi: httpClient.registry,
            bundleStore: fileBundleStore,
            ttlProvider: ttl
        )
        let syncManager = BundleSyncManager(
            bundleStore: fileBundleStore,
            bundleFetcher: bundleFetcher,
            credentialRepository: fileCredentialRepository,
            ttlProvider: ttl
        )
        self.bundleSyncManager = syncManager

        // Register the background bundle-sync task as early as possible.
        // BGTaskScheduler requires this before applicationDidBecomeActive fires.
        syncManager.registerBackgroundTask()
        syncManager.scheduleBackgroundSync()

        // One-time migration: copy legacy global profile VC to first identity
        let migrationVault: Vault = vaultImpl
        Task.detached(priority: .utility) {
            await ProfileMigration.migrateIfNeeded(vault: migrationVault)
        }
    }
}
