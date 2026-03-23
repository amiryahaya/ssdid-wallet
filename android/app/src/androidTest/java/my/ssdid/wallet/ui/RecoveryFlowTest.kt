package my.ssdid.wallet.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import my.ssdid.wallet.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecoveryFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun recoverySetup_showsRecoveryOptions() {
        composeRule.onNodeWithText("Settings", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("Recovery", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("Recovery Key")
            .assertIsDisplayed()
    }
}
