package my.ssdid.wallet.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object CreateIdentity : Screen("create_identity?acceptedAlgorithms={acceptedAlgorithms}") {
        fun createRoute(acceptedAlgorithms: String = ""): String =
            "create_identity?acceptedAlgorithms=${Uri.encode(acceptedAlgorithms)}"
    }
    object BiometricSetup : Screen("biometric_setup")
    object ProfileSetup : Screen("profile_setup")
    object ProfileEdit : Screen("profile_edit")
    object EmailVerification : Screen("email_verification?email={email}") {
        fun createRoute(email: String) = "email_verification?email=${Uri.encode(email)}"
    }
    object WalletHome : Screen("wallet_home")
    object IdentityDetail : Screen("identity_detail/{keyId}") {
        fun createRoute(keyId: String) = "identity_detail/${Uri.encode(keyId)}"
    }
    object ScanQr : Screen("scan_qr")
    object Registration : Screen("registration?serverUrl={serverUrl}&serverDid={serverDid}") {
        fun createRoute(serverUrl: String, serverDid: String) =
            "registration?serverUrl=${Uri.encode(serverUrl)}&serverDid=${Uri.encode(serverDid)}"
    }
    object AuthFlow : Screen("auth_flow?serverUrl={serverUrl}&callbackUrl={callbackUrl}") {
        fun createRoute(serverUrl: String, callbackUrl: String = "") =
            "auth_flow?serverUrl=${Uri.encode(serverUrl)}&callbackUrl=${Uri.encode(callbackUrl)}"
    }
    object Consent : Screen("consent?serverUrl={serverUrl}&callbackUrl={callbackUrl}&sessionId={sessionId}&requestedClaims={requestedClaims}&acceptedAlgorithms={acceptedAlgorithms}") {
        fun createRoute(
            serverUrl: String,
            callbackUrl: String = "",
            sessionId: String = "",
            requestedClaims: String = "",
            acceptedAlgorithms: String = ""
        ): String = "consent?serverUrl=${Uri.encode(serverUrl)}&callbackUrl=${Uri.encode(callbackUrl)}&sessionId=${Uri.encode(sessionId)}&requestedClaims=${Uri.encode(requestedClaims)}&acceptedAlgorithms=${Uri.encode(acceptedAlgorithms)}"
    }
    object DriveLogin : Screen("drive_login?serviceUrl={serviceUrl}&serviceName={serviceName}&challengeId={challengeId}&requestedClaims={requestedClaims}") {
        fun createRoute(
            serviceUrl: String,
            serviceName: String = "",
            challengeId: String = "",
            requestedClaims: String = ""
        ): String = "drive_login?serviceUrl=${Uri.encode(serviceUrl)}&serviceName=${Uri.encode(serviceName)}&challengeId=${Uri.encode(challengeId)}&requestedClaims=${Uri.encode(requestedClaims)}"
    }
    object TxSigning : Screen("tx_signing?serverUrl={serverUrl}&sessionToken={sessionToken}") {
        fun createRoute(serverUrl: String, sessionToken: String) =
            "tx_signing?serverUrl=${Uri.encode(serverUrl)}&sessionToken=${Uri.encode(sessionToken)}"
    }
    object Credentials : Screen("credentials")
    object CredentialDetail : Screen("credential_detail/{credentialId}") {
        fun createRoute(credentialId: String) = "credential_detail/${Uri.encode(credentialId)}"
    }
    object CredentialOffer : Screen("credential_offer?issuerUrl={issuerUrl}&offerId={offerId}") {
        fun createRoute(issuerUrl: String, offerId: String) =
            "credential_offer?issuerUrl=${Uri.encode(issuerUrl)}&offerId=${Uri.encode(offerId)}"
    }
    object Settings : Screen("settings")
    object TxHistory : Screen("tx_history")
    object RecoverySetup : Screen("recovery_setup/{keyId}") {
        fun createRoute(keyId: String) = "recovery_setup/${Uri.encode(keyId)}"
    }
    object KeyRotation : Screen("key_rotation/{keyId}") {
        fun createRoute(keyId: String) = "key_rotation/${Uri.encode(keyId)}"
    }
    object BackupExport : Screen("backup_export?restoreUri={restoreUri}") {
        fun createRoute(restoreUri: String = "") =
            "backup_export?restoreUri=${Uri.encode(restoreUri)}"
    }
    object RecoveryRestore : Screen("recovery_restore")
    object SocialRecoverySetup : Screen("social_recovery_setup/{keyId}") {
        fun createRoute(keyId: String) = "social_recovery_setup/${Uri.encode(keyId)}"
    }
    object InstitutionalSetup : Screen("institutional_setup/{keyId}") {
        fun createRoute(keyId: String) = "institutional_setup/${Uri.encode(keyId)}"
    }
    object SocialRecoveryRestore : Screen("social_recovery_restore")
    object DeviceManagement : Screen("device_management/{keyId}") {
        fun createRoute(keyId: String) = "device_management/${Uri.encode(keyId)}"
    }
    object DeviceEnroll : Screen("device_enroll/{keyId}?mode={mode}") {
        fun createRoute(keyId: String, mode: String) =
            "device_enroll/${Uri.encode(keyId)}?mode=${Uri.encode(mode)}"
    }
    object InviteAccept : Screen("invite_accept?serverUrl={serverUrl}&token={token}&callbackUrl={callbackUrl}") {
        fun createRoute(serverUrl: String, token: String, callbackUrl: String = "") =
            "invite_accept?serverUrl=${Uri.encode(serverUrl)}&token=${Uri.encode(token)}&callbackUrl=${Uri.encode(callbackUrl)}"
    }
}
