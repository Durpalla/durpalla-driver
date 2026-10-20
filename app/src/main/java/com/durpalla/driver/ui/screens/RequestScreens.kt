package com.durpalla.driver.ui.screens

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
import com.durpalla.driver.data.SampleData
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
                Text("Good afternoon", color = DP.Muted, fontSize = 12.sp)
                Text("${driver.name} ★ ${driver.rating}", style = MaterialTheme.typography.titleMedium)
            }
            if (online) LiveIndicator(connection)
            IconButton(onClick = onNotifications) { BadgedBox(badge = { Badge(containerColor = DP.Accent) }) { Icon(Icons.Filled.Notifications, "Notifications") } }
        }
        ScreenColumn(PaddingValues(0.dp)) {
            DCard(color = if (online) DP.SuccessSoft else Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (online) "You are Online" else "You are Offline", style = MaterialTheme.typography.titleMedium)
                        Text(if (online) "Receiving requests near ${driver.area.substringBefore(",")}" else "Go online to receive trip requests", color = DP.Muted, fontSize = 12.sp)
                    }
                    Switch(checked = online, onCheckedChange = onOnline, colors = brandSwitchColors())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Today", taka(summary.today), Modifier.weight(1f))
                StatTile("Trips", "${summary.todayTrips}", Modifier.weight(1f))
                StatTile("Active bids", "$activeBids", Modifier.weight(1f), DP.Accent)
            }
            if (upcoming != null) {
                DCard(color = DP.Primary500, border = null) {
                    Row { Text("UPCOMING CONFIRMED", color = DP.Primary200, fontSize = 11.sp, modifier = Modifier.weight(1f)); StatusChip("Slot locked", Tone.NEUTRAL) }
                    Text("${upcoming.from} → ${upcoming.to}", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("${upcoming.slot.label()} · ${upcoming.passengers} pax · ${taka(upcoming.fare)}", color = DP.Primary200, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("View trip", { onUpcoming(upcoming) }, accent = true)
                }
            }
            SectionHeader("New requests", "See all", onSeeAll)
            if (!online) EmptyState("You're offline")
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
                Text("${r.slot.label()} · ${r.passengers} pax", color = DP.Muted, fontSize = 12.sp)
                RatingText(r.customer.rating, " customer · ${taka(r.suggestedMin)}–${taka(r.suggestedMax)}")
            }
            if (blocked) StatusChip("Slot booked", Tone.DANGER) else StatusChip(r.vehicleType.label, Tone.PRIMARY)
        }
        if (onSkip != null && !blocked) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Skip", onSkip, Modifier.weight(1f))
                PrimaryButton("Place bid", onClick, Modifier.weight(1f), accent = true)
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
        Text(" ${left / 60}:${(left % 60).toString().padStart(2, '0')} left to bid", color = if (urgent) DP.Danger else Color(0xFFB45309), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun IncomingRequestScreen(r: TripRequest, conflict: Trip?, onBack: () -> Unit, onSkip: () -> Unit, onBid: () -> Unit, onConflict: () -> Unit,
    live: LiveBid? = null, connection: ConnectionState = ConnectionState.LIVE) {
    Scaffold(
        topBar = { DTopBar("Trip request", onBack) },
        bottomBar = {
            StickyCta {
                if (conflict != null) PrimaryButton("Slot booked · see why", onConflict, enabled = true)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Skip", onSkip, Modifier.weight(1f)); PrimaryButton("Place bid", onBid, Modifier.weight(1.4f), accent = true)
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
                KeyValue("Slot", r.slot.label()); KeyValue("Passengers", "${r.passengers}"); KeyValue("Distance", "${r.distanceKm} km")
                KeyValue("Suggested rent", "${taka(r.suggestedMin)} – ${taka(r.suggestedMax)}", DP.Primary500, true)
                if (r.note.isNotBlank()) KeyValue("Note", r.note)
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.customer.name)
                    Column(Modifier.padding(start = 12.dp)) { Text(r.customer.name, style = MaterialTheme.typography.titleSmall); RatingText(r.customer.rating, " · ${r.customer.trips} trips") }
                }
            }
            if (conflict != null) DCard(color = DP.DangerSoft, border = null) {
                Text("You have ${conflict.code} (${conflict.slot.timeLabel()}) in this slot. Bidding is disabled.", color = DP.Danger)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceBidScreen(r: TripRequest, form: BidFormState, onAmount: (String) -> Unit, onNote: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit, onDone: () -> Unit,
    live: LiveBid? = null, connection: ConnectionState = ConnectionState.LIVE) {
    val amt = form.amountInt ?: 0
    Scaffold(topBar = { DTopBar("Place your bid", onBack) }, bottomBar = {
        StickyCta { PrimaryButton(if (form.submitting) "Submitting…" else "Place bid ${if (amt > 0) taka(amt) else ""}", onSubmit, enabled = amt > 0, accent = true, loading = form.submitting) }
    }) { pad ->
        ScreenColumn(pad) {
            DCard { RouteText(r.from, r.to); Text("${r.slot.label()} · ${r.passengers} pax", color = DP.Muted, fontSize = 12.sp) }
            LiveBidPanel(r, live, connection)
            DCard(color = DP.Primary100, border = null) {
                Text("Suggested range", color = DP.Primary600, fontSize = 12.sp)
                Text("${taka(r.suggestedMin)} – ${taka(r.suggestedMax)}", style = MaterialTheme.typography.titleLarge, color = DP.Primary700)
                Text("Based on distance, slot & recent trips on this route", color = DP.Muted, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = form.amount, onValueChange = onAmount, label = { Text("Your rent (৳)") }, prefix = { Text("৳ ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                isError = form.error != null, supportingText = { form.error?.let { Text(it) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(r.suggestedMin, (r.suggestedMin + r.suggestedMax) / 2, r.suggestedMax).forEach { v ->
                    DurpallaButton(taka(v), { onAmount(v.toString()) }, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Small)
                }
            }
            OutlinedTextField(form.note, onNote, label = { Text("Note to customer (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
            DCard {
                KeyValue("Your bid", taka(amt))
                KeyValue("Durpalla commission (${Commission.RATE_PERCENT}%)", "-" + taka(Commission.of(amt)), DP.Danger)
                HorizontalDivider(color = DP.Line)
                KeyValue("You'll earn", taka(Commission.netOf(amt)), DP.Success, true)
            }
        }
    }
    if (form.submitted != null) {
        ModalBottomSheet(onDismissRequest = onDone, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = DP.Success, modifier = Modifier.size(56.dp))
                Text("Bid submitted", style = MaterialTheme.typography.titleLarge)
                Text("${taka(form.submitted.amount)} for ${r.from} → ${r.to}. You'll get an instant alert when the customer chooses.", color = DP.Muted, textAlign = TextAlign.Center)
                PrimaryButton("Go to My Bids", onDone)
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
    Scaffold(topBar = { DTopBar("Requests", actions = { LiveIndicator(connection); IconButton(onClick = onNotifications) { Icon(Icons.Filled.Notifications, null) } }) }) { pad ->
        ScreenColumn(pad) {
            SegmentedTabs(listOf("New (${requests.size})", "My bids"), tab, { tab = it })
            if (tab == 0) {
                if (requests.isEmpty()) EmptyState("No new requests nearby")
                requests.forEach { r -> RequestCard(r, isBlocked(r), { onRequest(r) }) }
            } else {
                val statuses = listOf(null) + BidStatus.entries
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    FilterChips(listOf("All") + BidStatus.entries.map { s -> "${s.label} ${bids.count { it.status == s }}" }, filter) { filter = it }
                }
                val shown = bids.filter { statuses[filter] == null || it.status == statuses[filter] }
                if (shown.isEmpty()) EmptyState("No bids here")
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
                Text("Your bid ${taka(b.amount)}", color = DP.Primary500, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(b.status.label, b.status.tone())
                Text(when (b.status) { BidStatus.PENDING -> "${b.request.bidsCount} bids"; BidStatus.WON -> "Slot locked"; BidStatus.LOST -> "Customer chose another"; BidStatus.EXPIRED -> "No choice made" }, color = DP.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BidDetailSheetContent(b: Bid, onSimulateWin: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteText(b.request.from, b.request.to)
        KeyValue("Your bid", taka(b.amount), bold = true); KeyValue("Status", b.status.label)
        if (b.status == BidStatus.PENDING) SecondaryButton("Demo: customer chooses me", onSimulateWin)
    }
}

@Composable
fun BidWonScreen(trip: Trip, onViewTrip: () -> Unit, onHome: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton("View trip details", onViewTrip); SecondaryButton("Back to home", onHome) } }) { pad ->
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(100.dp).clip(CircleShape).background(DP.SuccessSoft), contentAlignment = Alignment.Center) { Text("🎉", fontSize = 48.sp) }
            Text("You won the bid!", style = MaterialTheme.typography.headlineSmall)
            Text("${trip.customer.name} chose you. This slot is now locked; overlapping requests will show “Slot booked”.", color = DP.Muted, textAlign = TextAlign.Center)
            DCard {
                RouteText(trip.from, trip.to); KeyValue("Slot", trip.slot.label()); KeyValue("Passengers", "${trip.passengers}")
                KeyValue("Agreed rent", taka(trip.fare), bold = true); KeyValue("Advance paid by customer", taka(trip.advancePaid), DP.Success)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Lock, null, tint = DP.Primary500, modifier = Modifier.size(16.dp)); Text(" Slot ${trip.slot.timeLabel()} locked", color = DP.Primary500, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun SlotConflictScreen(request: TripRequest, conflict: Trip?, onBack: () -> Unit, onViewTrip: (Trip) -> Unit) {
    Scaffold(topBar = { DTopBar("Slot booked", onBack) }, bottomBar = {
        StickyCta { if (conflict != null) PrimaryButton("View my booked trip", { onViewTrip(conflict) }); SecondaryButton("Back to requests", onBack) }
    }) { pad ->
        ScreenColumn(pad) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(DP.DangerSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.EventBusy, null, tint = DP.Danger, modifier = Modifier.size(44.dp)) }
            }
            Text("You're already booked in this slot", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("A confirmed trip locks your vehicle for that time (plus a 30-min buffer). You can't bid on or accept overlapping requests.", color = DP.Muted, textAlign = TextAlign.Center)
            DCard(border = DP.Danger) { Text("This request", color = DP.Muted, fontSize = 12.sp); RouteText(request.from, request.to); Text(request.slot.label(), color = DP.Danger) }
            if (conflict != null) DCard(border = DP.Primary500) { Text("Booked trip ${conflict.code}", color = DP.Muted, fontSize = 12.sp); RouteText(conflict.from, conflict.to); Text(conflict.slot.label(), color = DP.Primary500) }
        }
    }
}

@PhonePreview @Composable private fun HomePreview() = P {
    HomeScreen(SampleData.driver, true, {}, SampleData.summary, 5, SampleData.trips[0], SampleData.requests, { it.id == "r3" }, {}, {}, {}, {}, {})
}
@PhonePreview @Composable private fun IncomingPreview() = P { IncomingRequestScreen(SampleData.requests[0], null, {}, {}, {}, {}) }
@PhonePreview @Composable private fun PlaceBidPreview() = P {
    PlaceBidScreen(SampleData.requests[0], BidFormState(amount = "3200"), {}, {}, {}, {}, {}, LiveBid("r1", 5, 3000, "b1", 3200, BidStatus.PENDING), ConnectionState.LIVE)
}
@PhonePreview @Composable private fun RequestsPreview() = P { RequestsScreen(SampleData.requests, emptyList(), { false }, 0, {}, {}, {}) }
@PhonePreview @Composable private fun MyBidsPreview() = P {
    val r = SampleData.requests
    RequestsScreen(r, BidStatus.entries.mapIndexed { i, s -> Bid("b$i", r[i].id, r[i], 3000 + i * 100, "", s) }, { false }, 1, {}, {}, {})
}
@PhonePreview @Composable private fun BidWonPreview() = P { BidWonScreen(SampleData.trips[0], {}, {}) }
@PhonePreview @Composable private fun ConflictPreview() = P { SlotConflictScreen(SampleData.requests[2], SampleData.trips[0], {}, {}) }
