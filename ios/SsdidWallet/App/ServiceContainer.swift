import Foundation

/// Lightweight dependency container for app-wide services.
/// Instantiated once at app launch and injected via SwiftUI environment.
@MainActor
final class ServiceContainer: ObservableObject {

    let keychainManager: KeychainManager
    let storage: VaultStorage
    let activityRepo: ActivityRepository
    let vault: Vault
    let backupManager: BackupManager
    let biometricAuthenticator: BiometricAuthenticator
    let httpClient: SsdidHttpClient
    let revocationManager: RevocationManager
    let ssdidClient: SsdidClient
    let notifyManager: NotifyManager
    let localNotificationStorage: LocalNotificationStorage

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
        let verifier = VerifierImpl(
            registryApi: httpClient.registry,
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
    }
}
