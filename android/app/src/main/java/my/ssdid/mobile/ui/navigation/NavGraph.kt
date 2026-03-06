package my.ssdid.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import java.net.URLDecoder
import java.net.URLEncoder

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
                onScanned = { serverUrl, serverDid, action ->
                    val encodedUrl = URLEncoder.encode(serverUrl, "UTF-8")
                    val encodedDid = URLEncoder.encode(serverDid, "UTF-8")
                    when (action) {
                        "register" -> navController.navigate(Screen.Registration.createRoute(encodedUrl, encodedDid))
                        "auth" -> navController.navigate(Screen.AuthFlow.createRoute(encodedUrl))
                        "tx" -> navController.navigate(Screen.TxSigning.createRoute(encodedUrl, ""))
                    }
                }
            )
        }
        composable(Screen.Registration.route) { backStackEntry ->
            val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: return@composable
            val serverDid = backStackEntry.arguments?.getString("serverDid") ?: return@composable
            RegistrationScreen(
                serverUrl = serverUrl,
                serverDid = serverDid,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.WalletHome.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.AuthFlow.route) { backStackEntry ->
            val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: return@composable
            AuthFlowScreen(
                serverUrl = serverUrl,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.WalletHome.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.TxSigning.route) { backStackEntry ->
            val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: return@composable
            val sessionToken = backStackEntry.arguments?.getString("sessionToken") ?: return@composable
            TxSigningScreen(
                serverUrl = serverUrl,
                sessionToken = sessionToken,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.WalletHome.route) {
                        popUpTo(Screen.WalletHome.route) { inclusive = true }
                    }
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
