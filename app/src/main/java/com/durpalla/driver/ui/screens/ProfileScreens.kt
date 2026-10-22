package com.durpalla.driver.ui.screens

import com.durpalla.driver.i18n.asString
import androidx.compose.ui.res.stringResource
import com.durpalla.driver.R
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.plural
import com.durpalla.driver.ui.title
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.i18n.DigitFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP

@Composable
fun ProfileScreen(d: Driver, onVehicles: () -> Unit, onRatings: () -> Unit, onHistory: () -> Unit, onPredefined: () -> Unit, onCalendar: () -> Unit, onWallet: () -> Unit, onSupport: () -> Unit, onNotifications: () -> Unit, onLogout: () -> Unit, onLanguage: () -> Unit = {}) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.profile)) }) { pad ->
        ScreenColumn(pad) {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.name, 64.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(d.name, style = MaterialTheme.typography.titleLarge); Text(d.phone.toLocalDigits(), color = DP.Muted, fontSize = 13.sp)
                        RatingText(d.rating, str(R.string.trips_verified, plural(R.plurals.trips_count, d.totalTrips)))
                    }
                }
            }
            DCard {
                MenuRow(Icons.Filled.DirectionsBoat, stringResource(R.string.vehicles_docs), plural(R.plurals.vehicles_count, d.vehicles.size), onClick = onVehicles)
                MenuRow(Icons.Filled.Route, stringResource(R.string.predef_title), onClick = onPredefined)
                MenuRow(Icons.Filled.CalendarMonth, stringResource(R.string.availability_calendar), onClick = onCalendar)
                MenuRow(Icons.Filled.History, stringResource(R.string.trip_history), onClick = onHistory)
                MenuRow(Icons.Filled.Star, stringResource(R.string.ratings), str(R.string.rating_star, num(d.rating)), onClick = onRatings)
            }
            DCard {
                MenuRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.wallet_payout), onClick = onWallet)
                MenuRow(Icons.Filled.Notifications, stringResource(R.string.notifications), onClick = onNotifications)
                MenuRow(Icons.Filled.Translate, stringResource(R.string.language), stringResource(R.string.language_sub), trailing = currentLanguageName(), onClick = onLanguage)
                MenuRow(Icons.Filled.SupportAgent, stringResource(R.string.help_support), onClick = onSupport)
                MenuRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.log_out), onClick = onLogout)
            }
            Text(stringResource(R.string.version_footer), color = DP.Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun VehiclesScreen(d: Driver, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.vehicles_docs), onBack) }) { pad ->
        ScreenColumn(pad) {
            SectionHeader(stringResource(R.string.vehicles), stringResource(R.string.add))
            d.vehicles.forEach { v ->
                DCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleBadge(v.type)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(v.name, style = MaterialTheme.typography.titleSmall); Text(str(R.string.dot_join3, v.type.title(), v.reg, plural(R.plurals.seats_count, v.seats)), color = DP.Muted, fontSize = 12.sp) }
                        StatusChip(stringResource(if (v.verified) R.string.verified else R.string.pending), if (v.verified) Tone.SUCCESS else Tone.WARNING)
                    }
                }
            }
            SectionHeader(stringResource(R.string.documents))
            DCard {
                d.documents.forEach { doc ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, tint = DP.Primary500)
                        Text(doc.name.toLocalDigits(), Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        StatusChip(doc.status.title(), when (doc.status) { DocStatus.VERIFIED -> Tone.SUCCESS; DocStatus.UNDER_REVIEW -> Tone.WARNING; DocStatus.EXPIRING -> Tone.ACCENT; DocStatus.MISSING -> Tone.DANGER })
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(list: List<AppNotification>, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.notifications), onBack) }) { pad ->
        ScreenColumn(pad) {
            list.forEach { n ->
                DCard(color = if (n.unread) DP.Primary100 else Color.White) {
                    Row { Text(n.title.asString().toLocalDigits(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(n.time.toLocalDigits(), color = DP.Muted, fontSize = 11.sp) }
                    Text(n.body.asString().toLocalDigits(), color = DP.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val faqs = listOf(
        stringResource(R.string.faq_q1) to stringResource(R.string.faq_a1),
        stringResource(R.string.faq_q2) to stringResource(R.string.faq_a2),
        stringResource(R.string.faq_q3) to stringResource(R.string.faq_a3),
        stringResource(R.string.faq_q4) to str(R.string.faq_a4, Commission.RATE_PERCENT),
    )
    Scaffold(topBar = { DTopBar(stringResource(R.string.help_support), onBack) }) { pad ->
        ScreenColumn(pad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.call_support), {}, Modifier.weight(1f)); SecondaryButton(stringResource(R.string.live_chat), {}, Modifier.weight(1f))
            }
            SectionHeader(stringResource(R.string.faq))
            faqs.forEach { (q, a) ->
                var open by remember { mutableStateOf(false) }
                DCard(onClick = { open = !open }) {
                    Text(q, fontWeight = FontWeight.SemiBold)
                    if (open) { Spacer(Modifier.height(6.dp)); Text(a, color = DP.Muted) }
                }
            }
            SecondaryButton(stringResource(R.string.report_problem), {})
        }
    }
}

@PhonePreview @Composable private fun SupportPreview() = P { SupportScreen {} }

@Composable
private fun currentLanguageName(): String = stringResource(if (com.durpalla.driver.i18n.AppLocale.isBangla) R.string.lang_bangla else R.string.lang_english)
