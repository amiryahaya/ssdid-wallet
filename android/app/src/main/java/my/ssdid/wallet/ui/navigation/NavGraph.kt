package my.ssdid.wallet.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import my.ssdid.wallet.feature.profile.EmailVerificationScreen
import my.ssdid.wallet.feature.recovery.InstitutionalSetupScreen
import my.ssdid.wallet.feature.recovery.RecoveryRestoreScreen
import my.ssdid.wallet.feature.recovery.RecoverySetupScreen
import my.ssdid.wallet.feature.recovery.SocialRecoveryRestoreScreen
import my.ssdid.wallet.feature.recovery.SocialRecoverySetupScreen
import my.ssdid.wallet.feature.registration.RegistrationScreen
import my.ssdid.wallet.feature.rotation.KeyRotationScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.feature.auth.DriveLoginScreen
import my.ssdid.wallet.feature.invite.InviteAcceptScreen
import my.ssdid.wallet.feature.presentation.PresentationRequestScreen
import my.ssdid.wallet.feature.scan.ScanQrScreen
import my.ssdid.wallet.feature.settings.SettingsScreen
import my.ssdid.wallet.feature.notifications.NotificationsScreen
import my.ssdid.wallet.feature.transaction.TxSigningScreen
import my.ssdid.wallet.feature.verification.VerificationResultScreen
import my.ssdid.wallet.feature.verification.VerificationResultViewModel
import my.ssdid.wallet.feature.bundles.BundleManagementScreen

@Composable
fun SsdidNavGraph(navController: NavHostController, startDestination: String, onOnboardingCompleted: () -> Unit = {}) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.CreateIdentity.createRoute()) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onRestore = {
                    navController.navigate(Screen.RecoveryRestore.route)
                }
            )
        }
composable(
            Screen.EmailVerification.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            EmailVerificationScreen(
                onVerified = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
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
                onSettings = { navController.navigate(Screen.Settings.route) },
                onNotifications = { navController.navigate(Screen.Notifications.route) }
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
                            val claims = payload.resolvedClaims
                            if (claims.isNotEmpty()) {
                                val claimsJson = Json.encodeToString(claims)
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
                                navController.navigate(Screen.AuthFlow.createRoute(payload.serverUrl, payload.callbackUrl))
                            }
                        }
                        "login" -> {
                            val url = payload.serviceUrl.ifBlank { payload.serverUrl }
                            val claimsJson = if (payload.resolvedClaims.isNotEmpty())
                                Json.encodeToString(payload.resolvedClaims) else ""
                            navController.navigate(Screen.DriveLogin.createRoute(
                                serviceUrl = url,
                                serviceName = payload.serviceName,
                                challengeId = payload.challengeId,
                                requestedClaims = claimsJson
                            ))
                        }
                        "sign" -> navController.navigate(Screen.TxSigning.createRoute(payload.serverUrl, payload.sessionToken))
                        "credential-offer" -> navController.navigate(Screen.CredentialOffer.createRoute(payload.issuerUrl, payload.offerId))
                        "presentation-request" -> navController.navigate(Screen.PresentationRequest.createRoute(payload.serverUrl))
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
            Screen.DriveLogin.route,
            arguments = listOf(
                navArgument("serviceUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("serviceName") { type = NavType.StringType; defaultValue = "" },
                navArgument("challengeId") { type = NavType.StringType; defaultValue = "" },
                navArgument("requestedClaims") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            DriveLoginScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                },
                // H2: Pass accepted algorithms (empty for Drive), not serviceUrl
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
                onBack = { navController.popBackStack() },
                onVerify = { id -> navController.navigate(Screen.VerificationResult.createRoute(id)) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBackupExport = { navController.navigate(Screen.BackupExport.route) },
                onBundleManagement = { navController.navigate(Screen.BundleManagement.route) }
            )
        }
composable(Screen.TxHistory.route) {
            TxHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
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
        composable(
            route = Screen.InviteAccept.route,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("token") { type = NavType.StringType; defaultValue = "" },
                navArgument("callbackUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            InviteAcceptScreen()
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
        composable(
            Screen.PresentationRequest.route,
            arguments = listOf(
                navArgument("rawUri") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PresentationRequestScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.popBackStack(Screen.WalletHome.route, inclusive = false)
                }
            )
        }
        composable(
            Screen.VerificationResult.route,
            arguments = listOf(navArgument("credentialId") { type = NavType.StringType })
        ) { backStackEntry ->
            val credentialId = backStackEntry.arguments?.getString("credentialId") ?: return@composable
            val viewModel: VerificationResultViewModel = hiltViewModel()
            val result by viewModel.result.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()

            LaunchedEffect(credentialId) {
                viewModel.verifyById(credentialId)
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                result?.let { res ->
                    VerificationResultScreen(result = res, onBack = { navController.popBackStack() })
                }
            }
        }
        composable(Screen.BundleManagement.route) {
            BundleManagementScreen(
                onBack = { navController.popBackStack() },
                onScanCredential = { navController.navigate(Screen.ScanQr.route) }
            )
        }
    }
}
