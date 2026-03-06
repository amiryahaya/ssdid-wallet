package my.ssdid.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import my.ssdid.mobile.feature.auth.AuthFlowScreen
import my.ssdid.mobile.feature.credentials.CredentialDetailScreen
import my.ssdid.mobile.feature.credentials.CredentialsScreen
import my.ssdid.mobile.feature.history.TxHistoryScreen
import my.ssdid.mobile.feature.identity.CreateIdentityScreen
import my.ssdid.mobile.feature.identity.IdentityDetailScreen
import my.ssdid.mobile.feature.identity.WalletHomeScreen
import my.ssdid.mobile.feature.onboarding.BiometricSetupScreen
import my.ssdid.mobile.feature.onboarding.OnboardingScreen
import my.ssdid.mobile.feature.registration.RegistrationScreen
import my.ssdid.mobile.feature.scan.ScanQrScreen
import my.ssdid.mobile.feature.settings.SettingsScreen
import my.ssdid.mobile.feature.transaction.TxSigningScreen

@Composable
fun SsdidNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Onboarding.route) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.CreateIdentity.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.BiometricSetup.route) {
            BiometricSetupScreen(
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.BiometricSetup.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.BiometricSetup.route) { inclusive = true }
                    }
                }
            )
        }
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
                onCreated = {
                    navController.navigate(Screen.BiometricSetup.route) {
                        popUpTo(Screen.CreateIdentity.route) { inclusive = true }
                    }
                }
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
        composable(Screen.ScanQr.route) {
            ScanQrScreen(
                onBack = { navController.popBackStack() },
                onScanned = { serverUrl, serverDid, action, sessionToken ->
                    when (action) {
                        "register" -> navController.navigate(Screen.Registration.createRoute(serverUrl, serverDid))
                        "authenticate" -> navController.navigate(Screen.AuthFlow.createRoute(serverUrl))
                        "sign" -> navController.navigate(Screen.TxSigning.createRoute(serverUrl, sessionToken))
                    }
                }
            )
        }
        composable(
            Screen.Registration.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("serverDid") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            RegistrationScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                }
            )
        }
        composable(
            Screen.AuthFlow.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            AuthFlowScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                }
            )
        }
        composable(
            Screen.TxSigning.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("sessionToken") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            TxSigningScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                }
            )
        }
        composable(Screen.Credentials.route) {
            CredentialsScreen(
                onBack = { navController.popBackStack() },
                onCredentialClick = { id -> navController.navigate(Screen.CredentialDetail.createRoute(id)) }
            )
        }
        composable(Screen.CredentialDetail.route) { backStackEntry ->
            val credentialId = backStackEntry.arguments?.getString("credentialId") ?: return@composable
            CredentialDetailScreen(
                credentialId = credentialId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.TxHistory.route) {
            TxHistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
