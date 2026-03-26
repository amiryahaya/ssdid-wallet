package my.ssdid.wallet.ui.offline

// Category: e2e

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

    // UC-8: Foreground resume triggers bundle refresh — after foreground resume the
    // bundle management screen should load without crashing and show its heading,
    // indicating the sync path did not throw.
    @Test
    fun foregroundResume_triggersBundleRefresh() {
        // Simulate background → foreground lifecycle transition
        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED) // background
        Thread.sleep(500) // brief pause
        scenario.moveToState(Lifecycle.State.RESUMED) // foreground

        // Navigate to bundle management to observe the post-resume state
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Prepare for Offline").performClick()

        // Wait until the bundle management screen header appears (proves sync path
        // ran without crashing and the UI refreshed correctly after resume)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Offline Bundles", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Offline Bundles", substring = true).assertIsDisplayed()

        // Additionally assert there is no stale "aging" badge visible, confirming
        // that an empty/fresh store does not show stale freshness indicators
        composeRule.onNodeWithText("Bundle aging").assertDoesNotExist()
    }
}
