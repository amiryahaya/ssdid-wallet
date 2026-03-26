package my.ssdid.wallet.ui.offline

// Category: e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import my.ssdid.wallet.MainActivity
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Tests 1-2: Online verification flow using real Hilt dependencies.
 * Tests 3-6 (fake verifier / offline states) live in VerificationFlowOfflineTest.
 */
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
        // This requires a pre-existing credential — guard with Assume so CI skips cleanly
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("credential_card").fetchSemanticsNodes()
        Assume.assumeTrue("No credential cards available", cards.isNotEmpty())
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
    // Self-contained: navigates through the full flow rather than relying on Test 1's state.
    @Test
    fun verificationResult_showsExpandableDetails() {
        // Navigate to credentials and guard if none are available
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("credential_card").fetchSemanticsNodes()
        Assume.assumeTrue("No credential cards available", cards.isNotEmpty())
        // Open first credential and trigger verification
        composeRule.onAllNodesWithTag("credential_card").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Verify").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Credential verified", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Tap the result card to expand check details
        composeRule.onNodeWithText("Credential verified", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Signature").assertIsDisplayed()
        composeRule.onNodeWithText("Expiry").assertIsDisplayed()
    }
}
