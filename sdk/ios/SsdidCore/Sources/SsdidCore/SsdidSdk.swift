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

    // MARK: - Public API Facades

    /// Identity lifecycle: create, list, delete, DID document management.
    public let identity: IdentityApi

    /// Cryptographic vault: signing, proof creation.
    public let vault: VaultApi

    /// Verifiable credential storage: store, list, delete.
    public let credentials: CredentialsApi

    /// SSDID protocol flows: registration, authentication, transaction signing.
    public let flows: FlowsApi

    /// OpenID4VCI credential issuance.
    public let issuance: IssuanceApi

    /// OpenID4VP presentation.
    public let presentation: PresentationApi

    /// SD-JWT VC operations: parse, store, list.
    public let sdJwt: SdJwtApi

    /// Online credential verification.
    public let verifier: VerifierApi

    /// Offline verification and bundle management.
    public let offline: OfflineApi

    /// Identity recovery operations.
    public let recovery: RecoveryApi

    /// KERI-inspired key rotation.
    public let rotation: RotationApi

    /// Encrypted backup and restore.
    public let backup: BackupApi

    /// Multi-device management.
    public let device: DeviceApi

    /// Push notification and mailbox management.
    public let notifications: NotificationsApi

    /// Credential revocation checking.
    public let revocation: RevocationApi

    /// Activity history log.
    public let history: HistoryApi

    // MARK: - Internal Services (exposed for first-party wallet migration)

    /// The HTTP client used for all network operations.
    public let internalHttpClient: SsdidHttpClient

    /// Bundle sync manager for background refresh.
    public let internalBundleSyncManager: BundleSyncManager

    /// The vault implementation.
    public let internalVault: Vault

    /// The SSDID client orchestrator.
    public let internalSsdidClient: SsdidClient

    /// Keychain manager for key wrapping.
    public let internalKeychainManager: KeychainManager

    /// Vault storage for persistence.
    public let internalStorage: VaultStorage

    /// Classical crypto provider.
    public let internalClassicalProvider: CryptoProvider

    /// PQC crypto provider.
    public let internalPqcProvider: CryptoProvider

    /// Backup manager.
    public let internalBackupManager: BackupManager

    /// Revocation manager.
    public let internalRevocationManager: RevocationManager

    /// Notify manager.
    public let internalNotifyManager: NotifyManager

    /// Local notification storage.
    public let internalLocalNotificationStorage: LocalNotificationStorage

    /// TTL provider.
    public let internalTtlProvider: TtlProvider

    /// Bundle store.
    public let internalBundleStore: BundleStore

    /// Credential repository.
    public let internalCredentialRepository: CredentialRepository

    /// Connectivity monitor.
    public let internalConnectivityMonitor: ConnectivityMonitor

    /// Offline verifier.
    public let internalOfflineVerifier: OfflineVerifier

    /// Verification orchestrator.
    public let internalVerificationOrchestrator: VerificationOrchestrator

    /// Bundle manager.
    public let internalBundleManager: BundleManager

    /// Activity repository.
    public let internalActivityRepo: ActivityRepository

    /// The logger instance.
    public let logger: SsdidLogger

    private init(
        identity: IdentityApi,
        vault: VaultApi,
        credentials: CredentialsApi,
        flows: FlowsApi,
        issuance: IssuanceApi,
        presentation: PresentationApi,
        sdJwt: SdJwtApi,
        verifier: VerifierApi,
        offline: OfflineApi,
        recovery: RecoveryApi,
        rotation: RotationApi,
        backup: BackupApi,
        device: DeviceApi,
        notifications: NotificationsApi,
        revocation: RevocationApi,
        history: HistoryApi,
        httpClient: SsdidHttpClient,
        bundleSyncManager: BundleSyncManager,
        logger: SsdidLogger,
        internalVault: Vault,
        internalSsdidClient: SsdidClient,
        internalKeychainManager: KeychainManager,
        internalStorage: VaultStorage,
        internalClassicalProvider: CryptoProvider,
        internalPqcProvider: CryptoProvider,
        internalBackupManager: BackupManager,
        internalRevocationManager: RevocationManager,
        internalNotifyManager: NotifyManager,
        internalLocalNotificationStorage: LocalNotificationStorage,
        internalTtlProvider: TtlProvider,
        internalBundleStore: BundleStore,
        internalCredentialRepository: CredentialRepository,
        internalConnectivityMonitor: ConnectivityMonitor,
        internalOfflineVerifier: OfflineVerifier,
        internalVerificationOrchestrator: VerificationOrchestrator,
        internalBundleManager: BundleManager,
        internalActivityRepo: ActivityRepository
    ) {
        self.identity = identity
        self.vault = vault
        self.credentials = credentials
        self.flows = flows
        self.issuance = issuance
        self.presentation = presentation
        self.sdJwt = sdJwt
        self.verifier = verifier
        self.offline = offline
        self.recovery = recovery
        self.rotation = rotation
        self.backup = backup
        self.device = device
        self.notifications = notifications
        self.revocation = revocation
        self.history = history
        self.internalHttpClient = httpClient
        self.internalBundleSyncManager = bundleSyncManager
        self.logger = logger
        self.internalVault = internalVault
        self.internalSsdidClient = internalSsdidClient
        self.internalKeychainManager = internalKeychainManager
        self.internalStorage = internalStorage
        self.internalClassicalProvider = internalClassicalProvider
        self.internalPqcProvider = internalPqcProvider
        self.internalBackupManager = internalBackupManager
        self.internalRevocationManager = internalRevocationManager
        self.internalNotifyManager = internalNotifyManager
        self.internalLocalNotificationStorage = internalLocalNotificationStorage
        self.internalTtlProvider = internalTtlProvider
        self.internalBundleStore = internalBundleStore
        self.internalCredentialRepository = internalCredentialRepository
        self.internalConnectivityMonitor = internalConnectivityMonitor
        self.internalOfflineVerifier = internalOfflineVerifier
        self.internalVerificationOrchestrator = internalVerificationOrchestrator
        self.internalBundleManager = internalBundleManager
        self.internalActivityRepo = internalActivityRepo
    }

    // MARK: - Builder

    /// Builder for constructing a configured ``SsdidSdk`` instance.
    public final class Builder {
        private var registryUrl: String?
        private var notifyUrl: String = "https://notify.ssdid.my"
        private var _logger: SsdidLogger = NoOpLogger()
        private var _classicalProvider: CryptoProvider?
        private var _pqcProvider: CryptoProvider?
        private var _requireBiometric: Bool = {
            #if DEBUG
            return false
            #else
            return true
            #endif
        }()

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
        public func classicalProvider(_ provider: CryptoProvider) -> Builder {
            self._classicalProvider = provider
            return self
        }

        /// Sets the PQC crypto provider (KAZ-Sign).
        @discardableResult
        public func pqcProvider(_ provider: CryptoProvider) -> Builder {
            self._pqcProvider = provider
            return self
        }

        /// Configure whether vault keys require biometric authentication.
        /// Defaults to `false` in DEBUG builds, `true` in release builds.
        @discardableResult
        public func requireBiometric(_ value: Bool) -> Builder {
            self._requireBiometric = value
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

            let keychain = KeychainManager(requireBiometric: _requireBiometric)
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
            let verifierImpl = VerifierImpl(
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

            notifyMgr.identityProvider = { [weak vaultImpl] in
                await vaultImpl?.listIdentities() ?? []
            }

            let revocationMgr = RevocationManager(fetcher: HttpStatusListFetcher())

            let ssdidClient = SsdidClient(
                vault: vaultImpl,
                verifier: verifierImpl,
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
                onlineVerifier: verifierImpl,
                offlineVerifier: offlineVerifierImpl,
                bundleStore: fileBundleStore
            )

            let bundleMgr = BundleManager(
                verifier: verifierImpl,
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

            // OpenID4VCI handler
            let vciTransport = OpenId4VciTransport()
            let metadataResolver = IssuerMetadataResolver()
            let tokenClient = TokenClient()
            let nonceManager = NonceManager()
            let vciHandler = OpenId4VciHandler(
                metadataResolver: metadataResolver,
                tokenClient: tokenClient,
                nonceManager: nonceManager,
                transport: vciTransport,
                vcStorage: storage
            )

            // OpenID4VP handler
            let vpTransport = OpenId4VpTransport()
            let vpHandler = OpenId4VpHandler(
                transport: vpTransport,
                vcStore: storage
            )

            // Device manager
            let deviceInfoProvider = DeviceInfoProvider()
            let deviceRegistryClient = HttpDeviceManagerRegistryClient(registryApi: httpClient.registry)
            let deviceSsdidProvider = SsdidClientDeviceProvider(ssdidClient: ssdidClient)
            let deviceMgr = DeviceManager(
                vault: vaultImpl,
                registryClient: deviceRegistryClient,
                ssdidClientProvider: deviceSsdidProvider,
                deviceName: deviceInfoProvider.deviceName,
                platform: "ios"
            )

            // Build all API facades
            let identityApi = IdentityApi(vault: vaultImpl, client: ssdidClient)
            let vaultApi = VaultApi(vault: vaultImpl)
            let credentialsApi = CredentialsApi(vault: vaultImpl)
            let flowsApi = FlowsApi(client: ssdidClient)
            let issuanceApi = IssuanceApi(handler: vciHandler)
            let presentationApi = PresentationApi(handler: vpHandler)
            let sdJwtApi = SdJwtApi(storage: storage)
            let verifierApi = VerifierApi(verifier: verifierImpl)
            let offlineApi = OfflineApi(
                offlineVerifier: offlineVerifierImpl,
                bundleManager: bundleMgr,
                orchestrator: verificationOrc
            )
            let recoveryApi = RecoveryApi(manager: recoveryMgr)
            let rotationApi = RotationApi(manager: keyRotationMgr)
            let backupApi = BackupApi(manager: backupMgr)
            let deviceApi = DeviceApi(manager: deviceMgr)
            let notificationsApi = NotificationsApi(manager: notifyMgr)
            let revocationApi = RevocationApi(manager: revocationMgr)
            let historyApi = HistoryApi(repo: activityRepo)

            let connectivityMon = ConnectivityMonitor()

            return SsdidSdk(
                identity: identityApi,
                vault: vaultApi,
                credentials: credentialsApi,
                flows: flowsApi,
                issuance: issuanceApi,
                presentation: presentationApi,
                sdJwt: sdJwtApi,
                verifier: verifierApi,
                offline: offlineApi,
                recovery: recoveryApi,
                rotation: rotationApi,
                backup: backupApi,
                device: deviceApi,
                notifications: notificationsApi,
                revocation: revocationApi,
                history: historyApi,
                httpClient: httpClient,
                bundleSyncManager: syncMgr,
                logger: _logger,
                internalVault: vaultImpl,
                internalSsdidClient: ssdidClient,
                internalKeychainManager: keychain,
                internalStorage: storage,
                internalClassicalProvider: classical,
                internalPqcProvider: pqc,
                internalBackupManager: backupMgr,
                internalRevocationManager: revocationMgr,
                internalNotifyManager: notifyMgr,
                internalLocalNotificationStorage: localNotifStorage,
                internalTtlProvider: ttl,
                internalBundleStore: fileBundleStore,
                internalCredentialRepository: fileCredentialRepo,
                internalConnectivityMonitor: connectivityMon,
                internalOfflineVerifier: offlineVerifierImpl,
                internalVerificationOrchestrator: verificationOrc,
                internalBundleManager: bundleMgr,
                internalActivityRepo: activityRepo
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
