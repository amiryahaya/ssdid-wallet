package my.ssdid.wallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.deeplink.DeepLinkHandler
import my.ssdid.wallet.ui.navigation.Screen
import my.ssdid.wallet.ui.navigation.SsdidNavGraph
import my.ssdid.wallet.ui.theme.SsdidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var vaultStorage: VaultStorage

    private val pendingDeepLinks = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SsdidTheme {
                var startDestination by mutableStateOf<String?>(null)

                LaunchedEffect(Unit) {
                    startDestination = if (vaultStorage.isOnboardingCompleted()) {
                        Screen.WalletHome.route
                    } else {
                        Screen.Onboarding.route
                    }
                }

                if (startDestination != null) {
                    val navController = rememberNavController()
                    SsdidNavGraph(navController = navController, startDestination = startDestination!!)

                    LaunchedEffect(Unit) {
                        // Handle cold start deep link
                        if (savedInstanceState == null) {
                            intent?.data?.let { handleDeepLink(intent, navController) }
                        }
                        // Handle warm start deep links via flow
                        pendingDeepLinks.collectLatest { newIntent ->
                            handleDeepLink(newIntent, navController)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            pendingDeepLinks.tryEmit(intent)
        }
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
        intent.data = null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
