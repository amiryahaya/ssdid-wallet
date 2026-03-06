package my.ssdid.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import my.ssdid.mobile.platform.deeplink.DeepLinkHandler
import my.ssdid.mobile.ui.navigation.SsdidNavGraph
import my.ssdid.mobile.ui.theme.SsdidTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SsdidTheme {
                val controller = rememberNavController()
                navController = controller
                SsdidNavGraph(navController = controller)

                // Handle deep link from cold start
                if (savedInstanceState == null) {
                    intent?.data?.let { uri ->
                        handleDeepLink(intent)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        val deepLinkAction = DeepLinkHandler.parse(uri) ?: run {
            Log.w(TAG, "Unrecognized deep link URI: $uri")
            return
        }
        val route = deepLinkAction.toNavRoute() ?: run {
            Log.w(TAG, "Unknown deep link action: ${deepLinkAction.action}")
            return
        }
        navController?.navigate(route)
        // Clear the intent data so it is not re-processed on config change
        intent.data = null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
