package my.ssdid.wallet.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object CreateIdentity : Screen("create_identity")
    object BiometricSetup : Screen("biometric_setup")
    object WalletHome : Screen("wallet_home")
    object IdentityDetail : Screen("identity_detail/{keyId}") {
        fun createRoute(keyId: String) = "identity_detail/${Uri.encode(keyId)}"
    }
    object ScanQr : Screen("scan_qr")
    object Registration : Screen("registration?serverUrl={serverUrl}&serverDid={serverDid}") {
        fun createRoute(serverUrl: String, serverDid: String) =
            "registration?serverUrl=${Uri.encode(serverUrl)}&serverDid=${Uri.encode(serverDid)}"
    }
    object AuthFlow : Screen("auth_flow?serverUrl={serverUrl}") {
        fun createRoute(serverUrl: String) = "auth_flow?serverUrl=${Uri.encode(serverUrl)}"
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
    object BackupExport : Screen("backup_export")
    object RecoveryRestore : Screen("recovery_restore")
    object DeviceManagement : Screen("device_management/{keyId}") {
        fun createRoute(keyId: String) = "device_management/${Uri.encode(keyId)}"
    }
    object DeviceEnroll : Screen("device_enroll/{keyId}?mode={mode}") {
        fun createRoute(keyId: String, mode: String) =
            "device_enroll/${Uri.encode(keyId)}?mode=$mode"
    }
}
