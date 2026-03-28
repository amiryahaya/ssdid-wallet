import SwiftUI
import SsdidCore

// MARK: - Route

enum Route: Hashable {
    case onboarding
    case createIdentity(acceptedAlgorithms: String? = nil)
    case biometricSetup
    case emailVerification(email: String, isEditing: Bool = false)
    case walletHome
    case identityDetail(keyId: String)
    case scanQr
    case registration(serverUrl: String, serverDid: String)
    case authFlow(serverUrl: String, callbackUrl: String, state: String? = nil)
    case consent(
        serverUrl: String,
        callbackUrl: String,
        sessionId: String,
        requestedClaims: String,
        acceptedAlgorithms: String? = nil,
        state: String? = nil
    )
    case driveLogin(
        serviceUrl: String,
        serviceName: String,
        challengeId: String,
        callbackUrl: String,
        requestedClaims: String,
        inviteCode: String? = nil,
        state: String? = nil
    )
    case txSigning(serverUrl: String, sessionToken: String)
    case credentials
    case credentialDetail(credentialId: String)
    case credentialOffer(issuerUrl: String, offerId: String)
    case settings
    case txHistory
    case notifications
    case recoverySetup(keyId: String)
    case keyRotation(keyId: String)
    case backupExport(restoreUri: String? = nil)
    case recoveryRestore
    case socialRecoverySetup(keyId: String)
    case institutionalSetup(keyId: String)
    case socialRecoveryRestore
    case deviceManagement(keyId: String)
    case deviceEnroll(keyId: String, mode: String)
    case inviteAccept(serverUrl: String, token: String, callbackUrl: String, state: String? = nil)
    case presentationRequest(rawUri: String)
    case verificationResult(result: UnifiedVerificationResult)
    case bundleManagement
}

// MARK: - AppRouter

@Observable
final class AppRouter {
    var navigationPath = NavigationPath()
    var hasCompletedOnboarding = false

    var startRoute: Route {
        hasCompletedOnboarding ? .walletHome : .onboarding
    }

    func push(_ route: Route) {
        navigationPath.append(route)
    }

    func pop() {
        guard !navigationPath.isEmpty else { return }
        navigationPath.removeLast()
    }

    func pop(count: Int) {
        let removeCount = min(count, navigationPath.count)
        navigationPath.removeLast(removeCount)
    }

    func reset(to route: Route) {
        navigationPath = NavigationPath()
        navigationPath.append(route)
    }

    func resetToRoot() {
        navigationPath = NavigationPath()
    }
}
