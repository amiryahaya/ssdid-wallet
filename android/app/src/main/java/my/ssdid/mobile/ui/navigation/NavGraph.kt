package my.ssdid.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import my.ssdid.mobile.feature.credentials.CredentialsScreen
import my.ssdid.mobile.feature.identity.CreateIdentityScreen
import my.ssdid.mobile.feature.identity.IdentityDetailScreen
import my.ssdid.mobile.feature.identity.WalletHomeScreen
import my.ssdid.mobile.feature.settings.SettingsScreen

@Composable
fun SsdidNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.WalletHome.route) {
        composable(Screen.WalletHome.route) {
            WalletHomeScreen(
                onCreateIdentity = { navController.navigate(Screen.CreateIdentity.route) },
                onIdentityClick = { keyId -> navController.navigate(Screen.IdentityDetail.createRoute(keyId)) },
                onScanQr = { navController.navigate(Screen.ScanQr.route) },
                onCredentials = { navController.navigate(Screen.Credentials.route) },
                onHistory = { navController.navigate(Screen.TxHistory.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.CreateIdentity.route) {
            CreateIdentityScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }
        composable(Screen.IdentityDetail.route) { backStackEntry ->
            val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
            IdentityDetailScreen(
                keyId = keyId,
                onBack = { navController.popBackStack() },
                onCredentialClick = { id -> navController.navigate(Screen.CredentialDetail.createRoute(id)) }
            )
        }
        composable(Screen.Credentials.route) {
            CredentialsScreen(
                onBack = { navController.popBackStack() },
                onCredentialClick = { id -> navController.navigate(Screen.CredentialDetail.createRoute(id)) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
