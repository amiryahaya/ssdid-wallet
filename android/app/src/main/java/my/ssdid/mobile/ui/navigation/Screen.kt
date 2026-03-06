package my.ssdid.mobile.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object CreateIdentity : Screen("create_identity")
    object BiometricSetup : Screen("biometric_setup")
    object WalletHome : Screen("wallet_home")
    object IdentityDetail : Screen("identity_detail/{keyId}") {
        fun createRoute(keyId: String) = "identity_detail/$keyId"
    }
    object ScanQr : Screen("scan_qr")
    object Registration : Screen("registration/{serverUrl}/{serverDid}") {
        fun createRoute(serverUrl: String, serverDid: String) = "registration/$serverUrl/$serverDid"
    }
    object AuthFlow : Screen("auth_flow/{serverUrl}") {
        fun createRoute(serverUrl: String) = "auth_flow/$serverUrl"
    }
    object TxSigning : Screen("tx_signing/{serverUrl}/{sessionToken}") {
        fun createRoute(serverUrl: String, sessionToken: String) = "tx_signing/$serverUrl/$sessionToken"
    }
    object Credentials : Screen("credentials")
    object CredentialDetail : Screen("credential_detail/{credentialId}") {
        fun createRoute(credentialId: String) = "credential_detail/$credentialId"
    }
    object Settings : Screen("settings")
    object TxHistory : Screen("tx_history")
}
