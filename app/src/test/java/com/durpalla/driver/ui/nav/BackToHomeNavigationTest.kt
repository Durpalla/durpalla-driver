package com.durpalla.driver.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.durpalla.driver.data.SampleData
import com.durpalla.driver.i18n.InMemoryLanguageStore
import com.durpalla.driver.i18n.LanguageManager
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.ui.components.LocalAnimateLive
import com.durpalla.driver.ui.theme.DurpallaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives every "Back to home" / Done / system-back path through the real nav graph and asserts the
 * user lands on Home with a clean stack ([home] only), and that Back from Home would exit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class BackToHomeNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: TestNavHostController
    private val trip = SampleData.trips[0]

    private fun launch(lang: LanguageManager = LanguageManager(InMemoryLanguageStore(AppLanguage.ENGLISH), {})) {
        rule.setContent {
            nav = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            androidx.compose.runtime.CompositionLocalProvider(LocalAnimateLive provides false) {
                DurpallaTheme { DriverApp(language = lang, navController = nav) }
            }
        }
    }
    private fun ui(block: () -> Unit) { rule.runOnUiThread(block); rule.waitForIdle() }
    /** Screen routes in the back stack, graphs excluded. */
    private fun stack() = nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }
    private fun route() = nav.currentDestination?.route
    private fun loggedIn() { launch(); ui { nav.navigateToHome() } }
    private fun systemBack() { ui { rule.activity.onBackPressedDispatcher.onBackPressed() } }
    private fun assertCleanHome() { assertEquals(Routes.HOME, route()); assertEquals(listOf(Routes.HOME), stack()) }
    private fun click(text: String) { rule.onNodeWithText(text).performClick(); rule.waitForIdle() }

    @Test fun afterLoginHomeIsRootAndBackExits() {
        launch(); ui { nav.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } }
        ui { nav.navigate(Routes.OTP) }
        ui { nav.navigateToHome() }   // what OTP success does
        assertCleanHome()
        assertFalse("Back from Home must exit, not return to OTP/Login", nav.popBackStack())
    }

    @Test fun bidWonBackToHome() {
        loggedIn(); ui { nav.navigateToTab(Routes.requests(0)) }; ui { nav.go(Routes.won(trip.id)) }
        click("Back to home")
        assertCleanHome()
        // Old bug: tab() saved [requests, won] and the Requests tab restored the bid-won screen.
        ui { nav.navigateToTab(Routes.requests(0)) }
        assertEquals(Routes.REQUESTS, route())
        assertEquals(listOf(Routes.HOME, Routes.REQUESTS), stack())
    }

    @Test fun bidWonViewTripThenBackReturnsBelowWon() {
        loggedIn(); ui { nav.go(Routes.won(trip.id)) }
        click("View trip details")
        assertEquals(listOf(Routes.HOME, Routes.TRIP), stack())
        systemBack(); assertCleanHome()
    }

    @Test fun tripCompletedDoneAndNoWayBackIntoPayment() {
        loggedIn(); ui { nav.navigateToTab(Routes.MY_TRIPS) }
        ui { nav.go(Routes.trip(trip.id)) }; ui { nav.go(Routes.live(trip.id)) }; ui { nav.go(Routes.collect(trip.id)) }
        click("Confirm ৳2,200 received")
        assertEquals(Routes.COMPLETED, route())
        assertFalse(Routes.COLLECT in stack()); assertFalse(Routes.LIVE in stack())
        click("Done")
        assertCleanHome()
        ui { nav.navigateToTab(Routes.MY_TRIPS) }
        assertEquals(listOf(Routes.HOME, Routes.MY_TRIPS), stack())
    }

    @Test fun placeBidGoToMyBidsOpensMyBidsTabAndBackGoesHome() {
        loggedIn()
        val r = SampleData.requests[0]
        ui { nav.navigateToTab(Routes.requests(0)) }; ui { nav.go(Routes.incoming(r.id)) }; ui { nav.go(Routes.bid(r.id)) }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("3000"); rule.waitForIdle()
        rule.onAllNodesWithText("Place bid", substring = true).onLast().performClick()
                // viewModelScope delay runs on the paused Robolectric main looper
        rule.waitUntil(10_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS); rule.onAllNodesWithText("Go to My Bids").fetchSemanticsNodes().isNotEmpty() }
        // Bottom-sheet popups don't receive injected touches under Robolectric; fire the semantics click.
        rule.onNodeWithText("Go to My Bids").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(5_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS); rule.waitForIdle(); stack() == listOf(Routes.HOME, Routes.REQUESTS) }
        assertEquals(1, nav.currentBackStackEntry?.arguments?.getInt("tab"))
        systemBack(); assertCleanHome()
    }

    @Test fun withdrawalSuccessWithoutStateIsNotADeadEnd() {
        loggedIn(); ui { nav.navigateToTab(Routes.EARNINGS) }; ui { nav.go(Routes.WITHDRAW_OK) }
        click("Back to home")
        assertCleanHome()
    }

    @Test fun reconciliationBackAndEarningsTabFresh() {
        loggedIn(); ui { nav.navigateToTab(Routes.EARNINGS) }; ui { nav.go(Routes.RECON) }
        systemBack(); assertEquals(listOf(Routes.HOME, Routes.EARNINGS), stack())
        systemBack(); assertCleanHome()
    }

    @Test fun deepLinkHasHomeUnderneath() {
        loggedIn(); ui { nav.navigateToTab(Routes.PROFILE) }
        ui { nav.openDeepLink("trip/${trip.id}") }
        assertEquals(Routes.TRIP, route()); assertEquals(Routes.HOME, stack().first())
        systemBack(); systemBack()
        assertEquals(Routes.HOME, route())
        assertFalse(nav.popBackStack())
    }

    @Test fun deepLinkToMissingTripShowsBackToHome() {
        loggedIn(); ui { nav.openDeepLink("won/does-not-exist") }
        click("Back to home"); assertCleanHome()
    }

    @Test fun notificationBidsDeepLinkOpensMyBids() {
        loggedIn(); ui { nav.openDeepLink("requests?tab=1") }
        assertEquals(listOf(Routes.HOME, Routes.REQUESTS), stack())
        assertEquals(1, nav.currentBackStackEntry?.arguments?.getInt("tab"))
        ui { nav.openDeepLink("home") }; assertCleanHome()
    }

    @Test fun doubleTapNavigatesOnce() {
        loggedIn()
        ui { nav.go(Routes.trip(trip.id)); nav.go(Routes.trip(trip.id)) }
        assertEquals(listOf(Routes.HOME, Routes.TRIP), stack())
        ui { nav.navigateToHome(); nav.navigateToHome() }
        assertCleanHome()
    }

    @Test fun homeTabFromAnyTabClearsStack() {
        loggedIn(); ui { nav.navigateToTab(Routes.EARNINGS) }; ui { nav.go(Routes.WALLET) }
        systemBack(); assertEquals(Routes.EARNINGS, route())
        click("Home")
        assertCleanHome()
    }

    @Test fun languagePromptContinueLeavesNoPromptBehind() {
        launch(LanguageManager(InMemoryLanguageStore(null), {}))
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        assertEquals(Routes.LANGUAGE_PROMPT, route())
        click("English"); click("Continue")
        assertEquals(listOf(Routes.LOGIN), stack())
    }

    @Test fun logoutClearsEverything() {
        loggedIn(); ui { nav.navigateToTab(Routes.PROFILE) }
        rule.onNodeWithText("Log out").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(Routes.LOGIN), stack())
    }
}
