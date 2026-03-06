package my.ssdid.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import my.ssdid.mobile.ui.navigation.SsdidNavGraph
import my.ssdid.mobile.ui.theme.SsdidTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SsdidTheme {
                val navController = rememberNavController()
                SsdidNavGraph(navController = navController)
            }
        }
    }
}
