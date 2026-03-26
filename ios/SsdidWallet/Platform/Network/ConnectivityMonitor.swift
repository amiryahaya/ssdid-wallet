import Foundation
import Network
import Combine

/// Observes network reachability using NWPathMonitor and publishes the current
/// online/offline state as a Combine `@Published` property.
final class ConnectivityMonitor: ObservableObject, @unchecked Sendable {
    @Published var isOnline: Bool = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "my.ssdid.wallet.connectivity.monitor")

    init() {
        // Support UI-test offline simulation: when the test harness passes
        // --simulate-offline, stay permanently offline without starting the monitor.
        if ProcessInfo.processInfo.arguments.contains("--simulate-offline") {
            isOnline = false
            return
        }

        // Seed from the current path so the initial state reflects real connectivity
        // rather than assuming online until the first pathUpdateHandler fires.
        isOnline = monitor.currentPath.status == .satisfied
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.isOnline = path.status == .satisfied
            }
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    /// Synchronous convenience accessor for the current connectivity state.
    func isCurrentlyOnline() -> Bool {
        return isOnline
    }
}
