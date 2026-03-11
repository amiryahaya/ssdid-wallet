package my.ssdid.wallet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import my.ssdid.wallet.feature.auth.AuthFlowScreen
import my.ssdid.wallet.feature.auth.ConsentScreen
import my.ssdid.wallet.feature.backup.BackupScreen
import my.ssdid.wallet.feature.credentials.CredentialDetailScreen
import my.ssdid.wallet.feature.credentials.CredentialOfferScreen
import my.ssdid.wallet.feature.credentials.CredentialsScreen
import my.ssdid.wallet.feature.device.DeviceEnrollScreen
import my.ssdid.wallet.feature.device.DeviceManagementScreen
import my.ssdid.wallet.feature.history.TxHistoryScreen
import my.ssdid.wallet.feature.identity.CreateIdentityScreen
import my.ssdid.wallet.feature.identity.IdentityDetailScreen
import my.ssdid.wallet.feature.identity.WalletHomeScreen
import my.ssdid.wallet.feature.onboarding.BiometricSetupScreen
import my.ssdid.wallet.feature.onboarding.OnboardingScreen
import my.ssdid.wallet.feature.profile.ProfileSetupScreen
import my.ssdid.wallet.feature.recovery.InstitutionalSetupScreen
import my.ssdid.wallet.feature.recovery.RecoveryRestoreScreen
import my.ssdid.wallet.feature.recovery.RecoverySetupScreen
import my.ssdid.wallet.feature.recovery.SocialRecoveryRestoreScreen
import my.ssdid.wallet.feature.recovery.SocialRecoverySetupScreen
import my.ssdid.wallet.feature.registration.RegistrationScreen
import my.ssdid.wallet.feature.rotation.KeyRotationScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.feature.scan.ScanQrScreen
import my.ssdid.wallet.feature.settings.SettingsScreen
import my.ssdid.wallet.feature.transaction.TxSigningScreen

@Composable
fun SsdidNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onRestore = {
                    navController.navigate(Screen.RecoveryRestore.route)
                }
            )
        }
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onComplete = {
                    navController.navigate(Screen.CreateIdentity.createRoute()) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
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
                onCreateIdentity = { navController.navigate(Screen.CreateIdentity.createRoute()) },
                onIdentityClick = { keyId -> navController.navigate(Screen.IdentityDetail.createRoute(keyId)) },
                onScanQr = { navController.navigate(Screen.ScanQr.route) },
                onCredentials = { navController.navigate(Screen.Credentials.route) },
                onHistory = { navController.navigate(Screen.TxHistory.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            route = Screen.CreateIdentity.route,
            arguments = listOf(
                navArgument("acceptedAlgorithms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
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
                onScanned = { payload ->
                    when (payload.action) {
                        "register" -> navController.navigate(Screen.Registration.createRoute(payload.serverUrl, payload.serverDid))
                        "authenticate" -> {
                            if (payload.requestedClaims.isNotEmpty()) {
                                val claimsJson = Json.encodeToString(payload.requestedClaims)
                                val algosJson = if (payload.acceptedAlgorithms.isNotEmpty())
                                    Json.encodeToString(payload.acceptedAlgorithms) else ""
                                navController.navigate(Screen.Consent.createRoute(
                                    serverUrl = payload.serverUrl,
                                    callbackUrl = payload.callbackUrl,
                                    sessionId = payload.sessionId,
                                    requestedClaims = claimsJson,
                                    acceptedAlgorithms = algosJson
                                ))
                            } else {
                                navController.navigate(Screen.AuthFlow.createRoute(payload.serverUrl))
                            }
                        }
                        "sign" -> navController.navigate(Screen.TxSigning.createRoute(payload.serverUrl, payload.sessionToken))
                        "credential-offer" -> navController.navigate(Screen.CredentialOffer.createRoute(payload.issuerUrl, payload.offerId))
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
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("callbackUrl") { type = NavType.StringType; defaultValue = "" }
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
            route = Screen.Consent.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("callbackUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("requestedClaims") { type = NavType.StringType; defaultValue = "" },
                navArgument("acceptedAlgorithms") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            ConsentScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                },
                onCreateIdentity = { acceptedAlgos ->
                    navController.navigate(Screen.CreateIdentity.createRoute(acceptedAlgos))
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
        composable(
            Screen.CredentialOffer.route,
            arguments = listOf(
                navArgument("issuerUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("offerId") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            CredentialOfferScreen(
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
                onBackupExport = { navController.navigate(Screen.BackupExport.route) },
                onProfile = { navController.navigate(Screen.ProfileEdit.route) }
            )
        }
        composable(Screen.ProfileEdit.route) {
            ProfileSetupScreen(
                onComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                buttonText = "Save"
            )
        }
        composable(Screen.TxHistory.route) {
            TxHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RecoverySetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            RecoverySetupScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSocialSetup = { keyId ->
                    navController.navigate(Screen.SocialRecoverySetup.createRoute(keyId))
                },
                onNavigateToInstitutionalSetup = { keyId ->
                    navController.navigate(Screen.InstitutionalSetup.createRoute(keyId))
                }
            )
        }
        composable(Screen.KeyRotation.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            KeyRotationScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Screen.BackupExport.route,
            arguments = listOf(
                navArgument("restoreUri") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.DeviceManagement.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            DeviceManagementScreen(
                onBack = { navController.popBackStack() },
                onEnrollDevice = { keyId ->
                    navController.navigate(Screen.DeviceEnroll.createRoute(keyId, "primary"))
                }
            )
        }
        composable(
            Screen.DeviceEnroll.route,
            arguments = listOf(
                navArgument("keyId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = "primary" }
            )
        ) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            DeviceEnrollScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RecoveryRestore.route) {
            RecoveryRestoreScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.RecoveryRestore.route) { inclusive = true }
                    }
                },
                onNavigateToSocialRestore = {
                    navController.navigate(Screen.SocialRecoveryRestore.route)
                }
            )
        }
        composable(Screen.SocialRecoverySetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            SocialRecoverySetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.InstitutionalSetup.route) { backStackEntry ->
            backStackEntry.arguments?.getString("keyId") ?: return@composable
            InstitutionalSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SocialRecoveryRestore.route) {
            SocialRecoveryRestoreScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.SocialRecoveryRestore.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
