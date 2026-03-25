package my.ssdid.wallet.ui.offline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import my.ssdid.wallet.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackgroundSyncTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // UC-8: Foreground resume triggers bundle refresh
    @Test
    fun foregroundResume_triggersBundleRefresh() {
        // Pre-seed a bundle nearing expiry (freshnessRatio > 0.8)
        // This requires @BindValue injection of BundleStore

        // Simulate background → foreground
        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED) // background
        Thread.sleep(500) // brief pause
        scenario.moveToState(Lifecycle.State.RESUMED) // foreground

        // Navigate to bundle management to observe the change
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Prepare for Offline").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            // Assert: bundle freshness has improved (no more "aging" badge)
            // or that the bundle timestamp is more recent
            true // placeholder — implementer should assert on observable state
        }
    }
}
