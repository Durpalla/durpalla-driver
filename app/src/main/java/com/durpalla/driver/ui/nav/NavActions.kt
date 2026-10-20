package com.durpalla.driver.ui.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

/** Logged-in app lives in this nested graph; its start destination is Home (the bottom-nav root). */
const val MAIN_GRAPH = "main"

private fun NavController.mainGraph(): NavGraph? = graph.findNode(MAIN_GRAPH) as? NavGraph
private fun NavController.homeId(): Int? = mainGraph()?.findStartDestination()?.id

/** True when Home is in the back stack (i.e. the user is inside the main graph). */
fun NavController.hasHome(): Boolean = homeId()?.let { id -> runCatching { getBackStackEntry(id) }.isSuccess } == true

/**
 * THE way back to Home. Pops everything above Home (inclusive = false, no state saved), single-top,
 * never restores a previously saved stack, so terminal screens (bid won, trip done, withdrawal OK…) can't come back.
 * If Home is not in the stack yet (e.g. right after login) the whole graph is replaced by the main graph.
 */
fun NavController.navigateToHome() {
    val home = homeId()
    if (home != null && hasHome()) {
        if (currentDestination?.id == home) return
        navigate(Routes.HOME) { popUpTo(home) { inclusive = false; saveState = false }; launchSingleTop = true; restoreState = false }
    } else {
        navigate(MAIN_GRAPH) { popUpTo(graph.id) { inclusive = true }; launchSingleTop = true }
    }
}

/** Bottom-nav switch: keeps each tab's own stack (standard multiple back stacks). */
fun NavController.navigateToTab(route: String) {
    if (route == Routes.HOME) return navigateToHome()
    val home = homeId() ?: return navigate(route)
    navigate(route) { popUpTo(home) { saveState = true }; launchSingleTop = true; restoreState = true }
}

/**
 * Open a tab *fresh* after finishing a flow (e.g. "Go to My Bids", "Back to earnings"): its saved stack is
 * dropped first, otherwise restoreState would bring the finished flow straight back.
 */
fun NavController.navigateToTabFresh(route: String, tabPattern: String = route) {
    val home = homeId() ?: return navigate(route)
    if (!hasHome()) navigateToHome()
    // Drop the live finished flow first (popUpTo + launchSingleTop would reuse the old tab entry and keep it).
    popBackStack(home, inclusive = false, saveState = false)
    mainGraph()?.findNode(tabPattern)?.id?.let { clearBackStack(it) }
    navigate(route) { launchSingleTop = true; restoreState = false }
}

/** Forward navigation that ignores a second tap on the same target (double-tap guard). */
fun NavController.go(route: String, builder: NavOptionsBuilder.() -> Unit = {}) = navigate(route) { launchSingleTop = true; builder() }

/** Deep links (notification tap, banner): always land with Home underneath, so Back returns Home instead of exiting. */
fun NavController.openDeepLink(route: String) {
    when {
        route == Routes.HOME || route.isBlank() -> navigateToHome()
        route.startsWith("requests") -> navigateToTabFresh(if (route.contains("tab=")) route else Routes.requests(0), Routes.REQUESTS)
        else -> { if (!hasHome()) navigateToHome(); go(route) }
    }
}

/** Leave the logged-in area (logout). */
fun NavController.navigateToLogin() = navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true }; launchSingleTop = true }
