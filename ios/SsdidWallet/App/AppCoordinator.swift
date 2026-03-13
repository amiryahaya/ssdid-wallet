import Foundation
import SwiftUI

enum AppRoute {
    case onboarding
    case home
}

@MainActor
final class AppCoordinator: ObservableObject {
    private static let isOnboardedKey = "my.ssdid.wallet.isOnboarded"

    @Published var isOnboarded: Bool {
        didSet {
            UserDefaults.standard.set(isOnboarded, forKey: Self.isOnboardedKey)
        }
    }

    @Published var pendingDeepLink: URL?

    var initialRoute: AppRoute {
        isOnboarded ? .home : .onboarding
    }

    init() {
        self.isOnboarded = UserDefaults.standard.bool(forKey: Self.isOnboardedKey)
    }

    func handleDeepLink(_ url: URL) {
        guard url.scheme == "ssdid" else { return }
        pendingDeepLink = url
    }

    func completeOnboarding() {
        isOnboarded = true
    }

    func consumeDeepLink() -> URL? {
        let url = pendingDeepLink
        pendingDeepLink = nil
        return url
    }
}
