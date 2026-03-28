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

    // MARK: - Internal Services (kept for backward compatibility)

    /// The HTTP client used for all network operations.
    let httpClient: SsdidHttpClient

    /// Bundle sync manager for background refresh.
    let bundleSyncManager: BundleSyncManager

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
        logger: SsdidLogger
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
        self.httpClient = httpClient
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
