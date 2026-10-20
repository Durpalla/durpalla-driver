package com.durpalla.driver.ui.screens

import androidx.compose.ui.res.stringResource
import com.durpalla.driver.R
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.plural
import com.durpalla.driver.ui.pax
import com.durpalla.driver.ui.title
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.i18n.DigitFormatter
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

@Composable fun TripStatus.label() = title()
fun TripStatus.tone() = when (this) { TripStatus.COMPLETED -> Tone.SUCCESS; TripStatus.CANCELLED -> Tone.DANGER; TripStatus.UPCOMING -> Tone.PRIMARY; else -> Tone.ACCENT }

@Composable
fun TripCard(t: Trip, onClick: () -> Unit) {
    DCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleBadge(t.vehicleType)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                RouteText(t.from, t.to, MaterialTheme.typography.titleSmall)
                Text(str(R.string.dot_join, t.slot.label(), pax(t.passengers)), color = DP.Muted, fontSize = 12.sp)
                Text(str(R.string.dot_join, t.code, taka(t.fare)), color = DP.Primary500, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            StatusChip(t.status.label(), t.status.tone())
        }
    }
}

@Composable
fun MyTripsScreen(trips: List<Trip>, predefined: List<PredefinedTrip>, onTrip: (Trip) -> Unit, onPredef: (PredefinedTrip) -> Unit, onCreate: () -> Unit, onCalendar: () -> Unit, onHistory: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.my_trips), actions = { IconButton(onClick = onCalendar) { Icon(Icons.Filled.CalendarMonth, stringResource(R.string.availability)) } }) },
        floatingActionButton = { if (tab == 1) DurpallaFab(stringResource(R.string.new_trip), Icons.Filled.Add, onCreate) },
    ) { pad ->
        ScreenColumn(pad) {
            SegmentedTabs(listOf(stringResource(R.string.seg_upcoming), stringResource(R.string.seg_predefined), stringResource(R.string.seg_history)), tab, { tab = it })
            when (tab) {
                0 -> trips.filter { it.status !in setOf(TripStatus.COMPLETED, TripStatus.CANCELLED) }.ifEmpty { null }?.forEach { TripCard(it) { onTrip(it) } } ?: EmptyState(stringResource(R.string.no_upcoming))
                1 -> PredefinedList(predefined, onPredef)
                else -> { trips.filter { it.status == TripStatus.COMPLETED || it.status == TripStatus.CANCELLED }.take(3).forEach { TripCard(it) { onTrip(it) } }; SecondaryButton(stringResource(R.string.full_history), onHistory) }
            }
        }
    }
}

@Composable
fun PredefinedList(list: List<PredefinedTrip>, onClick: (PredefinedTrip) -> Unit) {
    Text(stringResource(R.string.predef_sub), color = DP.Muted, fontSize = 12.sp)
    list.forEach { p ->
        DCard(onClick = { onClick(p) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VehicleBadge(p.vehicleType, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    RouteText(p.from, p.to, MaterialTheme.typography.titleSmall)
                    Text(str(R.string.dot_join3, p.departure, weekDays.filter { it.first in p.days }.map { stringResource(it.second) }.joinToString(), plural(R.plurals.seats_count, p.seats)), color = DP.Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(taka(p.rent), fontWeight = FontWeight.Bold, color = DP.Primary500)
                    StatusChip(stringResource(if (p.active) R.string.active else R.string.paused), if (p.active) Tone.SUCCESS else Tone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
fun PredefinedListScreen(list: List<PredefinedTrip>, onBack: () -> Unit, onEdit: (PredefinedTrip) -> Unit, onCreate: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.predef_title), onBack) }, bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.create_trip_plus), onCreate, accent = true) } }) { pad ->
        ScreenColumn(pad) { PredefinedList(list, onEdit) }
    }
}

