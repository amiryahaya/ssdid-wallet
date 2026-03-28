import Foundation
import SsdidCore

/// Lightweight dependency container for app-wide services.
/// Instantiated once at app launch and injected via SwiftUI environment.
///
/// Uses ``SsdidSdk.Builder`` for all domain/platform wiring and exposes
/// service accessors for backward compatibility with existing Feature screens.
@MainActor
final class ServiceContainer: ObservableObject {

    /// Set by `SsdidWalletApp` after the container is created so that AppDelegate
    /// can forward BGAppRefreshTask events to the live `bundleSyncManager` instance.
    /// This is safe because registration fires after `applicationDidBecomeActive`.
    static weak var shared: ServiceContainer?

    /// The fully wired SDK instance.
    let sdk: SsdidSdk

    /// Wallet-specific: biometric authentication gate.
    let biometricAuthenticator: BiometricAuthenticator

    // MARK: - Backward-Compatible Service Accessors

    var keychainManager: KeychainManager { sdk.internalKeychainManager }
    var storage: VaultStorage { sdk.internalStorage }
    var activityRepo: ActivityRepository { sdk.internalActivityRepo }
    var vault: Vault { sdk.internalVault }
    var classicalProvider: CryptoProvider { sdk.internalClassicalProvider }
    var pqcProvider: CryptoProvider { sdk.internalPqcProvider }
    var backupManager: BackupManager { sdk.internalBackupManager }
    var httpClient: SsdidHttpClient { sdk.internalHttpClient }
    var revocationManager: RevocationManager { sdk.internalRevocationManager }
    var ssdidClient: SsdidClient { sdk.internalSsdidClient }
    var notifyManager: NotifyManager { sdk.internalNotifyManager }
    var localNotificationStorage: LocalNotificationStorage { sdk.internalLocalNotificationStorage }
    var ttlProvider: TtlProvider { sdk.internalTtlProvider }
    var bundleStore: BundleStore { sdk.internalBundleStore }
    var credentialRepository: CredentialRepository { sdk.internalCredentialRepository }
    var connectivityMonitor: ConnectivityMonitor { sdk.internalConnectivityMonitor }
    var offlineVerifier: OfflineVerifier { sdk.internalOfflineVerifier }
    var verificationOrchestrator: VerificationOrchestrator { sdk.internalVerificationOrchestrator }
    var bundleManager: BundleManager { sdk.internalBundleManager }
    var bundleSyncManager: BundleSyncManager { sdk.internalBundleSyncManager }

    /// Base URL for the SSDID Notify service. Override in debug builds or via configuration.
    static let notifyBaseURL: String = "https://notify.ssdid.my"

    init() {
        let sdk = SsdidSdk.Builder()
            .registryUrl("https://registry.ssdid.my")
            .notifyUrl(Self.notifyBaseURL)
            .pqcProvider(PqcProvider())
            .build()

        self.sdk = sdk
        self.biometricAuthenticator = BiometricAuthenticator()

        // Schedule the initial background sync request. Registration is handled earlier
        // in AppDelegate.didFinishLaunchingWithOptions via BundleSyncManager.registerHandler.
        sdk.internalBundleSyncManager.scheduleBackgroundSync()

        // One-time migration: copy legacy global profile VC to first identity
        let migrationVault: Vault = sdk.internalVault
        Task.detached(priority: .utility) {
            await ProfileMigration.migrateIfNeeded(vault: migrationVault)
        }
    }
}
