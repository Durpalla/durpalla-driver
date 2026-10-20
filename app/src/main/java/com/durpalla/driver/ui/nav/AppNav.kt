package com.durpalla.driver.ui.nav

import androidx.compose.ui.res.stringResource
import com.durpalla.driver.R
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.i18n.LanguageManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.DriverViewModel
import com.durpalla.driver.ui.LiveBanner
import com.durpalla.driver.ui.components.LiveBannerCard
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import com.durpalla.driver.ui.screens.*
import com.durpalla.driver.ui.theme.DP

object Routes {
    const val SPLASH = "splash"; const val LANGUAGE_PROMPT = "language/first"; const val LANGUAGE = "language"; const val LOGIN = "login"; const val OTP = "otp"
    const val KYC_VEHICLE = "kyc/vehicle"; const val KYC_DOCS = "kyc/docs"; const val KYC_REVIEW = "kyc/review"
    const val HOME = "home"; const val REQUESTS = "requests?tab={tab}"; const val MY_TRIPS = "trips"; const val EARNINGS = "earnings"; const val PROFILE = "profile"
    const val INCOMING = "request/{id}"; const val BID = "request/{id}/bid"; const val CONFLICT = "request/{id}/conflict"
    const val BID_WON = "won/{tripId}"; const val TRIP = "trip/{tripId}"; const val LIVE = "trip/{tripId}/live"
    const val COLLECT = "trip/{tripId}/collect"; const val COMPLETED = "trip/{tripId}/done"
    const val PREDEF = "predefined"; const val PREDEF_FORM = "predefined/edit?id={id}"; const val CALENDAR = "availability"
    const val HISTORY = "history"; const val RATINGS = "ratings"; const val WITHDRAW = "withdraw"; const val WITHDRAW_OK = "withdraw/ok"
    const val RECON = "reconciliation"; const val WALLET = "wallet"; const val NOTIFS = "notifications"; const val VEHICLES = "vehicles"; const val SUPPORT = "support"
    fun incoming(id: String) = "request/$id"; fun bid(id: String) = "request/$id/bid"; fun conflict(id: String) = "request/$id/conflict"
    fun won(t: String) = "won/$t"; fun trip(t: String) = "trip/$t"; fun live(t: String) = "trip/$t/live"; fun collect(t: String) = "trip/$t/collect"; fun done(t: String) = "trip/$t/done"
    fun predefForm(id: String?) = "predefined/edit?id=${id ?: ""}"
    /** tab 0 = New requests, 1 = My bids. */
    fun requests(tab: Int = 0) = "requests?tab=$tab"
}

