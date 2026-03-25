import Foundation
import Network
import Combine

/// Observes network reachability using NWPathMonitor and publishes the current
/// online/offline state as a Combine `@Published` property.
final class ConnectivityMonitor: ObservableObject {
    @Published var isOnline: Bool = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "my.ssdid.wallet.connectivity.monitor")

    init() {
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
