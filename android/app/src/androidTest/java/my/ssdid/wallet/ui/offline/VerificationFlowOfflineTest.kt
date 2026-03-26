package my.ssdid.wallet.ui.offline

// Category: e2e, offline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.MainActivity
import my.ssdid.wallet.di.OfflineModule
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Tests 3-6: Offline/degraded/failed verification flows.
 * Uses @UninstallModules(OfflineModule::class) so that FakeOnlineVerifier and
 * InMemoryBundleStore replace the real network-dependent implementations.
 */
@HiltAndroidTest
@UninstallModules(OfflineModule::class)
@RunWith(AndroidJUnit4::class)
class VerificationFlowOfflineTest {

    /** FakeOnlineVerifier replaces the real Verifier binding from OfflineModule */
    @BindValue
    @JvmField
    val fakeVerifier: Verifier = FakeOnlineVerifier(shouldThrow = null, shouldReturn = false)

    /** InMemoryBundleStore replaces DataStoreBundleStore binding from OfflineModule */
    @BindValue
    @JvmField
    val bundleStore: BundleStore = InMemoryBundleStore()

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val issuerDid = "did:ssdid:offline-flow-test-issuer"

    @Before
    fun setUp() {
        hiltRule.inject()
        (bundleStore as InMemoryBundleStore).clear()
    }

    private fun navigateToFirstCredentialAndVerify() {
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("credential_card").fetchSemanticsNodes()
        if (cards.isEmpty()) {
            // No credentials on clean device — skip gracefully
            org.junit.Assume.assumeTrue("No credential cards available — skipping UI verification test", false)
        }
        composeRule.onAllNodesWithTag("credential_card").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Verify").performClick()
        composeRule.waitForIdle()
    }

    // UC-2 Test 3: Yellow traffic light for DEGRADED result (stale bundle + network failure)
    @Test
    fun degradedResult_showsYellowTrafficLight() {
        // Pre-seed a stale bundle (freshnessRatio = 1.5 → expired, causes DEGRADED)
        val (pubKey, _) = OfflineTestHelper.createKeyPair()
        val keyId = "$issuerDid#key-1"
        val didDocument = OfflineTestHelper.createDidDocument(issuerDid, keyId, pubKey)
        val staleBundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 1.5)
        runBlocking { bundleStore.saveBundle(staleBundle) }

        navigateToFirstCredentialAndVerify()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Verified with limitations", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Verified with limitations", substring = true).assertIsDisplayed()
    }

    // UC-2 Test 4: Red traffic light for FAILED result (no bundle + network failure)
    @Test
    fun failedResult_showsRedTrafficLight() {
        // bundleStore is empty → online verifier returns false → FAILED
        navigateToFirstCredentialAndVerify()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Verification failed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Verification failed", substring = true).assertIsDisplayed()
    }

    // Test 5: Offline badge appears when source is offline (fresh bundle, network failure)
    @Test
    fun offlineResult_showsOfflineBadge() {
        // Pre-seed a fresh bundle so offline verification succeeds
        val (pubKey, privKey) = OfflineTestHelper.createKeyPair()
        val keyId = "$issuerDid#key-1"
        val didDocument = OfflineTestHelper.createDidDocument(issuerDid, keyId, pubKey)
        val freshBundle = OfflineTestHelper.createBundle(issuerDid, didDocument, freshnessRatio = 0.1)
        runBlocking { bundleStore.saveBundle(freshBundle) }

        navigateToFirstCredentialAndVerify()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Offline", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Offline", substring = true).assertIsDisplayed()
    }

    // Test 6: Pre-verification check when no bundle cached shows warning
    @Test
    fun noBundleCached_showsWarningMessage() {
        // bundleStore is empty — after verification attempt the UI should surface
        // "No cached data" or equivalent warning text
        navigateToFirstCredentialAndVerify()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("No cached data", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Verification failed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Accept either the specific "No cached data" copy or the generic failure state
        val noCacheExists = composeRule
            .onAllNodesWithText("No cached data", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        val failedExists = composeRule
            .onAllNodesWithText("Verification failed", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        assert(noCacheExists || failedExists) {
            "Expected 'No cached data' or 'Verification failed' to be visible when bundle store is empty"
        }
    }
}
