package com.durpalla.driver.ui.screens

import androidx.compose.ui.res.stringResource
import com.durpalla.driver.R
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.plural
import com.durpalla.driver.ui.pax
import com.durpalla.driver.ui.title
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.i18n.asString
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.BidFormState
import com.durpalla.driver.realtime.ConnectionState
import com.durpalla.driver.realtime.LiveBid
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    driver: Driver, online: Boolean, onOnline: (Boolean) -> Unit, summary: EarningsSummary,
    activeBids: Int, upcoming: Trip?, requests: List<TripRequest>, isBlocked: (TripRequest) -> Boolean,
    onNotifications: () -> Unit, onUpcoming: (Trip) -> Unit, onRequest: (TripRequest) -> Unit, onSkip: (TripRequest) -> Unit, onSeeAll: () -> Unit,
    connection: ConnectionState = ConnectionState.LIVE,
) {
    Column(Modifier.fillMaxSize().background(DP.Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(driver.name)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(greetingRes()), color = DP.Muted, fontSize = 12.sp)
                Text("${driver.name} ★ ${num(driver.rating)}", style = MaterialTheme.typography.titleMedium)
            }
            if (online) LiveIndicator(connection)
            IconButton(onClick = onNotifications) { BadgedBox(badge = { Badge(containerColor = DP.Accent) }) { Icon(Icons.Filled.Notifications, stringResource(R.string.notifications)) } }
        }
        ScreenColumn(PaddingValues(0.dp)) {
            DCard(color = if (online) DP.SuccessSoft else Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (online) R.string.online else R.string.offline), style = MaterialTheme.typography.titleMedium)
                        Text(if (online) str(R.string.online_sub, driver.area.substringBefore(",")) else stringResource(R.string.offline_sub), color = DP.Muted, fontSize = 12.sp)
                    }
                    Switch(checked = online, onCheckedChange = onOnline, colors = brandSwitchColors())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.today), taka(summary.today), Modifier.weight(1f))
                StatTile(stringResource(R.string.trips), summary.todayTrips.l(), Modifier.weight(1f))
                StatTile(stringResource(R.string.active_bids), activeBids.l(), Modifier.weight(1f), DP.Accent)
            }
            if (upcoming != null) {
                DCard(color = DP.Primary500, border = null) {
                    Row { Text(stringResource(R.string.upcoming_confirmed), color = DP.Primary200, fontSize = 11.sp, modifier = Modifier.weight(1f)); StatusChip(stringResource(R.string.slot_locked), Tone.NEUTRAL) }
                    Text(str(R.string.route_from_to, upcoming.from, upcoming.to), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(str(R.string.dot_join3, upcoming.slot.label(), pax(upcoming.passengers), taka(upcoming.fare)), color = DP.Primary200, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(stringResource(R.string.view_trip), { onUpcoming(upcoming) }, accent = true)
                }
            }
            SectionHeader(stringResource(R.string.new_requests), stringResource(R.string.see_all), onSeeAll)
            if (!online) EmptyState(stringResource(R.string.youre_offline))
            else requests.take(3).forEach { r -> RequestCard(r, isBlocked(r), onClick = { onRequest(r) }, onSkip = { onSkip(r) }) }
        }
    }
}

@Composable
fun RequestCard(r: TripRequest, blocked: Boolean, onClick: () -> Unit, onSkip: (() -> Unit)? = null) {
    DCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleBadge(r.vehicleType)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                RouteText(r.from, r.to)
                Text(str(R.string.dot_join, r.slot.label(), pax(r.passengers)), color = DP.Muted, fontSize = 12.sp)
                RatingText(r.customer.rating, str(R.string.customer_range, taka(r.suggestedMin), taka(r.suggestedMax)))
            }
            if (blocked) StatusChip(stringResource(R.string.slot_booked), Tone.DANGER) else StatusChip(r.vehicleType.title(), Tone.PRIMARY)
        }
        if (onSkip != null && !blocked) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.skip), onSkip, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.place_bid), onClick, Modifier.weight(1f), accent = true)
            }
        }
    }
}

