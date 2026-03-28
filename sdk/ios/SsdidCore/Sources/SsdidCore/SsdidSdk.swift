import Foundation

/// Main entry point for the SSDID SDK.
///
/// Use the ``Builder`` to configure and create an instance:
/// ```swift
/// let sdk = SsdidSdk.Builder()
///     .registryUrl("https://registry.ssdid.my")
///     .logger(MyLogger())
///     .build()
/// ```
public final class SsdidSdk: @unchecked Sendable {

    /// The HTTP client used for all network operations.
    let httpClient: SsdidHttpClient

    /// The Vault for managing identities, credentials, and cryptographic operations.
    let vault: Vault

    /// The orchestrator for SSDID operations (identity creation, registration, auth, tx signing).
    let client: SsdidClient

    /// Backup manager for identity backup/restore.
    let backupManager: BackupManager

    /// Key rotation manager.
    let keyRotationManager: KeyRotationManager

    /// Recovery manager.
    let recoveryManager: RecoveryManager

    /// Revocation checking.
    let revocationManager: RevocationManager

    /// Notify manager for push notification mailboxes.
    let notifyManager: NotifyManager

    /// Offline verification.
    let offlineVerifier: OfflineVerifier

    /// Verification orchestrator (online + offline).
    let verificationOrchestrator: VerificationOrchestrator

    /// Bundle manager for verification bundle lifecycle.
    let bundleManager: BundleManager

    /// Bundle sync manager for background refresh.
    let bundleSyncManager: BundleSyncManager

    /// The logger instance.
    let logger: SsdidLogger

    private init(
        httpClient: SsdidHttpClient,
        vault: Vault,
        client: SsdidClient,
        backupManager: BackupManager,
        keyRotationManager: KeyRotationManager,
        recoveryManager: RecoveryManager,
        revocationManager: RevocationManager,
        notifyManager: NotifyManager,
        offlineVerifier: OfflineVerifier,
        verificationOrchestrator: VerificationOrchestrator,
        bundleManager: BundleManager,
        bundleSyncManager: BundleSyncManager,
        logger: SsdidLogger
    ) {
        self.httpClient = httpClient
        self.vault = vault
        self.client = client
        self.backupManager = backupManager
        self.keyRotationManager = keyRotationManager
        self.recoveryManager = recoveryManager
        self.revocationManager = revocationManager
        self.notifyManager = notifyManager
        self.offlineVerifier = offlineVerifier
        self.verificationOrchestrator = verificationOrchestrator
        self.bundleManager = bundleManager
        self.bundleSyncManager = bundleSyncManager
        self.logger = logger
    }

    // MARK: - Builder

    /// Builder for constructing a configured ``SsdidSdk`` instance.
    public final class Builder {
        private var registryUrl: String?
        private var notifyUrl: String = "https://notify.ssdid.my"
        private var _logger: SsdidLogger = NoOpLogger()
        private var _classicalProvider: CryptoProvider?
        private var _pqcProvider: CryptoProvider?

        public init() {}

        /// Sets the SSDID Registry URL (required).
        @discardableResult
        public func registryUrl(_ url: String) -> Builder {
            self.registryUrl = url
            return self
        }

        /// Sets the SSDID Notify service URL.
        @discardableResult
        public func notifyUrl(_ url: String) -> Builder {
            self.notifyUrl = url
            return self
        }

        /// Sets a custom logger implementation.
        @discardableResult
        public func logger(_ logger: SsdidLogger) -> Builder {
            self._logger = logger
            return self
        }

        /// Sets the classical crypto provider (Ed25519, ECDSA).
        @discardableResult
        func classicalProvider(_ provider: CryptoProvider) -> Builder {
            self._classicalProvider = provider
            return self
        }

        /// Sets the PQC crypto provider (KAZ-Sign).
        @discardableResult
        func pqcProvider(_ provider: CryptoProvider) -> Builder {
            self._pqcProvider = provider
            return self
        }

