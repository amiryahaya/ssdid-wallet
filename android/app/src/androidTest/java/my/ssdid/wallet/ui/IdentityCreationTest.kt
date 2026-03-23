package my.ssdid.wallet.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
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
class IdentityCreationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun identityCreation_showsDisplayNameStep() {
        composeRule.onNodeWithText("Create Identity", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("Display Name")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Email")
            .assertIsDisplayed()
    }

    @Test
    fun identityCreation_canEnterDisplayName() {
        composeRule.onNodeWithText("Create Identity", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("Display Name")
            .performTextInput("Test User")
        composeRule.onNodeWithText("Test User")
            .assertIsDisplayed()
    }
}