@Composable
fun Countdown(totalSec: Int, onExpire: () -> Unit = {}) {
    var left by remember(totalSec) { mutableIntStateOf(totalSec) }
    LaunchedEffect(left) { if (left > 0) { delay(1000); left-- } else onExpire() }
    val urgent = left < 30
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (urgent) DP.DangerSoft else DP.AccentSoft).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Timer, null, tint = if (urgent) DP.Danger else DP.Accent, modifier = Modifier.size(16.dp))
        Text(str(R.string.left_to_bid, "${left / 60}:${(left % 60).toString().padStart(2, '0')}"), color = if (urgent) DP.Danger else Color(0xFFB45309), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun IncomingRequestScreen(r: TripRequest, conflict: Trip?, onBack: () -> Unit, onSkip: () -> Unit, onBid: () -> Unit, onConflict: () -> Unit,
    live: LiveBid? = null, connection: ConnectionState = ConnectionState.LIVE) {
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.trip_request), onBack) },
        bottomBar = {
            StickyCta {
                if (conflict != null) PrimaryButton(stringResource(R.string.slot_booked_why), onConflict, enabled = true)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.skip), onSkip, Modifier.weight(1f)); PrimaryButton(stringResource(R.string.place_bid), onBid, Modifier.weight(1.4f), accent = true)
                }
            }
        },
    ) { pad ->
        ScreenColumn(pad) {
            MapPlaceholder(r.from, r.to)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Countdown(r.expiresInSec); Spacer(Modifier.weight(1f)); LiveIndicator(connection)
            }
            LiveBidPanel(r, live, connection)
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) { VehicleBadge(r.vehicleType); Spacer(Modifier.width(12.dp)); RouteText(r.from, r.to, MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.height(8.dp))
                KeyValue(stringResource(R.string.slot), r.slot.label()); KeyValue(stringResource(R.string.passengers), r.passengers.l()); KeyValue(stringResource(R.string.distance), str(R.string.km, num(r.distanceKm)))
                KeyValue(stringResource(R.string.suggested_rent), str(R.string.range, taka(r.suggestedMin), taka(r.suggestedMax)), DP.Primary500, true)
                if (r.note.isNotBlank()) KeyValue(stringResource(R.string.note), r.note.toLocalDigits())
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.customer.name)
                    Column(Modifier.padding(start = 12.dp)) { Text(r.customer.name, style = MaterialTheme.typography.titleSmall); RatingText(r.customer.rating, " · " + plural(R.plurals.trips_count, r.customer.trips)) }
                }
            }
            if (conflict != null) DCard(color = DP.DangerSoft, border = null) {
                Text(str(R.string.conflict_note, conflict.code, conflict.slot.timeLabel()), color = DP.Danger)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceBidScreen(r: TripRequest, form: BidFormState, onAmount: (String) -> Unit, onNote: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit, onDone: () -> Unit,
    live: LiveBid? = null, connection: ConnectionState = ConnectionState.LIVE) {
    val amt = form.amountInt ?: 0
    Scaffold(topBar = { DTopBar(stringResource(R.string.place_your_bid), onBack) }, bottomBar = {
        StickyCta { PrimaryButton(if (form.submitting) stringResource(R.string.submitting) else if (amt > 0) str(R.string.place_bid_amount, taka(amt)) else stringResource(R.string.place_bid), onSubmit, enabled = amt > 0, accent = true, loading = form.submitting) }
    }) { pad ->
        ScreenColumn(pad) {
            DCard { RouteText(r.from, r.to); Text(str(R.string.dot_join, r.slot.label(), pax(r.passengers)), color = DP.Muted, fontSize = 12.sp) }
            LiveBidPanel(r, live, connection)
            DCard(color = DP.Primary100, border = null) {
                Text(stringResource(R.string.suggested_range), color = DP.Primary600, fontSize = 12.sp)
                Text(str(R.string.range, taka(r.suggestedMin), taka(r.suggestedMax)), style = MaterialTheme.typography.titleLarge, color = DP.Primary700)
                Text(stringResource(R.string.suggested_basis), color = DP.Muted, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = form.amount.toLocalDigits(), onValueChange = onAmount, label = { Text(stringResource(R.string.your_rent)) }, prefix = { Text(stringResource(R.string.taka_prefix)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                isError = form.error != null, supportingText = { form.error?.let { Text(it.asString()) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(r.suggestedMin, (r.suggestedMin + r.suggestedMax) / 2, r.suggestedMax).forEach { v ->
                    DurpallaButton(taka(v), { onAmount(v.toString()) }, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Small)
                }
            }
            OutlinedTextField(form.note, onNote, label = { Text(stringResource(R.string.note_to_customer)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
            DCard {
                KeyValue(stringResource(R.string.your_bid), taka(amt))
                KeyValue(str(R.string.durpalla_commission_pct, Commission.RATE_PERCENT), "-" + taka(Commission.of(amt)), DP.Danger)
                HorizontalDivider(color = DP.Line)
                KeyValue(stringResource(R.string.youll_earn), taka(Commission.netOf(amt)), DP.Success, true)
            }
        }
    }
    if (form.submitted != null) {
        ModalBottomSheet(onDismissRequest = onDone, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = DP.Success, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.bid_submitted), style = MaterialTheme.typography.titleLarge)
                Text(str(R.string.bid_submitted_sub, taka(form.submitted.amount), r.from, r.to), color = DP.Muted, textAlign = TextAlign.Center)
                PrimaryButton(stringResource(R.string.go_my_bids), onDone)
            }
        }
    }
}

@Composable
fun RequestsScreen(
    requests: List<TripRequest>, bids: List<Bid>, isBlocked: (TripRequest) -> Boolean, initialTab: Int = 0,
    onNotifications: () -> Unit, onRequest: (TripRequest) -> Unit, onBid: (Bid) -> Unit,
    connection: ConnectionState = ConnectionState.LIVE,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { DTopBar(stringResource(R.string.requests), actions = { LiveIndicator(connection); IconButton(onClick = onNotifications) { Icon(Icons.Filled.Notifications, null) } }) }) { pad ->
        ScreenColumn(pad) {
            SegmentedTabs(listOf(str(R.string.tab_new, requests.size), stringResource(R.string.my_bids)), tab, { tab = it })
            if (tab == 0) {
                if (requests.isEmpty()) EmptyState(stringResource(R.string.no_requests))
                requests.forEach { r -> RequestCard(r, isBlocked(r), { onRequest(r) }) }
            } else {
                val statuses = listOf(null) + BidStatus.entries
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    FilterChips(listOf(stringResource(R.string.all)) + BidStatus.entries.map { s -> str(R.string.filter_count, s.title(), bids.count { it.status == s }) }, filter) { filter = it }
                }
                val shown = bids.filter { statuses[filter] == null || it.status == statuses[filter] }
                if (shown.isEmpty()) EmptyState(stringResource(R.string.no_bids))
                shown.forEach { b -> BidCard(b) { onBid(b) } }
            }
        }
    }
}

@Composable
fun BidCard(b: Bid, onClick: () -> Unit) {
    DCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleBadge(b.request.vehicleType)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                RouteText(b.request.from, b.request.to, MaterialTheme.typography.titleSmall)
                Text(b.request.slot.label(), color = DP.Muted, fontSize = 12.sp)
                Text(str(R.string.your_bid_amount, taka(b.amount)), color = DP.Primary500, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(b.status.title(), b.status.tone())
                Text(when (b.status) { BidStatus.PENDING -> plural(R.plurals.bids_count, b.request.bidsCount); BidStatus.WON -> stringResource(R.string.slot_locked); BidStatus.LOST -> stringResource(R.string.customer_chose_another); BidStatus.EXPIRED -> stringResource(R.string.no_choice) }, color = DP.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BidDetailSheetContent(b: Bid, onWithdraw: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteText(b.request.from, b.request.to)
        KeyValue(stringResource(R.string.your_bid), taka(b.amount), bold = true); KeyValue(stringResource(R.string.status), b.status.title())
        if (b.status == BidStatus.PENDING) SecondaryButton(stringResource(R.string.withdraw_bid), onWithdraw)
    }
}

@Composable
fun BidWonScreen(trip: Trip, onViewTrip: () -> Unit, onHome: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.view_trip_details), onViewTrip); SecondaryButton(stringResource(R.string.back_home), onHome) } }) { pad ->
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(100.dp).clip(CircleShape).background(DP.SuccessSoft), contentAlignment = Alignment.Center) { Text("🎉", fontSize = 48.sp) }
            Text(stringResource(R.string.won_title), style = MaterialTheme.typography.headlineSmall)
            Text(str(R.string.won_sub, trip.customer.name), color = DP.Muted, textAlign = TextAlign.Center)
            DCard {
                RouteText(trip.from, trip.to); KeyValue(stringResource(R.string.slot), trip.slot.label()); KeyValue(stringResource(R.string.passengers), trip.passengers.l())
                KeyValue(stringResource(R.string.agreed_rent), taka(trip.fare), bold = true); KeyValue(stringResource(R.string.advance_paid_customer), taka(trip.advancePaid), DP.Success)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Lock, null, tint = DP.Primary500, modifier = Modifier.size(16.dp)); Text(str(R.string.slot_x_locked, trip.slot.timeLabel()), color = DP.Primary500, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun SlotConflictScreen(request: TripRequest, conflict: Trip?, onBack: () -> Unit, onViewTrip: (Trip) -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.slot_booked), onBack) }, bottomBar = {
        StickyCta { if (conflict != null) PrimaryButton(stringResource(R.string.view_booked_trip), { onViewTrip(conflict) }); SecondaryButton(stringResource(R.string.back_requests), onBack) }
    }) { pad ->
        ScreenColumn(pad) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(DP.DangerSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.EventBusy, null, tint = DP.Danger, modifier = Modifier.size(44.dp)) }
            }
            Text(stringResource(R.string.already_booked), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.already_booked_sub), color = DP.Muted, textAlign = TextAlign.Center)
            DCard(border = DP.Danger) { Text(stringResource(R.string.this_request), color = DP.Muted, fontSize = 12.sp); RouteText(request.from, request.to); Text(request.slot.label(), color = DP.Danger) }
            if (conflict != null) DCard(border = DP.Primary500) { Text(str(R.string.booked_trip, conflict.code), color = DP.Muted, fontSize = 12.sp); RouteText(conflict.from, conflict.to); Text(conflict.slot.label(), color = DP.Primary500) }
        }
    }
}


private fun greetingRes(h: Int = java.time.LocalTime.now().hour) = when (h) { in 4..11 -> R.string.greeting_morning; in 12..16 -> R.string.greeting_afternoon; else -> R.string.greeting_evening }
