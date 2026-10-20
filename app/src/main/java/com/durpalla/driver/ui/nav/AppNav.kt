package com.durpalla.driver.ui.nav

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
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.DriverViewModel
import com.durpalla.driver.ui.screens.*
import com.durpalla.driver.ui.theme.DP

object Routes {
    const val SPLASH = "splash"; const val LOGIN = "login"; const val OTP = "otp"
    const val KYC_VEHICLE = "kyc/vehicle"; const val KYC_DOCS = "kyc/docs"; const val KYC_REVIEW = "kyc/review"
    const val HOME = "home"; const val REQUESTS = "requests"; const val MY_TRIPS = "trips"; const val EARNINGS = "earnings"; const val PROFILE = "profile"
    const val INCOMING = "request/{id}"; const val BID = "request/{id}/bid"; const val CONFLICT = "request/{id}/conflict"
    const val BID_WON = "won/{tripId}"; const val TRIP = "trip/{tripId}"; const val LIVE = "trip/{tripId}/live"
    const val COLLECT = "trip/{tripId}/collect"; const val COMPLETED = "trip/{tripId}/done"
    const val PREDEF = "predefined"; const val PREDEF_FORM = "predefined/edit?id={id}"; const val CALENDAR = "availability"
    const val HISTORY = "history"; const val RATINGS = "ratings"; const val WITHDRAW = "withdraw"; const val WITHDRAW_OK = "withdraw/ok"
    const val RECON = "reconciliation"; const val WALLET = "wallet"; const val NOTIFS = "notifications"; const val VEHICLES = "vehicles"; const val SUPPORT = "support"
    fun incoming(id: String) = "request/$id"; fun bid(id: String) = "request/$id/bid"; fun conflict(id: String) = "request/$id/conflict"
    fun won(t: String) = "won/$t"; fun trip(t: String) = "trip/$t"; fun live(t: String) = "trip/$t/live"; fun collect(t: String) = "trip/$t/collect"; fun done(t: String) = "trip/$t/done"
    fun predefForm(id: String?) = "predefined/edit?id=${id ?: ""}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home), Tab(Routes.REQUESTS, "Requests", Icons.Filled.Inbox), Tab(Routes.MY_TRIPS, "My Trips", Icons.Filled.Schedule),
    Tab(Routes.EARNINGS, "Earnings", Icons.Filled.Payments), Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverApp(vm: DriverViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val requests by vm.openRequests.collectAsStateWithLifecycle()
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
    // Recompute slot locks whenever trips change.
    val blockedFn: (TripRequest) -> Boolean = remember(trips) { { r -> vm.isSlotBlocked(r) } }
    var bidSheet by remember { mutableStateOf<Bid?>(null) }

    Scaffold(
        containerColor = DP.Bg,
        bottomBar = {
            if (current in tabs.map { it.route }) NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(selected = current == t.route, onClick = { nav.tab(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = DP.Primary500, selectedTextColor = DP.Primary500, indicatorColor = DP.Primary100))
                }
            }
        },
    ) { outer ->
        NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.padding(bottom = if (current in tabs.map { it.route }) outer.calculateBottomPadding() else 0.dp)) {
            composable(Routes.SPLASH) { SplashScreen { nav.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } } }
            composable(Routes.LOGIN) { PhoneLoginScreen(auth, vm::setPhone) { vm.sendOtp { nav.navigate(Routes.OTP) } } }
            composable(Routes.OTP) { OtpScreen(auth.phone, auth.error, { nav.popBackStack() }) { code -> vm.verifyOtp(code) { kyc -> nav.navigate(if (kyc) Routes.HOME else Routes.KYC_VEHICLE) } } }
            composable(Routes.KYC_VEHICLE) { KycVehicleScreen(auth.kycType, vm::setKycType, { nav.popBackStack() }) { nav.navigate(Routes.KYC_DOCS) } }
            composable(Routes.KYC_DOCS) { KycDocumentsScreen(auth.uploaded, vm::toggleDoc, { nav.popBackStack() }) { vm.submitKyc { nav.navigate(Routes.KYC_REVIEW) } } }
            composable(Routes.KYC_REVIEW) { KycReviewScreen { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } } }

            composable(Routes.HOME) {
                HomeScreen(driver, online, vm::setOnline, summary, bids.count { it.status == BidStatus.PENDING },
                    trips.filter { it.status == TripStatus.UPCOMING }.minByOrNull { it.slot.start }, requests, blockedFn,
                    onNotifications = { nav.navigate(Routes.NOTIFS) }, onUpcoming = { nav.navigate(Routes.trip(it.id)) },
                    onRequest = { nav.navigate(Routes.incoming(it.id)) }, onSkip = { vm.skip(it.id) }, onSeeAll = { nav.tab(Routes.REQUESTS) })
            }
            composable(Routes.REQUESTS) {
                RequestsScreen(requests, bids, blockedFn, 0, { nav.navigate(Routes.NOTIFS) }, { nav.navigate(Routes.incoming(it.id)) }, { bidSheet = it })
            }
            composable(Routes.INCOMING) { e ->
                val r = requests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable
                IncomingRequestScreen(r, vm.conflictFor(r), { nav.popBackStack() }, { vm.skip(r.id); nav.popBackStack() },
                    { vm.resetBid(r.suggestedMin); nav.navigate(Routes.bid(r.id)) }, { nav.navigate(Routes.conflict(r.id)) })
            }
            composable(Routes.BID) { e ->
                val r = requests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable
                PlaceBidScreen(r, bidForm, vm::setBidAmount, vm::setBidNote, { nav.popBackStack() }, { vm.submitBid(r.id) }) {
                    vm.resetBid(); nav.tab(Routes.REQUESTS)
                }
            }
            composable(Routes.CONFLICT) { e ->
                val r = requests.firstOrNull { it.id == e.arguments?.getString("id") } ?: return@composable
                SlotConflictScreen(r, vm.conflictFor(r), { nav.popBackStack() }) { nav.navigate(Routes.trip(it.id)) }
            }
            composable(Routes.BID_WON) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable
                BidWonScreen(t, { nav.navigate(Routes.trip(t.id)) }) { nav.tab(Routes.HOME) }
            }
            composable(Routes.MY_TRIPS) {
                MyTripsScreen(trips, predefined, { nav.navigate(Routes.trip(it.id)) }, { nav.navigate(Routes.predefForm(it.id)) }, { nav.navigate(Routes.predefForm(null)) },
                    { nav.navigate(Routes.CALENDAR) }, { nav.navigate(Routes.HISTORY) })
            }
            composable(Routes.TRIP) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable
                TripDetailScreen(t, { nav.popBackStack() }, { vm.setTripStatus(t.id, TripStatus.ON_THE_WAY); nav.navigate(Routes.live(t.id)) })
            }
            composable(Routes.LIVE) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable
                LiveTripScreen(t, { vm.setTripStatus(t.id, it) }, onCollect = { nav.navigate(Routes.collect(t.id)) })
            }
            composable(Routes.COLLECT) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable
                CollectPaymentScreen(t, { nav.popBackStack() }) { m -> vm.setTripStatus(t.id, TripStatus.COMPLETED, m); nav.navigate(Routes.done(t.id)) }
            }
            composable(Routes.COMPLETED) { e ->
                val t = trips.firstOrNull { it.id == e.arguments?.getString("tripId") } ?: return@composable
                TripCompletedScreen(t) { nav.tab(Routes.HOME) }
            }
            composable(Routes.PREDEF) { PredefinedListScreen(predefined, { nav.popBackStack() }, { nav.navigate(Routes.predefForm(it.id)) }) { nav.navigate(Routes.predefForm(null)) } }
            composable(Routes.PREDEF_FORM, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                val id = e.arguments?.getString("id")
                PredefinedFormScreen(predefined.firstOrNull { it.id == id }, { nav.popBackStack() }) { vm.savePredefined(it); nav.popBackStack() }
            }
            composable(Routes.CALENDAR) { AvailabilityScreen(trips, blocked, vm::toggleDay) { nav.popBackStack() } }
            composable(Routes.HISTORY) { TripHistoryScreen(trips, { nav.popBackStack() }) { nav.navigate(Routes.trip(it.id)) } }
            composable(Routes.RATINGS) { RatingsScreen(driver.rating, ratings) { nav.popBackStack() } }

            composable(Routes.EARNINGS) {
                EarningsScreen(summary, { vm.resetWithdraw(); nav.navigate(Routes.WITHDRAW) }, { nav.navigate(Routes.WALLET) }, { nav.navigate(Routes.RECON) }, { nav.navigate(Routes.RATINGS) }, driver.rating)
            }
            composable(Routes.WITHDRAW) {
                LaunchedEffect(wd.done) { if (wd.done != null) nav.navigate(Routes.WITHDRAW_OK) { popUpTo(Routes.WITHDRAW) { inclusive = true } } }
                WithdrawScreen(summary.walletBalance, vm.accounts, wd, vm::setWdAccount, vm::setWdAmount, vm::setPin, vm::confirmWithdraw) { nav.popBackStack() }
            }
            composable(Routes.WITHDRAW_OK) { wd.done?.let { WithdrawSuccessScreen(it, wd.account) { nav.tab(Routes.EARNINGS) } } }
            composable(Routes.RECON) { ReconciliationScreen(recon, vm::loadReconciliation) { nav.popBackStack() } }
            composable(Routes.WALLET) { WalletScreen(summary.walletBalance, txs, { nav.popBackStack() }) { vm.resetWithdraw(); nav.navigate(Routes.WITHDRAW) } }

            composable(Routes.PROFILE) {
                ProfileScreen(driver, { nav.navigate(Routes.VEHICLES) }, { nav.navigate(Routes.RATINGS) }, { nav.navigate(Routes.HISTORY) }, { nav.navigate(Routes.PREDEF) },
                    { nav.navigate(Routes.CALENDAR) }, { nav.navigate(Routes.WALLET) }, { nav.navigate(Routes.SUPPORT) }, { nav.navigate(Routes.NOTIFS) }) {
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            }
            composable(Routes.VEHICLES) { VehiclesScreen(driver) { nav.popBackStack() } }
            composable(Routes.NOTIFS) { NotificationsScreen(notifs) { nav.popBackStack() } }
            composable(Routes.SUPPORT) { SupportScreen { nav.popBackStack() } }
        }
    }

    bidSheet?.let { b ->
        ModalBottomSheet(onDismissRequest = { bidSheet = null }, containerColor = Color.White) {
            BidDetailSheetContent(b) {
                bidSheet = null
                vm.simulateWin(b.id) { trip -> nav.navigate(Routes.won(trip.id)) }
            }
        }
    }
}
