package com.durpalla.driver.call

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.ui.screens.CallScreen
import com.durpalla.driver.ui.screens.callDuration
import com.durpalla.driver.ui.theme.DurpallaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CallScreenTest {
    @get:Rule val rule = createComposeRule()
    private fun click(d: String) { rule.onNodeWithContentDescription(d).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
    @After fun reset() { AppLocale.language = AppLanguage.ENGLISH }

    @Test fun incomingAcceptDecline() {
        var accepted = false; var declined = false
        rule.setContent { DurpallaTheme { CallScreen(CallState.Incoming(IncomingCall("c", "t", "Rahim"), 0), { accepted = true }, { declined = true }, {}, {}, {}, {}) } }
        rule.onNodeWithText("Rahim").assertIsDisplayed(); rule.onNodeWithText("Incoming Durpalla call").assertIsDisplayed()
        click("Accept"); click("Decline")
        assertTrue(accepted && declined)
    }

    @Test @Config(qualifiers = "bn") fun activeCallInBangla() {
        AppLocale.language = AppLanguage.BANGLA
        var muted: Boolean? = null; var ended = false
        rule.setContent { DurpallaTheme { CallScreen(CallState.Active("c", "t", "করিম", System.currentTimeMillis()), {}, {}, { ended = true }, { muted = it }, {}, {}) } }
        rule.onNodeWithText("করিম").assertIsDisplayed()
        click("মিউট"); click("শেষ")
        assertEquals(true, muted); assertTrue(ended)
        assertEquals("০১:০৫", callDuration(65))
    }

    @Test fun weakConnectionHint() {
        rule.setContent { DurpallaTheme { CallScreen(CallState.Active("c", "t", "R", System.currentTimeMillis(), weak = true), {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Weak connection").assertIsDisplayed()
    }
}
