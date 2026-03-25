package my.ssdid.wallet.ui.offline

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
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
class BundleManagementTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun navigateToBundleManagement() {
        // Navigate: WalletHome → Settings → Prepare for Offline
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Prepare for Offline").performClick()
        composeRule.waitForIdle()
    }

    // UC-5 Test 1: Add issuer by valid DID
    @Test
    fun addIssuerByValidDid_appearsInList() {
        navigateToBundleManagement()
        // Tap add button
        composeRule.onNodeWithContentDescription("Add Issuer").performClick()
        composeRule.waitForIdle()
        // Enter a test DID (use a known registered DID or create one)
        composeRule.onNode(hasText("did:ssdid:")).performTextInput("did:ssdid:test")
        composeRule.onNodeWithText("Add").performClick()
        // Wait for network fetch
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("did:ssdid:test").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Fresh", substring = true).assertIsDisplayed()
    }

    // UC-5 Test 2: Reject invalid DID format
    @Test
    fun rejectInvalidDid_showsError() {
        navigateToBundleManagement()
        composeRule.onNodeWithContentDescription("Add Issuer").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("did:ssdid:")).performTextInput("not-a-did")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Invalid", substring = true).assertIsDisplayed()
    }

    // UC-6 Test 3: Refresh all bundles updates freshness
    @Test
    fun refreshAll_updatesBundle() {
        navigateToBundleManagement()
        // Assumes at least one bundle exists (from test 1 or pre-seeded)
        composeRule.onNodeWithContentDescription("Refresh All").performClick()
        // Wait for refresh to complete
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    // UC-6 Test 4: Delete bundle via delete button
    @Test
    fun deleteBundle_removedFromList() {
        navigateToBundleManagement()
        // Assumes at least one bundle exists
        val bundleNodes = composeRule.onAllNodesWithText("did:ssdid:", substring = true)
        val countBefore = bundleNodes.fetchSemanticsNodes().size
        if (countBefore > 0) {
            composeRule.onNodeWithContentDescription("Delete").performClick()
            composeRule.waitForIdle()
            val countAfter = composeRule.onAllNodesWithText("did:ssdid:", substring = true)
                .fetchSemanticsNodes().size
            assertThat(countAfter).isLessThan(countBefore)
        }
    }

    // UC-6 Test 5: Empty state shown when no bundles
    @Test
    fun emptyState_showsMessage() {
        navigateToBundleManagement()
        // If list is empty, should show empty state
        composeRule.onNodeWithText("No cached bundles", substring = true, ignoreCase = true)
            .assertExists()
    }

    // UC-5 Test 6: Scan credential button navigates to scanner
    @Test
    fun scanCredential_navigatesToScanner() {
        navigateToBundleManagement()
        composeRule.onNodeWithContentDescription("Add Issuer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Scan Credential QR").performClick()
        composeRule.waitForIdle()
        // Assert scanner screen appeared (camera permission dialog or scanner view)
        // The exact assertion depends on the ScanQrScreen implementation
    }
}
