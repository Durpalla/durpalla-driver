package com.durpalla.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

fun TripStatus.label() = when (this) {
    TripStatus.UPCOMING -> "Upcoming"; TripStatus.ON_THE_WAY -> "On the way"; TripStatus.ARRIVED -> "Arrived"
    TripStatus.IN_PROGRESS -> "In progress"; TripStatus.COMPLETED -> "Completed"; TripStatus.CANCELLED -> "Cancelled"
}
fun TripStatus.tone() = when (this) { TripStatus.COMPLETED -> Tone.SUCCESS; TripStatus.CANCELLED -> Tone.DANGER; TripStatus.UPCOMING -> Tone.PRIMARY; else -> Tone.ACCENT }

@Composable
fun TripCard(t: Trip, onClick: () -> Unit) {
    DCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleBadge(t.vehicleType)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                RouteText(t.from, t.to, MaterialTheme.typography.titleSmall)
                Text("${t.slot.label()} · ${t.passengers} pax", color = DP.Muted, fontSize = 12.sp)
                Text("${t.code} · ${taka(t.fare)}", color = DP.Primary500, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            StatusChip(t.status.label(), t.status.tone())
        }
    }
}

@Composable
fun MyTripsScreen(trips: List<Trip>, predefined: List<PredefinedTrip>, onTrip: (Trip) -> Unit, onPredef: (PredefinedTrip) -> Unit, onCreate: () -> Unit, onCalendar: () -> Unit, onHistory: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = { DTopBar("My trips", actions = { IconButton(onClick = onCalendar) { Icon(Icons.Filled.CalendarMonth, "Availability") } }) },
        floatingActionButton = { if (tab == 1) ExtendedFloatingActionButton(onClick = onCreate, containerColor = DP.Accent, contentColor = Color.White, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New trip") }) },
    ) { pad ->
        ScreenColumn(pad) {
            SegmentedTabs(listOf("Upcoming", "Pre-defined", "History"), tab, { tab = it })
            when (tab) {
                0 -> trips.filter { it.status !in setOf(TripStatus.COMPLETED, TripStatus.CANCELLED) }.ifEmpty { null }?.forEach { TripCard(it) { onTrip(it) } } ?: EmptyState("No upcoming trips")
                1 -> PredefinedList(predefined, onPredef)
                else -> { trips.filter { it.status == TripStatus.COMPLETED || it.status == TripStatus.CANCELLED }.take(3).forEach { TripCard(it) { onTrip(it) } }; SecondaryButton("Full trip history", onHistory) }
            }
        }
    }
}

@Composable
fun PredefinedList(list: List<PredefinedTrip>, onClick: (PredefinedTrip) -> Unit) {
    Text("Fixed-rent trips customers can search and book directly. Bookings lock the slot automatically.", color = DP.Muted, fontSize = 12.sp)
    list.forEach { p ->
        DCard(onClick = { onClick(p) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VehicleBadge(p.vehicleType, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    RouteText(p.from, p.to, MaterialTheme.typography.titleSmall)
                    Text("${p.departure} · ${p.days.joinToString()} · ${p.seats} seats", color = DP.Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(taka(p.rent), fontWeight = FontWeight.Bold, color = DP.Primary500)
                    StatusChip(if (p.active) "Active" else "Paused", if (p.active) Tone.SUCCESS else Tone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
fun PredefinedListScreen(list: List<PredefinedTrip>, onBack: () -> Unit, onEdit: (PredefinedTrip) -> Unit, onCreate: () -> Unit) {
    Scaffold(topBar = { DTopBar("Pre-defined trips", onBack) }, bottomBar = { StickyCta { PrimaryButton("+ Create trip", onCreate, accent = true) } }) { pad ->
        ScreenColumn(pad) { PredefinedList(list, onEdit) }
    }
}

private val weekDays = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PredefinedFormScreen(initial: PredefinedTrip?, onBack: () -> Unit, onSave: (PredefinedTrip) -> Unit) {
    var type by remember { mutableStateOf(initial?.vehicleType ?: VehicleType.SPEEDBOAT) }
    var from by remember { mutableStateOf(initial?.from ?: "") }
    var to by remember { mutableStateOf(initial?.to ?: "") }
    var time by remember { mutableStateOf(initial?.departure ?: "8:00 AM") }
    var days by remember { mutableStateOf(initial?.days ?: setOf("Fri")) }
    var seats by remember { mutableStateOf(initial?.seats?.toString() ?: "10") }
    var rent by remember { mutableStateOf(initial?.rent?.toString() ?: "") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    val valid = from.isNotBlank() && to.isNotBlank() && rent.toIntOrNull() != null && days.isNotEmpty()
    Scaffold(topBar = { DTopBar(if (initial == null) "Create trip" else "Edit trip", onBack) }, bottomBar = {
        StickyCta {
            PrimaryButton("Save trip", { onSave(PredefinedTrip(initial?.id ?: "p${System.nanoTime()}", type, from, to, time, days, seats.toIntOrNull() ?: 1, rent.toInt(), active)) }, enabled = valid)
        }
    }) { pad ->
        ScreenColumn(pad) {
            Text("Vehicle", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach { FilterChip(it == type, { type = it }, { Text("${it.emoji} ${it.label}") }) }
            }
            OutlinedTextField(from, { from = it }, label = { Text("From (ghat / pickup)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(to, { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Departure time") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Runs on", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekDays.forEach { d -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(d) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(seats, { seats = it.filter(Char::isDigit) }, label = { Text("Seats") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rent, { rent = it.filter(Char::isDigit) }, label = { Text("Fixed rent") }, prefix = { Text("৳") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            rent.toIntOrNull()?.let { Text("You'll earn ${taka(Commission.netOf(it))} after ${Commission.RATE_PERCENT}% commission", color = DP.Success, fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Visible to customers", Modifier.weight(1f)); Switch(active, { active = it }) }
        }
    }
}

@Composable
fun AvailabilityScreen(trips: List<Trip>, blocked: Set<LocalDate>, onToggle: (LocalDate) -> Unit, onBack: () -> Unit) {
    val month = YearMonth.now()
    val booked = trips.filter { it.status == TripStatus.UPCOMING }.map { it.slot.start.toLocalDate() }.toSet()
    Scaffold(topBar = { DTopBar("Availability", onBack) }) { pad ->
        ScreenColumn(pad) {
            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}", style = MaterialTheme.typography.titleLarge)
            DCard {
                Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = DP.Muted, fontSize = 12.sp) } }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        (0 until 7).forEach { i ->
                            val d = week.getOrNull(i)
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    val (bg, fg) = when {
                                        d in booked -> DP.Primary500 to Color.White
                                        d in blocked -> DP.DangerSoft to DP.Danger
                                        d.isBefore(LocalDate.now()) -> Color.Transparent to DP.Line
                                        else -> DP.SuccessSoft to DP.Success
                                    }
                                    Box(Modifier.fillMaxSize().clip(CircleShape).background(bg).clickable(enabled = d !in booked && !d.isBefore(LocalDate.now())) { onToggle(d) }, contentAlignment = Alignment.Center) {
                                        Text("${d.dayOfMonth}", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChip("Available", Tone.SUCCESS); StatusChip("Booked (locked)", Tone.PRIMARY); StatusChip("Off", Tone.DANGER) }
            Text("Tap a free day to mark it off. Booked days are locked by confirmed trips.", color = DP.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TripDetailScreen(t: Trip, onBack: () -> Unit, onStart: () -> Unit, onCall: () -> Unit = {}) {
    Scaffold(topBar = { DTopBar("Trip ${t.code}", onBack) }, bottomBar = {
        if (t.status == TripStatus.UPCOMING) StickyCta { PrimaryButton("Start trip", onStart, accent = true) }
    }) { pad ->
        ScreenColumn(pad) {
            MapPlaceholder(t.from, t.to, height = 160.dp)
            DCard {
                Row { RouteText(t.from, t.to, MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f)); StatusChip(t.status.label(), t.status.tone()) }
                KeyValue("Slot", t.slot.label()); KeyValue("Vehicle", t.vehicleType.label); KeyValue("Passengers", "${t.passengers}")
                if (t.fromPredefined) KeyValue("Booking", "Pre-defined trip")
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(t.customer.name)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t.customer.name, style = MaterialTheme.typography.titleSmall); RatingText(t.customer.rating) }
                    FilledIconButton(onClick = onCall, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DP.Primary100, contentColor = DP.Primary500)) { Icon(Icons.Filled.Call, "Call") }
                }
            }
            DCard {
                KeyValue("Agreed rent", taka(t.fare), bold = true); KeyValue("Advance paid (online)", taka(t.advancePaid), DP.Success)
                KeyValue("To collect", taka(t.fare - t.advancePaid), DP.Accent, true); KeyValue("Commission (${Commission.RATE_PERCENT}%)", taka(Commission.of(t.fare)))
                t.paymentMethod?.let { KeyValue("Paid via", it.label) }
            }
        }
    }
}

@Composable
fun LiveTripScreen(t: Trip, onAdvance: (TripStatus) -> Unit, onCollect: () -> Unit, onSos: () -> Unit = {}) {
    val (label, next) = when (t.status) {
        TripStatus.UPCOMING, TripStatus.ON_THE_WAY -> "I've arrived at pickup" to TripStatus.ARRIVED
        TripStatus.ARRIVED -> "Start ride" to TripStatus.IN_PROGRESS
        else -> "End trip & collect" to null
    }
    Scaffold(bottomBar = { StickyCta { PrimaryButton(label, { if (next != null) onAdvance(next) else onCollect() }, accent = next == null) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f)) {
                MapPlaceholder(t.from, t.to, Modifier.fillMaxSize(), height = 400.dp)
                FilledTonalButton(onClick = onSos, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = DP.Danger, contentColor = Color.White)) { Text("SOS") }
            }
            Column(Modifier.background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(if (t.status == TripStatus.UPCOMING) TripStatus.ON_THE_WAY.label() else t.status.label(), Tone.ACCENT)
                RouteText(t.from, t.to, MaterialTheme.typography.titleLarge)
                Text("${t.customer.name} · ${t.passengers} pax · ${t.code}", color = DP.Muted)
                LinearProgressIndicator(progress = { when (t.status) { TripStatus.ARRIVED -> 0.35f; TripStatus.IN_PROGRESS -> 0.7f; else -> 0.1f } }, modifier = Modifier.fillMaxWidth(), color = DP.Primary500, trackColor = DP.Primary100)
            }
        }
    }
}

@Composable
fun CollectPaymentScreen(t: Trip, onBack: () -> Unit, onConfirm: (PaymentMethod) -> Unit) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    val due = t.fare - t.advancePaid
    Scaffold(topBar = { DTopBar("Collect payment", onBack) }, bottomBar = { StickyCta { PrimaryButton("Confirm ${taka(due)} received", { onConfirm(method) }, accent = true) } }) { pad ->
        ScreenColumn(pad) {
            DCard(color = DP.Primary500, border = null) {
                Text("Amount to collect", color = DP.Primary200); Text(taka(due), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("Rent ${taka(t.fare)} − advance ${taka(t.advancePaid)}", color = DP.Primary200, fontSize = 12.sp)
            }
            Text("Payment method", style = MaterialTheme.typography.titleSmall)
            listOf(PaymentMethod.CASH, PaymentMethod.BKASH, PaymentMethod.NAGAD).forEach { m ->
                DCard(onClick = { method = m }, border = if (m == method) DP.Primary500 else DP.Line) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); RadioButton(m == method, { method = m }) }
                }
            }
            if (method == PaymentMethod.CASH) Text("Cash trips: ${Commission.RATE_PERCENT}% commission (${taka(Commission.of(t.fare))}) is deducted from your wallet during reconciliation.", color = DP.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TripCompletedScreen(t: Trip, onDone: () -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    Scaffold(bottomBar = { StickyCta { PrimaryButton("Done", onDone) } }) { pad ->
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.CheckCircle, null, tint = DP.Success, modifier = Modifier.size(80.dp))
            Text("Trip completed", style = MaterialTheme.typography.headlineSmall)
            DCard {
                KeyValue("Trip", t.code); KeyValue("Rent", taka(t.fare)); KeyValue("Commission", "-" + taka(Commission.of(t.fare)), DP.Danger)
                HorizontalDivider(color = DP.Line); KeyValue("Your earning", taka(Commission.netOf(t.fare)), DP.Success, true)
            }
            Text("Rate ${t.customer.name}", style = MaterialTheme.typography.titleSmall)
            Row { (1..5).forEach { i -> Icon(Icons.Filled.Star, null, tint = if (i <= stars) DP.Warning else DP.Line, modifier = Modifier.size(40.dp).clickable { stars = i }) } }
        }
    }
}

@Composable
fun TripHistoryScreen(trips: List<Trip>, onBack: () -> Unit, onTrip: (Trip) -> Unit) {
    val done = trips.filter { it.status == TripStatus.COMPLETED || it.status == TripStatus.CANCELLED }
    Scaffold(topBar = { DTopBar("Trip history", onBack) }) { pad ->
        ScreenColumn(pad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Completed", "${done.count { it.status == TripStatus.COMPLETED }}", Modifier.weight(1f))
                StatTile("Cancelled", "${done.count { it.status == TripStatus.CANCELLED }}", Modifier.weight(1f), DP.Danger)
                StatTile("Earned", taka(done.filter { it.status == TripStatus.COMPLETED }.sumOf { Commission.netOf(it.fare) }), Modifier.weight(1.3f), DP.Success)
            }
            done.forEach { TripCard(it) { onTrip(it) } }
        }
    }
}

@PhonePreview @Composable private fun MyTripsPreview() = P { MyTripsScreen(SampleData.trips, SampleData.predefined, {}, {}, {}, {}, {}) }
@PhonePreview @Composable private fun PredefListPreview() = P { PredefinedListScreen(SampleData.predefined, {}, {}, {}) }
@PhonePreview @Composable private fun PredefFormPreview() = P { PredefinedFormScreen(SampleData.predefined[0], {}, {}) }
@PhonePreview @Composable private fun AvailabilityPreview() = P { AvailabilityScreen(SampleData.trips, setOf(LocalDate.now().plusDays(3)), {}, {}) }
@PhonePreview @Composable private fun TripDetailPreview() = P { TripDetailScreen(SampleData.trips[0], {}, {}) }
@PhonePreview @Composable private fun LivePreview() = P { LiveTripScreen(SampleData.trips[0].copy(status = TripStatus.IN_PROGRESS), {}, onCollect = {}) }
@PhonePreview @Composable private fun CollectPreview() = P { CollectPaymentScreen(SampleData.trips[0], {}, {}) }
@PhonePreview @Composable private fun CompletedPreview() = P { TripCompletedScreen(SampleData.trips[1]) {} }
@PhonePreview @Composable private fun HistoryPreview() = P { TripHistoryScreen(SampleData.trips, {}, {}) }