        /// Builds and returns a fully wired ``SsdidSdk`` instance.
        @MainActor
        public func build() -> SsdidSdk {
            guard let regUrl = registryUrl else {
                fatalError("SsdidSdk.Builder: registryUrl is required")
            }

            let classical = _classicalProvider ?? ClassicalProvider()
            // PQC provider is optional — if not set, use a no-op provider.
            let pqc = _pqcProvider ?? NoOpCryptoProvider()

            let keychain = KeychainManager()
            let storage = FileVaultStorage()
            let activityRepo = UserDefaultsActivityRepository()

            let vaultImpl = VaultImpl(
                classicalProvider: classical,
                pqcProvider: pqc,
                keychainManager: keychain,
                storage: storage
            )

            let httpClient = SsdidHttpClient(registryURL: regUrl)

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
            let notifyMgr = NotifyManager(
                api: httpClient.notifyApi(baseURL: notifyUrl),
                keychainManager: keychain,
                localNotificationStorage: localNotifStorage
            )

            let revocationMgr = RevocationManager(fetcher: HttpStatusListFetcher())

            let ssdidClient = SsdidClient(
                vault: vaultImpl,
                verifier: verifier,
                httpClient: httpClient,
                activityRepo: activityRepo,
                revocationManager: revocationMgr,
                notifyManager: notifyMgr
            )

            let backupMgr = BackupManager(
                vault: vaultImpl,
                storage: storage,
                keychainManager: keychain,
                activityRepo: activityRepo
            )

            let keyRotationMgr = KeyRotationManager(
                vault: vaultImpl,
                storage: storage,
                cryptoProvider: classical,
                keychainManager: keychain,
                ssdidClient: ssdidClient
            )

            let recoveryMgr = RecoveryManager(
                vault: vaultImpl,
                storage: storage,
                classicalProvider: classical,
                pqcProvider: pqc,
                keychainManager: keychain
            )

            let ttl = TtlProvider()
            let fileBundleStore = FileBundleStore()
            let fileCredentialRepo = FileCredentialRepository()

            let offlineVerifierImpl = OfflineVerifier(
                classicalProvider: classical,
                pqcProvider: pqc,
                bundleStore: fileBundleStore,
                ttlProvider: ttl
            )

            let verificationOrc = VerificationOrchestrator(
                onlineVerifier: verifier,
                offlineVerifier: offlineVerifierImpl,
                bundleStore: fileBundleStore
            )

            let bundleMgr = BundleManager(
                verifier: verifier,
                statusListFetcher: HttpStatusListFetcher(),
                bundleStore: fileBundleStore,
                ttlProvider: ttl
            )

            let syncMgr = BundleSyncManager(
                bundleStore: fileBundleStore,
                bundleManager: bundleMgr,
                credentialRepository: fileCredentialRepo,
                ttlProvider: ttl
            )

            return SsdidSdk(
                httpClient: httpClient,
                vault: vaultImpl,
                client: ssdidClient,
                backupManager: backupMgr,
                keyRotationManager: keyRotationMgr,
                recoveryManager: recoveryMgr,
                revocationManager: revocationMgr,
                notifyManager: notifyMgr,
                offlineVerifier: offlineVerifierImpl,
                verificationOrchestrator: verificationOrc,
                bundleManager: bundleMgr,
                bundleSyncManager: syncMgr,
                logger: _logger
            )
        }
    }
}

/// A no-op CryptoProvider used when no PQC provider is supplied.
/// Returns `false` for all algorithm support checks.
internal struct NoOpCryptoProvider: CryptoProvider {
    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool { false }
    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        throw CryptoError.unsupportedAlgorithm(algorithm)
    }
    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        throw CryptoError.unsupportedAlgorithm(algorithm)
    }
    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        throw CryptoError.unsupportedAlgorithm(algorithm)
    }
}
