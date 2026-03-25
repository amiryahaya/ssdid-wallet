package my.ssdid.wallet.ui.offline

// Category: e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.MainActivity
import my.ssdid.wallet.di.OfflineModule
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@HiltAndroidTest
@UninstallModules(OfflineModule::class)
@RunWith(AndroidJUnit4::class)
class OfflineSettingsTest {

    // Replace BundleStore with InMemoryBundleStore for all tests so badges are deterministic
    @BindValue
    @JvmField
    val bundleStore: BundleStore = InMemoryBundleStore()

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val issuerDid = "did:ssdid:settings-test-issuer"

    @Before
    fun setUp() {
        hiltRule.inject()
        // Start with a clean store before each test
        (bundleStore as InMemoryBundleStore).clear()
    }

    private fun navigateToSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    // UC-4 Test 1: TTL picker shows presets with recommendations
    @Test
    fun ttlPicker_showsPresetsWithRecommendations() {
        navigateToSettings()
        composeRule.onNodeWithText("Bundle TTL").performClick()
        composeRule.waitForIdle()
        // Assert presets visible
        composeRule.onNodeWithText("1 day").assertIsDisplayed()
        composeRule.onNodeWithText("7 days").assertIsDisplayed()
        composeRule.onNodeWithText("14 days").assertIsDisplayed()
        composeRule.onNodeWithText("30 days").assertIsDisplayed()
        // Assert recommendations visible
        composeRule.onNodeWithText("financial", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    // UC-4 Test 2: Selecting TTL persists value
    @Test
    fun selectTtl_persistsValue() {
        navigateToSettings()
        composeRule.onNodeWithText("Bundle TTL").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("14 days").performClick()
        composeRule.waitForIdle()
        // Navigate away and back
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        // Assert TTL shows 14 days
        composeRule.onNodeWithText("14 days").assertIsDisplayed()
    }

    // UC-3 Test 3: Freshness badge on credential card (aging)
    @Test
    fun agingBundle_showsAgingBadge() {
        // Pre-seed an aging bundle (freshnessRatio = 0.85 → within TTL but past aging threshold)
        val (pubKey, privKey) = OfflineTestHelper.createKeyPair()
        val keyId = "$issuerDid#key-1"
        val didDocument = OfflineTestHelper.createDidDocument(issuerDid, keyId, pubKey)
        val agingBundle = OfflineTestHelper.createBundle(
            issuerDid, didDocument, freshnessRatio = 0.85
        )
        runBlocking { bundleStore.saveBundle(agingBundle) }

        // Navigate to credentials list where bundles display freshness badges
        navigateToSettings()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        // Assert aging badge is visible
        composeRule.onNodeWithText("Bundle aging").assertIsDisplayed()
    }

    // UC-3 Test 4: Freshness badge on credential card (expired)
    @Test
    fun expiredBundle_showsExpiredBadge() {
        // Pre-seed an expired bundle (freshnessRatio = 1.5 → past TTL boundary)
        val (pubKey, _) = OfflineTestHelper.createKeyPair()
        val keyId = "$issuerDid#key-1"
        val didDocument = OfflineTestHelper.createDidDocument(issuerDid, keyId, pubKey)
        val expiredBundle = OfflineTestHelper.createBundle(
            issuerDid, didDocument, freshnessRatio = 1.5
        )
        runBlocking { bundleStore.saveBundle(expiredBundle) }

        navigateToSettings()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        // Assert expired badge is visible
        composeRule.onNodeWithText("Bundle expired").assertIsDisplayed()
    }

    // UC-3 Test 5: No badge when bundle is fresh
    @Test
    fun freshBundle_showsNoBadge() {
        navigateToSettings()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Credentials", substring = true).performClick()
        composeRule.waitForIdle()
        // Assert no freshness badge visible when store is empty / fresh
        composeRule.onNodeWithText("Bundle aging").assertDoesNotExist()
        composeRule.onNodeWithText("Bundle expired").assertDoesNotExist()
    }
}
