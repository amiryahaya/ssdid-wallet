@preconcurrency import Foundation
@preconcurrency import BackgroundTasks

/// Schedules and executes background refresh of verification bundles via BGAppRefreshTask.
///
/// Register `BundleSyncManager.taskIdentifier` in Info.plist under
/// `BGTaskSchedulerPermittedIdentifiers`, and call `registerBackgroundTask()` early
/// in the app lifecycle (before the first `applicationDidBecomeActive`).
final class BundleSyncManager: @unchecked Sendable {
    static let taskIdentifier = "my.ssdid.wallet.bundleSync"

    private let bundleStore: BundleStore
    private let bundleManager: BundleManager
    private let credentialRepository: CredentialRepository
    private let ttlProvider: TtlProvider

    init(
        bundleStore: BundleStore,
        bundleManager: BundleManager,
        credentialRepository: CredentialRepository,
        ttlProvider: TtlProvider = TtlProvider()
    ) {
        self.bundleStore = bundleStore
        self.bundleManager = bundleManager
        self.credentialRepository = credentialRepository
        self.ttlProvider = ttlProvider
    }

    // MARK: - Background Task Registration

    /// Register the BGAppRefreshTask handler with a caller-supplied closure.
    /// Must be called before `applicationDidBecomeActive` fires (i.e. from AppDelegate
    /// `didFinishLaunchingWithOptions`). BGTaskScheduler enforces this constraint.
    static func registerHandler(handler: @escaping (BGAppRefreshTask) -> Void) {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handler(refreshTask)
        }
    }

    /// Instance-level convenience: register this instance as the background task handler.
    func registerBackgroundTask() {
        BundleSyncManager.registerHandler { [weak self] task in
            guard let self else {
                task.setTaskCompleted(success: false)
                return
            }
            self.handleBackgroundTask(task)
        }
    }

    /// Submit a BGAppRefreshTaskRequest to run approximately 12 hours from now.
    func scheduleBackgroundSync() {
        let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: - Sync Logic

    /// Refresh all bundles whose TTL has expired, based on the issuer DIDs of held credentials.
    func syncNow() async {
        let issuerDids = await credentialRepository.getUniqueIssuerDids()
        for did in issuerDids {
            guard !Task.isCancelled else { return }
            guard let bundle = await bundleStore.getBundle(issuerDid: did) else {
                // No cached bundle — fetch one proactively.
                _ = await bundleManager.prefetchBundle(issuerDid: did)
                continue
            }
            if ttlProvider.isExpired(fetchedAt: bundle.fetchedAt) {
                _ = await bundleManager.prefetchBundle(
                    issuerDid: did,
                    statusListUrl: bundle.statusList?.id
                )
            }
        }
    }

    // MARK: - Internal

    func handleBackgroundTask(_ task: BGAppRefreshTask) {
        // Schedule the next refresh before doing any work so the chain continues
        // even if this run expires early.
        scheduleBackgroundSync()

        let syncTask = Task { [weak self] in
            await self?.syncNow()
        }

        // Wire expiration handler before starting any awaiting so it is guaranteed
        // to fire even if the system expires the task immediately.
        task.expirationHandler = {
            syncTask.cancel()
        }

        nonisolated(unsafe) let bgTask = task
        nonisolated(unsafe) let sync = syncTask
        Task.detached {
            _ = await sync.result
            bgTask.setTaskCompleted(success: !sync.isCancelled)
        }
    }
}
