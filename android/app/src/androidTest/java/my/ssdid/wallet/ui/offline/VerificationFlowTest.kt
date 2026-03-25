package my.ssdid.wallet.ui.offline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class VerificationFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // UC-1 Test 1: Verify credential online shows green traffic light
    @Test
    fun verifyOnline_showsGreenTrafficLight() {
        // Navigate to a credential and tap verify
        // This requires a pre-existing credential — may need setup
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        // Select first credential
        composeRule.onAllNodesWithTag("credential_card").onFirst().performClick()
        composeRule.waitForIdle()
        // Tap verify
        composeRule.onNodeWithText("Verify").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Credential verified", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Credential verified").assertIsDisplayed()
    }

    // UC-2 Test 2: Verification result shows expandable check details
    @Test
    fun verificationResult_showsExpandableDetails() {
        // After a verification (reuse test 1 setup or mock)
        // Tap the result card to expand
        composeRule.onNodeWithText("Credential verified", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Signature").assertIsDisplayed()
        composeRule.onNodeWithText("Expiry").assertIsDisplayed()
    }

    // UC-2 Test 3: Yellow traffic light for DEGRADED result
    @Test
    fun degradedResult_showsYellowTrafficLight() {
        // Requires Hilt module replacement: inject FakeOnlineVerifier + stale bundle
        // See spec: @UninstallModules(OfflineModule::class) + @BindValue
        composeRule.onNodeWithText("Verified with limitations", substring = true).assertIsDisplayed()
    }

    // UC-2 Test 4: Red traffic light for FAILED result
    @Test
    fun failedResult_showsRedTrafficLight() {
        // Requires Hilt module replacement: inject FakeOnlineVerifier + expired credential
        composeRule.onNodeWithText("Verification failed", substring = true).assertIsDisplayed()
    }

    // Test 5: Offline badge appears when source is offline
    @Test
    fun offlineResult_showsOfflineBadge() {
        // Requires Hilt module replacement: FakeOnlineVerifier + fresh bundle
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    // Test 6: Pre-verification check when no bundle cached
    @Test
    fun noBundleCached_showsWarningMessage() {
        // Requires Hilt module replacement: FakeOnlineVerifier + empty BundleStore
        // After verification attempt fails
        composeRule.onNodeWithText("No cached data", substring = true).assertExists()
    }
}
