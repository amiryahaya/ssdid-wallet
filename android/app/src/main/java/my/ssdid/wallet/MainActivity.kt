package my.ssdid.wallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.feature.splash.SplashScreen
import my.ssdid.wallet.platform.deeplink.DeepLinkHandler
import my.ssdid.wallet.ui.navigation.Screen
import my.ssdid.wallet.ui.navigation.SsdidNavGraph
import my.ssdid.wallet.ui.theme.SsdidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var vaultStorage: VaultStorage

    private val pendingDeepLinks = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SsdidTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    startDestination = if (vaultStorage.isOnboardingCompleted()) {
                        Screen.WalletHome.route
                    } else {
                        Screen.Onboarding.route
                    }
                    delay(1500)
                    showSplash = false
                }

                AnimatedVisibility(
                    visible = showSplash,
                    exit = fadeOut(animationSpec = tween(400))
                ) {
                    SplashScreen()
                }

                if (!showSplash && startDestination != null) {
                    val navController = rememberNavController()
                    SsdidNavGraph(navController = navController, startDestination = startDestination!!)

                    LaunchedEffect(Unit) {
                        // Handle cold start deep link or share intent
                        if (savedInstanceState == null) {
                            handleIntent(intent, navController)
                        }
                        // Handle warm start deep links via flow
                        pendingDeepLinks.collectLatest { newIntent ->
                            handleIntent(newIntent, navController)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            intent.data != null -> pendingDeepLinks.tryEmit(intent)
            intent.action == Intent.ACTION_SEND -> pendingDeepLinks.tryEmit(intent)
        }
    }

    private fun handleIntent(intent: Intent, navController: NavHostController) {
        if (intent.action == Intent.ACTION_SEND) {
            handleShareIntent(intent, navController)
            return
        }
        handleDeepLink(intent, navController)
    }

    private fun handleShareIntent(intent: Intent, navController: NavHostController) {
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return
        if (uri.scheme != "content") {
            Log.w(TAG, "Rejected share intent with non-content URI scheme: ${uri.scheme}")
            return
        }
        navController.navigate(Screen.BackupExport.createRoute(uri.toString()))
        setIntent(Intent())
    }

    private fun handleDeepLink(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: return
        val deepLinkAction = DeepLinkHandler.parse(uri) ?: run {
            Log.w(TAG, "Unrecognized deep link URI: $uri")
            return
        }
        val route = deepLinkAction.toNavRoute() ?: run {
            Log.w(TAG, "Unknown deep link action: ${deepLinkAction.action}")
            return
        }
        navController.navigate(route)
        setIntent(Intent())
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
