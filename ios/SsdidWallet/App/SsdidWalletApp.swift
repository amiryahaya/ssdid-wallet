import SwiftUI
import SentrySwiftUI
import UserNotifications

// MARK: - AppDelegate Adaptor

/// Bridges UIApplicationDelegate callbacks that have no SwiftUI equivalent —
/// specifically the APNs device token delivery and registration failure.
final class AppDelegate: NSObject, UIApplicationDelegate {

    /// Shared reference set by SsdidWalletApp after services are ready.
    /// Setting this replays any token that arrived before services were initialized.
    var notifyManager: NotifyManager? {
        didSet {
            if let manager = notifyManager, let pending = pendingToken {
                pendingToken = nil
                Task { @MainActor in try? await manager.registerAPNsToken(pending) }
            }
        }
    }

    /// Buffers the APNs token if it arrives before notifyManager is set.
    private var pendingToken: Data?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard let manager = notifyManager else {
            pendingToken = deviceToken
            return
        }
        Task { @MainActor in try? await manager.registerAPNsToken(deviceToken) }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Registration failure is non-fatal; notifications are supplemental.
        // The app operates normally without push. Log only in debug builds.
        #if DEBUG
        print("[NotifyManager] APNs registration failed: \(error.localizedDescription)")
        #endif
    }

}

// MARK: - UNUserNotificationCenterDelegate

extension AppDelegate: @preconcurrency UNUserNotificationCenterDelegate {
    /// Called when a notification arrives while the app is in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}

// MARK: - App Entry Point

@main
struct SsdidWalletApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var coordinator = AppCoordinator()
    @StateObject private var services = ServiceContainer()

    init() {
        SentryManager.start()
    }

    var body: some Scene {
        WindowGroup {
            SentryTracedView("RootView") {
                RootView()
                    .environmentObject(coordinator)
                    .environmentObject(services)
                    .preferredColorScheme(.dark)
                    .onOpenURL { url in
                        coordinator.handleDeepLink(url)
                    }
                    .task {
                        // Wire the delegate to the live NotifyManager before
                        // requesting permission, so the token callback is handled.
                        appDelegate.notifyManager = services.notifyManager

                        // Wire identity provider so fetchAndDemux can resolve mailboxes.
                        services.notifyManager.identityProvider = { [weak services] in
                            guard let services = services else { return [] }
                            return await services.vault.listIdentities()
                        }

                        // Ensure the inbox exists before we request APNs permission.
                        try? await services.notifyManager.ensureInboxRegistered()

                        // Request notification authorization and register for APNs.
                        let center = UNUserNotificationCenter.current()
                        let granted = try? await center.requestAuthorization(
                            options: [.alert, .sound, .badge]
                        )
                        if granted == true {
                            await MainActor.run {
                                UIApplication.shared.registerForRemoteNotifications()
                            }
                        }
                    }
                    .onChange(of: scenePhase) { _, newPhase in
                        if newPhase == .active {
                            Task {
                                try? await services.notifyManager.fetchAndDemux()
                            }
                        }
                    }
            }
        }
    }
}