private data class Tab(val route: String, @androidx.annotation.StringRes val label: Int, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Filled.Home), Tab(Routes.REQUESTS, R.string.tab_requests, Icons.Filled.Inbox), Tab(Routes.MY_TRIPS, R.string.tab_trips, Icons.Filled.Schedule),
    Tab(Routes.EARNINGS, R.string.tab_earnings, Icons.Filled.Payments), Tab(Routes.PROFILE, R.string.tab_profile, Icons.Filled.Person),
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverApp(
    vm: DriverViewModel = viewModel(),
    deepLink: kotlinx.coroutines.flow.StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onDeepLinkHandled: () -> Unit = {},
    language: LanguageManager = ServiceLocator.language,
    onLanguageApplied: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    calls: com.durpalla.driver.call.CallProvider = ServiceLocator.calls,
) {
    val scope = rememberCoroutineScope()
    val nav = navController
    val callState by calls.state.collectAsStateWithLifecycle()
    val micDeniedMsg = stringResource(R.string.call_mic_denied)
    val callCtx = androidx.compose.ui.platform.LocalContext.current
    val withMic = rememberWithMic { android.widget.Toast.makeText(callCtx, micDeniedMsg, android.widget.Toast.LENGTH_LONG).show() }
    val acceptRequested by ServiceLocator.acceptFromNotification.collectAsStateWithLifecycle()
    LaunchedEffect(acceptRequested, callState) {
        if (acceptRequested && callState is com.durpalla.driver.call.CallState.Incoming) { ServiceLocator.acceptFromNotification.value = false; withMic { calls.accept() } }
    }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val requests by vm.openRequests.collectAsStateWithLifecycle()
    val allRequests by vm.requests.collectAsStateWithLifecycle()
    val bids by vm.bids.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val predefined by vm.predefined.collectAsStateWithLifecycle()
    val blocked by vm.blockedDays.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val driver by vm.driver.collectAsStateWithLifecycle()
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val bidForm by vm.bidForm.collectAsStateWithLifecycle()
    val wd by vm.withdraw.collectAsStateWithLifecycle()
    val recon by vm.reconciliation.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val live by vm.liveBids.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val pending by deepLink.collectAsStateWithLifecycle()
    // Notification tap -> route (only once logged in, i.e. a tab destination exists in the back stack).
    LaunchedEffect(pending, current) {
        val route = pending ?: return@LaunchedEffect
        // Only once logged in (Home exists); during login/KYC the link waits.
        if (current != null && nav.hasHome()) {
            nav.openDeepLink(route)
            onDeepLinkHandled()
        }
    }
    // Android 13+ notification permission, asked once the driver reaches Home.
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(current == Routes.HOME) {
        if (current == Routes.HOME && android.os.Build.VERSION.SDK_INT >= 33 && !com.durpalla.driver.notifications.Notifier.canPost(ctx))
            permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    // Recompute slot locks whenever trips change.
    val blockedFn: (TripRequest) -> Boolean = remember(trips) { { r -> vm.isSlotBlocked(r) } }
    var bidSheet by remember { mutableStateOf<Bid?>(null) }

    Scaffold(
        containerColor = DP.Bg,
        bottomBar = {
            if (current in tabs.map { it.route }) NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(selected = current == t.route, onClick = { if (t.route == Routes.REQUESTS) nav.navigateToTab(Routes.requests(0)) else nav.navigateToTab(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label), maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = DP.Primary500, selectedTextColor = DP.Primary500, indicatorColor = DP.Primary100))
                }
            }
        },
    ) { outer ->
      Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.padding(bottom = if (current in tabs.map { it.route }) outer.calculateBottomPadding() else 0.dp)) {
            composable(Routes.SPLASH) {
                SplashScreen {
                    scope.launch {
                        val next = if (language.needsPrompt()) Routes.LANGUAGE_PROMPT else Routes.LOGIN
                        nav.go(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                }
            }
            composable(Routes.LANGUAGE_PROMPT) {
                LanguagePromptScreen(initial = AppLanguage.fromTag(java.util.Locale.getDefault().language)?.takeIf { it == AppLanguage.BANGLA }) { lang ->
                    // Navigate first: applying the locale may recreate the activity, which restores this back stack.
                    nav.go(Routes.LOGIN) { popUpTo(Routes.LANGUAGE_PROMPT) { inclusive = true } }
                    scope.launch { language.choose(lang); onLanguageApplied() }
                }
            }
            composable(Routes.LANGUAGE) {
                LanguageSettingsScreen(AppLocale.language, { lang -> scope.launch { language.choose(lang); onLanguageApplied() } }) { nav.popBackStack() }
            }
            composable(Routes.LOGIN) { PhoneLoginScreen(auth, vm::setPhone) { vm.sendOtp { nav.go(Routes.OTP) } } }
            composable(Routes.OTP) { OtpScreen(auth.phone, auth.error, { nav.popBackStack() }) { code -> vm.verifyOtp(code) { kyc -> if (kyc) nav.navigateToHome() else nav.go(Routes.KYC_VEHICLE) } } }
            composable(Routes.KYC_VEHICLE) { KycVehicleScreen(auth.kycType, vm::setKycType, { nav.popBackStack() }) { nav.go(Routes.KYC_DOCS) } }
            composable(Routes.KYC_DOCS) { KycDocumentsScreen(auth.uploaded, vm::toggleDoc, { nav.popBackStack() }) { vm.submitKyc { nav.go(Routes.KYC_REVIEW) } } }
            composable(Routes.KYC_REVIEW) { KycReviewScreen { nav.navigateToHome() } }

          // Logged-in area. Home is the start destination: every "Back to home" pops to it.
          navigation(startDestination = Routes.HOME, route = MAIN_GRAPH) {

            composable(Routes.HOME) {
                HomeScreen(driver, online, vm::setOnline, summary, bids.count { it.status == BidStatus.PENDING },
                    trips.filter { it.status == TripStatus.UPCOMING }.minByOrNull { it.slot.start }, requests, blockedFn,
                    onNotifications = { nav.go(Routes.NOTIFS) }, onUpcoming = { nav.go(Routes.trip(it.id)) },
                    onRequest = { nav.go(Routes.incoming(it.id)) }, onSkip = { vm.skip(it.id) }, onSeeAll = { nav.navigateToTab(Routes.requests(0)) }, connection = connection)
            }
            composable(Routes.REQUESTS, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { e ->
                RequestsScreen(requests, bids, blockedFn, e.arguments?.getInt("tab") ?: 0, { nav.go(Routes.NOTIFS) }, { nav.go(Routes.incoming(it.id)) }, { bidSheet = it }, connection)
            }
            composable(Routes.INCOMING) { e ->
                val r = allRequests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                IncomingRequestScreen(r, vm.conflictFor(r), { nav.popBackStack() }, { vm.skip(r.id); nav.popBackStack() },
                    { vm.resetBid(r.suggestedMin); nav.go(Routes.bid(r.id)) }, { nav.go(Routes.conflict(r.id)) }, live[r.id], connection)
            }
            composable(Routes.BID) { e ->
                val r = allRequests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                PlaceBidScreen(r, bidForm, vm::setBidAmount, vm::setBidNote, { nav.popBackStack() }, { vm.submitBid(r.id) },
                    onDone = { vm.resetBid(); nav.navigateToTabFresh(Routes.requests(1), Routes.REQUESTS) }, live = live[r.id], connection = connection)
            }
            composable(Routes.CONFLICT) { e ->
                val r = allRequests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                SlotConflictScreen(r, vm.conflictFor(r), { nav.popBackStack() }) { nav.go(Routes.trip(it.id)) }
            }
            composable(Routes.BID_WON) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                BidWonScreen(t, { nav.go(Routes.trip(t.id)) { popUpTo(Routes.BID_WON) { inclusive = true } } }) { nav.navigateToHome() }
            }
            composable(Routes.MY_TRIPS) {
                MyTripsScreen(trips, predefined, { nav.go(Routes.trip(it.id)) }, { nav.go(Routes.predefForm(it.id)) }, { nav.go(Routes.predefForm(null)) },
                    { nav.go(Routes.CALENDAR) }, { nav.go(Routes.HISTORY) })
            }
            composable(Routes.TRIP) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                TripDetailScreen(t, { nav.popBackStack() }, { vm.setTripStatus(t.id, TripStatus.ON_THE_WAY); nav.go(Routes.live(t.id)) },
                    onCall = { withMic { calls.startCall(t.id, t.customer.name) } })
            }
            composable(Routes.LIVE) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                LiveTripScreen(t, { vm.setTripStatus(t.id, it) }, onCollect = { nav.go(Routes.collect(t.id)) }, onCall = { withMic { calls.startCall(t.id, t.customer.name) } })
            }
            composable(Routes.COLLECT) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                CollectPaymentScreen(t, { nav.popBackStack() }) { m -> vm.setTripStatus(t.id, TripStatus.COMPLETED, m); nav.go(Routes.done(t.id)) { popUpTo(Routes.TRIP) { inclusive = true } } }
            }
            composable(Routes.COMPLETED) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable MissingContent({ nav.popBackStack() }) { nav.navigateToHome() }
                TripCompletedScreen(t) { nav.navigateToHome() }
            }
            composable(Routes.PREDEF) { PredefinedListScreen(predefined, { nav.popBackStack() }, { nav.go(Routes.predefForm(it.id)) }) { nav.go(Routes.predefForm(null)) } }
            composable(Routes.PREDEF_FORM, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                val id = e.arguments?.getString("id")
                PredefinedFormScreen(predefined.firstOrNull { it.id == id }, { nav.popBackStack() }) { vm.savePredefined(it); nav.popBackStack() }
            }
            composable(Routes.CALENDAR) { AvailabilityScreen(trips, blocked, vm::toggleDay) { nav.popBackStack() } }
            composable(Routes.HISTORY) { TripHistoryScreen(trips, { nav.popBackStack() }) { nav.go(Routes.trip(it.id)) } }
            composable(Routes.RATINGS) { RatingsScreen(driver.rating, ratings) { nav.popBackStack() } }

            composable(Routes.EARNINGS) {
                EarningsScreen(summary, { vm.resetWithdraw(); nav.go(Routes.WITHDRAW) }, { nav.go(Routes.WALLET) }, { nav.go(Routes.RECON) }, { nav.go(Routes.RATINGS) }, driver.rating)
            }
            composable(Routes.WITHDRAW) {
                LaunchedEffect(wd.done) { if (wd.done != null) nav.go(Routes.WITHDRAW_OK) { popUpTo(Routes.WITHDRAW) { inclusive = true } } }
                WithdrawScreen(summary.walletBalance, vm.accounts, wd, vm::setWdAccount, vm::setWdAmount, vm::setPin, vm::confirmWithdraw) { nav.popBackStack() }
            }
            composable(Routes.WITHDRAW_OK) {
                val done = wd.done
                if (done == null) MissingContent({ nav.navigateToTabFresh(Routes.EARNINGS) }) { nav.navigateToHome() }
                else WithdrawSuccessScreen(done, wd.account) { nav.navigateToTabFresh(Routes.EARNINGS) }
            }
            composable(Routes.RECON) { ReconciliationScreen(recon, vm::loadReconciliation) { nav.popBackStack() } }
            composable(Routes.WALLET) { WalletScreen(summary.walletBalance, txs, { nav.popBackStack() }) { vm.resetWithdraw(); nav.go(Routes.WITHDRAW) } }

            composable(Routes.PROFILE) {
                ProfileScreen(driver, { nav.go(Routes.VEHICLES) }, { nav.go(Routes.RATINGS) }, { nav.go(Routes.HISTORY) }, { nav.go(Routes.PREDEF) },
                    { nav.go(Routes.CALENDAR) }, { nav.go(Routes.WALLET) }, { nav.go(Routes.SUPPORT) }, { nav.go(Routes.NOTIFS) },
                    { nav.navigateToLogin() }, onLanguage = { nav.go(Routes.LANGUAGE) })
            }
            composable(Routes.VEHICLES) { VehiclesScreen(driver) { nav.popBackStack() } }
            composable(Routes.NOTIFS) { NotificationsScreen(notifs) { nav.popBackStack() } }
            composable(Routes.SUPPORT) { SupportScreen { nav.popBackStack() } }
          }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = banner != null && current !in setOf(Routes.SPLASH, Routes.LANGUAGE_PROMPT, Routes.LOGIN, Routes.OTP),
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
        ) {
            banner?.let { b ->
                LiveBannerCard(b, onOpen = { vm.dismissBanner(); nav.openDeepLink(b.route) }, onDismiss = vm::dismissBanner)
            }
        }
        // In-app call (WebRTC audio): drawn above everything; system back can't dismiss a live call.
        if (callState !is com.durpalla.driver.call.CallState.Idle) {
            androidx.activity.compose.BackHandler { }
            CallScreen(callState, onAccept = { withMic { calls.accept() } }, onDecline = calls::decline, onHangUp = calls::hangUp,
                onMute = calls::setMuted, onSpeaker = calls::setSpeaker, onDismiss = calls::dismissEnded)
        }
        // Auto-hide non-critical banners.
        LaunchedEffect(banner) { if (banner != null && banner?.kind != LiveBanner.Kind.ACCEPTED) { kotlinx.coroutines.delay(6000); vm.dismissBanner() } }
      }
    }

    bidSheet?.let { b ->
        ModalBottomSheet(onDismissRequest = { bidSheet = null }, containerColor = Color.White) {
            BidDetailSheetContent(b) {
                bidSheet = null
                vm.simulateWin(b.id) { trip -> nav.go(Routes.won(trip.id)) }
            }
        }
    }
}
