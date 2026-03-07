package my.ssdid.wallet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import my.ssdid.wallet.feature.auth.AuthFlowScreen
import my.ssdid.wallet.feature.backup.BackupScreen
import my.ssdid.wallet.feature.credentials.CredentialDetailScreen
import my.ssdid.wallet.feature.credentials.CredentialsScreen
import my.ssdid.wallet.feature.device.DeviceManagementScreen
import my.ssdid.wallet.feature.history.TxHistoryScreen
import my.ssdid.wallet.feature.identity.CreateIdentityScreen
import my.ssdid.wallet.feature.identity.IdentityDetailScreen
import my.ssdid.wallet.feature.identity.WalletHomeScreen
import my.ssdid.wallet.feature.onboarding.BiometricSetupScreen
import my.ssdid.wallet.feature.onboarding.OnboardingScreen
import my.ssdid.wallet.feature.recovery.RecoverySetupScreen
import my.ssdid.wallet.feature.registration.RegistrationScreen
import my.ssdid.wallet.feature.rotation.KeyRotationScreen
import my.ssdid.wallet.feature.scan.ScanQrScreen
import my.ssdid.wallet.feature.settings.SettingsScreen
import my.ssdid.wallet.feature.transaction.TxSigningScreen

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
                onCredentialClick = { id -> navController.navigate(Screen.CredentialDetail.createRoute(id)) },
                onRecoverySetup = { id -> navController.navigate(Screen.RecoverySetup.createRoute(id)) },
                onKeyRotation = { id -> navController.navigate(Screen.KeyRotation.createRoute(id)) },
                onDeviceManagement = { id -> navController.navigate(Screen.DeviceManagement.createRoute(id)) }
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
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBackupExport = { navController.navigate(Screen.BackupExport.route) }
            )
        }
        composable(Screen.TxHistory.route) {
            TxHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RecoverySetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            RecoverySetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.KeyRotation.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            KeyRotationScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.BackupExport.route) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.DeviceManagement.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            DeviceManagementScreen(onBack = { navController.popBackStack() })
        }
    }
}
