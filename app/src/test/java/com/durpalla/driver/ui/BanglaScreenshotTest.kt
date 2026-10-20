package com.durpalla.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.durpalla.driver.R
import com.durpalla.driver.data.SampleData
import com.durpalla.driver.domain.BidStatus
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.i18n.UiText
import com.durpalla.driver.realtime.ConnectionState
import com.durpalla.driver.realtime.LiveBid
import com.durpalla.driver.ui.components.LiveBannerCard
import com.durpalla.driver.ui.components.LocalAnimateLive
import com.durpalla.driver.ui.screens.*
import com.durpalla.driver.ui.theme.DP
import com.durpalla.driver.ui.theme.DurpallaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bangla UI (values-bn + SolaimanLipi + Bangla digits). Images: build/shots/driver_bn_*.png */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "bn-w390dp-h844dp-xxhdpi")
class BanglaScreenshotTest {
    private val r = SampleData.requests[0]
    @Before fun bn() { AppLocale.language = AppLanguage.BANGLA }
    @After fun en() { AppLocale.language = AppLanguage.ENGLISH }
    private fun shot(n: String, c: @Composable () -> Unit) =
        captureRoboImage("build/shots/driver_bn_$n.png") { CompositionLocalProvider(LocalAnimateLive provides false) { DurpallaTheme { c() } } }

    @Test fun languagePrompt() = shot("language_prompt") { LanguagePromptScreen(AppLanguage.BANGLA) {} }
    @Test fun languagePromptUnselected() = shot("language_prompt_unselected") { LanguagePromptScreen(null) {} }
    @Test fun login() = shot("login") { PhoneLoginScreen(AuthState(phone = "01711234567"), {}, {}) }
    @Test fun otp() = shot("otp") { OtpScreen("01711234567", null, {}, {}) }
    @Test fun home() = shot("home") {
        HomeScreen(SampleData.driver, true, {}, SampleData.summary, 2, SampleData.trips[0], SampleData.requests, { it.id == "r3" }, {}, {}, {}, {}, {}, ConnectionState.LIVE)
    }
    @Test fun bid() = shot("bid") {
        PlaceBidScreen(r, BidFormState(amount = "3100"), {}, {}, {}, {}, {}, LiveBid(r.id, 5, 2950, "b1", 3100, BidStatus.PENDING), ConnectionState.LIVE)
    }
    @Test fun acceptedBanner() = shot("accepted_banner") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(SampleData.driver, true, {}, SampleData.summary, 2, SampleData.trips[0], SampleData.requests, { it.id == "r3" }, {}, {}, {}, {}, {}, ConnectionState.LIVE)
            Box(Modifier.fillMaxSize().background(DP.Ink.copy(alpha = 0.35f)))
            LiveBannerCard(LiveBanner(LiveBanner.Kind.ACCEPTED, UiText.res(R.string.banner_accepted), UiText.res(R.string.banner_accepted_body, "Sadarghat", "Narayanganj", UiText.Taka(3100)), "won/t1"), {}, {},
                Modifier.align(Alignment.TopCenter).padding(12.dp).padding(top = 24.dp))
        }
    }
    @Test fun settingsLanguage() = shot("settings_language") { LanguageSettingsScreen(AppLanguage.BANGLA, {}, {}) }
}