/** Stored/API day keys stay English (Sat…Fri); only the label is translated. */
private val weekDays = listOf("Sat" to R.string.day_sat, "Sun" to R.string.day_sun, "Mon" to R.string.day_mon, "Tue" to R.string.day_tue, "Wed" to R.string.day_wed, "Thu" to R.string.day_thu, "Fri" to R.string.day_fri)

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
    val valid = from.isNotBlank() && to.isNotBlank() && DigitFormatter.parseInt(rent) != null && days.isNotEmpty()
    Scaffold(topBar = { DTopBar(stringResource(if (initial == null) R.string.create_trip else R.string.edit_trip), onBack) }, bottomBar = {
        StickyCta {
            PrimaryButton(stringResource(R.string.save_trip), { onSave(PredefinedTrip(initial?.id ?: "p${System.nanoTime()}", type, from, to, DigitFormatter.toAsciiDigits(time), days, DigitFormatter.parseInt(seats) ?: 1, DigitFormatter.parseInt(rent) ?: 0, active)) }, enabled = valid)
        }
    }) { pad ->
        ScreenColumn(pad) {
            Text(stringResource(R.string.vehicle), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach { FilterChip(it == type, { type = it }, { Text("${it.emoji} ${it.title()}") }, colors = brandChipColors()) }
            }
            OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.from_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.to_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(time.toLocalDigits(), { time = DigitFormatter.toAsciiDigits(it) }, label = { Text(stringResource(R.string.departure_time)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.runs_on), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekDays.forEach { (d, res) -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(stringResource(res)) }, colors = brandChipColors()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(seats.toLocalDigits(), { seats = DigitFormatter.digitsOnly(it, 3) }, label = { Text(stringResource(R.string.seats)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rent.toLocalDigits(), { rent = DigitFormatter.digitsOnly(it, 6) }, label = { Text(stringResource(R.string.fixed_rent)) }, prefix = { Text(stringResource(R.string.taka_prefix)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            DigitFormatter.parseInt(rent)?.let { Text(str(R.string.earn_after_commission, taka(Commission.netOf(it)), Commission.RATE_PERCENT), color = DP.Success, fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.visible_customers), Modifier.weight(1f)); Switch(active, { active = it }, colors = brandSwitchColors()) }
        }
    }
}

@Composable
fun AvailabilityScreen(trips: List<Trip>, blocked: Set<LocalDate>, onToggle: (LocalDate) -> Unit, onBack: () -> Unit) {
    val month = YearMonth.now()
    val booked = trips.filter { it.status == TripStatus.UPCOMING }.map { it.slot.start.toLocalDate() }.toSet()
    Scaffold(topBar = { DTopBar(stringResource(R.string.availability), onBack) }) { pad ->
        ScreenColumn(pad) {
            Text(monthText(month.monthValue, month.year), style = MaterialTheme.typography.titleLarge)
            DCard {
                Row { listOf(R.string.cal_m, R.string.cal_t, R.string.cal_w, R.string.cal_th, R.string.cal_f, R.string.cal_s, R.string.cal_su).forEach { Text(stringResource(it), Modifier.weight(1f), textAlign = TextAlign.Center, color = DP.Muted, fontSize = 12.sp) } }
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
                                        Text(d.dayOfMonth.l(), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChip(stringResource(R.string.available), Tone.SUCCESS); StatusChip(stringResource(R.string.booked_locked), Tone.PRIMARY); StatusChip(stringResource(R.string.off), Tone.DANGER) }
            Text(stringResource(R.string.availability_sub), color = DP.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TripDetailScreen(t: Trip, onBack: () -> Unit, onStart: () -> Unit, onCall: () -> Unit = {}) {
    Scaffold(topBar = { DTopBar(str(R.string.trip_code, t.code), onBack) }, bottomBar = {
        if (t.status == TripStatus.UPCOMING) StickyCta { PrimaryButton(stringResource(R.string.start_trip), onStart, accent = true) }
    }) { pad ->
        ScreenColumn(pad) {
            MapPlaceholder(t.from, t.to, height = 160.dp)
            DCard {
                Row { RouteText(t.from, t.to, MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f)); StatusChip(t.status.label(), t.status.tone()) }
                KeyValue(stringResource(R.string.slot), t.slot.label()); KeyValue(stringResource(R.string.vehicle), t.vehicleType.title()); KeyValue(stringResource(R.string.passengers), t.passengers.l())
                if (t.fromPredefined) KeyValue(stringResource(R.string.booking), stringResource(R.string.predefined_trip))
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(t.customer.name)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t.customer.name, style = MaterialTheme.typography.titleSmall); RatingText(t.customer.rating) }
                    DurpallaIconButton(Icons.Filled.Call, stringResource(R.string.call), onCall)
                }
            }
            DCard {
                KeyValue(stringResource(R.string.agreed_rent), taka(t.fare), bold = true); KeyValue(stringResource(R.string.advance_online), taka(t.advancePaid), DP.Success)
                KeyValue(stringResource(R.string.to_collect), taka(t.fare - t.advancePaid), DP.Accent, true); KeyValue(str(R.string.commission_pct, Commission.RATE_PERCENT), taka(Commission.of(t.fare)))
                t.paymentMethod?.let { KeyValue(stringResource(R.string.paid_via), it.title()) }
            }
            CallLogCard(t.callLog)
        }
    }
}

@Composable
fun LiveTripScreen(t: Trip, onAdvance: (TripStatus) -> Unit, onCollect: () -> Unit, onSos: () -> Unit = {}, onCall: () -> Unit = {}) {
    val (label, next) = when (t.status) {
        TripStatus.UPCOMING, TripStatus.ON_THE_WAY -> stringResource(R.string.arrived_pickup) to TripStatus.ARRIVED
        TripStatus.ARRIVED -> stringResource(R.string.start_ride) to TripStatus.IN_PROGRESS
        else -> stringResource(R.string.end_collect) to null
    }
    Scaffold(bottomBar = { StickyCta { PrimaryButton(label, { if (next != null) onAdvance(next) else onCollect() }, accent = next == null) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f)) {
                MapPlaceholder(t.from, t.to, Modifier.fillMaxSize(), height = 400.dp)
                DurpallaButton(stringResource(R.string.sos), onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), ButtonVariant.Danger, ButtonSize.Medium, fillWidth = false)
            }
            Column(Modifier.background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(if (t.status == TripStatus.UPCOMING) TripStatus.ON_THE_WAY.label() else t.status.label(), Tone.ACCENT)
                RouteText(t.from, t.to, MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str(R.string.dot_join3, t.customer.name, pax(t.passengers), t.code), color = DP.Muted, modifier = Modifier.weight(1f))
                    DurpallaIconButton(Icons.Filled.Call, stringResource(R.string.call), onCall)
                }
                LinearProgressIndicator(progress = { when (t.status) { TripStatus.ARRIVED -> 0.35f; TripStatus.IN_PROGRESS -> 0.7f; else -> 0.1f } }, modifier = Modifier.fillMaxWidth(), color = DP.Primary500, trackColor = DP.Primary100)
            }
        }
    }
}

@Composable
fun CollectPaymentScreen(t: Trip, onBack: () -> Unit, onConfirm: (PaymentMethod) -> Unit) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    val due = t.fare - t.advancePaid
    Scaffold(topBar = { DTopBar(stringResource(R.string.collect_payment), onBack) }, bottomBar = { StickyCta { PrimaryButton(str(R.string.confirm_received, taka(due)), { onConfirm(method) }, accent = true) } }) { pad ->
        ScreenColumn(pad) {
            DCard(color = DP.Primary500, border = null) {
                Text(stringResource(R.string.amount_to_collect), color = DP.Primary200); Text(taka(due), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(str(R.string.rent_minus_advance, taka(t.fare), taka(t.advancePaid)), color = DP.Primary200, fontSize = 12.sp)
            }
            Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleSmall)
            listOf(PaymentMethod.CASH, PaymentMethod.BKASH, PaymentMethod.NAGAD).forEach { m ->
                DCard(onClick = { method = m }, border = if (m == method) DP.Primary500 else DP.Line) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(m.title(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); RadioButton(m == method, { method = m }) }
                }
            }
            if (method == PaymentMethod.CASH) Text(str(R.string.cash_commission_note, Commission.RATE_PERCENT, taka(Commission.of(t.fare))), color = DP.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TripCompletedScreen(t: Trip, onDone: () -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    Scaffold(bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.done), onDone) } }) { pad ->
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.CheckCircle, null, tint = DP.Success, modifier = Modifier.size(80.dp))
            Text(stringResource(R.string.trip_completed), style = MaterialTheme.typography.headlineSmall)
            DCard {
                KeyValue(stringResource(R.string.trip), t.code); KeyValue(stringResource(R.string.rent), taka(t.fare)); KeyValue(stringResource(R.string.commission), "-" + taka(Commission.of(t.fare)), DP.Danger)
                HorizontalDivider(color = DP.Line); KeyValue(stringResource(R.string.your_earning), taka(Commission.netOf(t.fare)), DP.Success, true)
            }
            Text(str(R.string.rate_customer, t.customer.name), style = MaterialTheme.typography.titleSmall)
            Row { (1..5).forEach { i -> Icon(Icons.Filled.Star, null, tint = if (i <= stars) DP.Warning else DP.Line, modifier = Modifier.size(40.dp).clickable { stars = i }) } }
        }
    }
}

@Composable
fun TripHistoryScreen(trips: List<Trip>, onBack: () -> Unit, onTrip: (Trip) -> Unit) {
    val done = trips.filter { it.status == TripStatus.COMPLETED || it.status == TripStatus.CANCELLED }
    Scaffold(topBar = { DTopBar(stringResource(R.string.trip_history), onBack) }) { pad ->
        ScreenColumn(pad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.completed), done.count { it.status == TripStatus.COMPLETED }.l(), Modifier.weight(1f))
                StatTile(stringResource(R.string.cancelled), done.count { it.status == TripStatus.CANCELLED }.l(), Modifier.weight(1f), DP.Danger)
                StatTile(stringResource(R.string.earned), taka(done.filter { it.status == TripStatus.COMPLETED }.sumOf { Commission.netOf(it.fare) }), Modifier.weight(1.3f), DP.Success)
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
