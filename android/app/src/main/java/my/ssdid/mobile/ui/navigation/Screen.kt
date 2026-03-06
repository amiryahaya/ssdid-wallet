package my.ssdid.mobile.ui.navigation

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
    object Settings : Screen("settings")
    object TxHistory : Screen("tx_history")
}
