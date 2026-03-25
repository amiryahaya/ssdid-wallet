import Foundation
import BackgroundTasks

/// Schedules and executes background refresh of verification bundles via BGAppRefreshTask.
///
/// Register `BundleSyncManager.taskIdentifier` in Info.plist under
/// `BGTaskSchedulerPermittedIdentifiers`, and call `registerBackgroundTask()` early
/// in the app lifecycle (before the first `applicationDidBecomeActive`).
final class BundleSyncManager {
    static let taskIdentifier = "my.ssdid.wallet.bundleSync"

    private let bundleStore: BundleStore
    private let bundleFetcher: BundleFetcher
    private let credentialRepository: CredentialRepository
    private let ttlProvider: TtlProvider

    init(
        bundleStore: BundleStore,
        bundleFetcher: BundleFetcher,
        credentialRepository: CredentialRepository,
        ttlProvider: TtlProvider = TtlProvider()
    ) {
        self.bundleStore = bundleStore
        self.bundleFetcher = bundleFetcher
        self.credentialRepository = credentialRepository
        self.ttlProvider = ttlProvider
    }

    // MARK: - Background Task Registration

    /// Register the BGAppRefreshTask handler. Must be called before the app finishes launching.
    func registerBackgroundTask() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.taskIdentifier,
            using: nil
        ) { [weak self] task in
            guard let self, let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.handleBackgroundTask(refreshTask)
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
            guard let bundle = await bundleStore.getBundle(issuerDid: did) else {
                // No cached bundle — fetch one proactively.
                _ = await bundleFetcher.fetchAndCache(issuerDid: did)
                continue
            }
            if ttlProvider.isExpired(fetchedAt: bundle.fetchedAt) {
                _ = await bundleFetcher.fetchAndCache(issuerDid: did)
            }
        }
    }

    // MARK: - Private

    private func handleBackgroundTask(_ task: BGAppRefreshTask) {
        // Schedule the next refresh before doing any work so the chain continues
        // even if this run expires early.
        scheduleBackgroundSync()

        let syncTask = Task {
            await syncNow()
        }

        task.expirationHandler = {
            syncTask.cancel()
        }

        Task {
            await syncTask.value
            task.setTaskCompleted(success: true)
        }
    }
}
