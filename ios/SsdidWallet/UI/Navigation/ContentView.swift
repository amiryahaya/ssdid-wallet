import SwiftUI

struct RootView: View {
    @EnvironmentObject var coordinator: AppCoordinator
    @State private var router = AppRouter()

    var body: some View {
        NavigationStack(path: $router.navigationPath) {
            Group {
                if coordinator.isOnboarded {
                    routeDestination(for: .walletHome)
                } else {
                    routeDestination(for: .onboarding)
                }
            }
            .navigationDestination(for: Route.self) { route in
                routeDestination(for: route)
                    .navigationBarBackButtonHidden(true)
            }
            .navigationBarBackButtonHidden(true)
        }
        .environment(router)
        .ssdidTheme()
        .onChange(of: coordinator.pendingDeepLink) { _, newURL in
            guard let url = newURL else { return }
            _ = coordinator.consumeDeepLink()
            routeDeepLink(url)
        }
    }

    /// Routes an incoming deep link URL to the appropriate screen.
    private func routeDeepLink(_ url: URL) {
        // Don't process deep links before onboarding is complete
        guard coordinator.isOnboarded else { return }

        let handler = DeepLinkHandler()
        guard let action = try? handler.parse(url: url) else { return }

        switch action {
        case .login(let serverUrl, let serviceName, let challengeId, let callbackUrl, let requestedClaims):
            router.push(.driveLogin(
                serviceUrl: serverUrl,
                serviceName: serviceName ?? "SSDID Drive",
                challengeId: challengeId ?? "",
                callbackUrl: callbackUrl,
                requestedClaims: requestedClaims ?? ""
            ))
        case .authenticate(let serverUrl, let callbackUrl, let sessionId, let requestedClaims, let acceptedAlgorithms):
            if sessionId != nil || requestedClaims != nil {
                router.push(.consent(
                    serverUrl: serverUrl,
                    callbackUrl: callbackUrl,
                    sessionId: sessionId ?? "",
                    requestedClaims: requestedClaims ?? "",
                    acceptedAlgorithms: acceptedAlgorithms
                ))
            } else {
                router.push(.authFlow(serverUrl: serverUrl, callbackUrl: callbackUrl))
            }
        case .register(let serverUrl, let serverDid):
            router.push(.registration(serverUrl: serverUrl, serverDid: serverDid ?? ""))
        case .sign(let serverUrl, let sessionToken):
            router.push(.txSigning(serverUrl: serverUrl, sessionToken: sessionToken))
        case .credentialOffer(let issuerUrl, let offerId):
            router.push(.credentialOffer(issuerUrl: issuerUrl, offerId: offerId))
        case .invite(let serverUrl, let token, let callbackUrl):
            router.push(.inviteAccept(serverUrl: serverUrl, token: token, callbackUrl: callbackUrl))
        }
    }

    @ViewBuilder
    private func routeDestination(for route: Route) -> some View {
        switch route {
        case .onboarding:
            OnboardingScreen()
        case .createIdentity(let acceptedAlgorithms):
            CreateIdentityScreen(acceptedAlgorithms: acceptedAlgorithms)
        case .biometricSetup:
            BiometricSetupScreen()
        case .profileSetup:
            ProfileSetupScreen(isEditing: false)
        case .profileEdit:
            ProfileSetupScreen(isEditing: true)
        case .emailVerification(let email, let isEditing):
            EmailVerificationScreen(email: email, isEditing: isEditing)
        case .walletHome:
            WalletHomeScreen()
        case .identityDetail(let keyId):
            IdentityDetailScreen(keyId: keyId)
        case .scanQr:
            ScanQrScreen()
        case .registration(let serverUrl, let serverDid):
            RegistrationScreen(serverUrl: serverUrl, serverDid: serverDid)
        case .authFlow(let serverUrl, let callbackUrl):
            AuthFlowScreen(serverUrl: serverUrl, callbackUrl: callbackUrl)
        case .consent(let serverUrl, let callbackUrl, let sessionId, let requestedClaims, let acceptedAlgorithms):
            ConsentScreen(serverUrl: serverUrl, callbackUrl: callbackUrl, sessionId: sessionId, requestedClaims: requestedClaims, acceptedAlgorithms: acceptedAlgorithms)
        case .driveLogin(let serviceUrl, let serviceName, let challengeId, let callbackUrl, let requestedClaims):
            DriveLoginScreen(serviceUrl: serviceUrl, serviceName: serviceName, challengeId: challengeId, callbackUrl: callbackUrl, requestedClaims: requestedClaims)
        case .txSigning(let serverUrl, let sessionToken):
            TxSigningScreen(serverUrl: serverUrl, sessionToken: sessionToken)
        case .credentials:
            CredentialsScreen()
        case .credentialDetail(let credentialId):
            CredentialDetailScreen(credentialId: credentialId)
        case .credentialOffer(let issuerUrl, let offerId):
            CredentialOfferScreen(issuerUrl: issuerUrl, offerId: offerId)
        case .settings:
            SettingsScreen()
        case .txHistory:
            TxHistoryScreen()
        case .recoverySetup(let keyId):
            RecoverySetupScreen(keyId: keyId)
        case .keyRotation(let keyId):
            KeyRotationScreen(keyId: keyId)
        case .backupExport(let restoreUri):
            BackupScreen(restoreUri: restoreUri)
        case .recoveryRestore:
            RecoveryRestoreScreen()
        case .socialRecoverySetup(let keyId):
            SocialRecoverySetupScreen(keyId: keyId)
        case .institutionalSetup(let keyId):
            InstitutionalSetupScreen(keyId: keyId)
        case .socialRecoveryRestore:
            SocialRecoveryRestoreScreen()
        case .deviceManagement(let keyId):
            DeviceManagementScreen(keyId: keyId)
        case .deviceEnroll(let keyId, let mode):
            DeviceEnrollScreen(keyId: keyId, mode: mode)
        case .inviteAccept(let serverUrl, let token, let callbackUrl):
            InviteAcceptScreen(serverUrl: serverUrl, token: token, callbackUrl: callbackUrl)
        }
    }
}
