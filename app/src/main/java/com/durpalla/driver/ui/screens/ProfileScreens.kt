package com.durpalla.driver.ui.screens

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
import com.durpalla.driver.data.SampleData
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP

@Composable
fun ProfileScreen(d: Driver, onVehicles: () -> Unit, onRatings: () -> Unit, onHistory: () -> Unit, onPredefined: () -> Unit, onCalendar: () -> Unit, onWallet: () -> Unit, onSupport: () -> Unit, onNotifications: () -> Unit, onLogout: () -> Unit) {
    Scaffold(topBar = { DTopBar("Profile") }) { pad ->
        ScreenColumn(pad) {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.name, 64.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(d.name, style = MaterialTheme.typography.titleLarge); Text(d.phone, color = DP.Muted, fontSize = 13.sp)
                        RatingText(d.rating, " · ${d.totalTrips} trips · Verified")
                    }
                }
            }
            DCard {
                MenuRow(Icons.Filled.DirectionsBoat, "Vehicles & documents", "${d.vehicles.size} vehicles", onClick = onVehicles)
                MenuRow(Icons.Filled.Route, "Pre-defined trips", onClick = onPredefined)
                MenuRow(Icons.Filled.CalendarMonth, "Availability calendar", onClick = onCalendar)
                MenuRow(Icons.Filled.History, "Trip history", onClick = onHistory)
                MenuRow(Icons.Filled.Star, "Ratings", "${d.rating} ★", onClick = onRatings)
            }
            DCard {
                MenuRow(Icons.Filled.AccountBalanceWallet, "Wallet & payout methods", onClick = onWallet)
                MenuRow(Icons.Filled.Notifications, "Notifications", onClick = onNotifications)
                MenuRow(Icons.Filled.SupportAgent, "Help & support", onClick = onSupport)
                MenuRow(Icons.AutoMirrored.Filled.Logout, "Log out", onClick = onLogout)
            }
            Text("Durpalla Driver v0.1 · A Durpalla Limited service", color = DP.Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun VehiclesScreen(d: Driver, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar("Vehicles & documents", onBack) }) { pad ->
        ScreenColumn(pad) {
            SectionHeader("Vehicles", "+ Add")
            d.vehicles.forEach { v ->
                DCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleBadge(v.type)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(v.name, style = MaterialTheme.typography.titleSmall); Text("${v.type.label} · ${v.reg} · ${v.seats} seats", color = DP.Muted, fontSize = 12.sp) }
                        StatusChip(if (v.verified) "Verified" else "Pending", if (v.verified) Tone.SUCCESS else Tone.WARNING)
                    }
                }
            }
            SectionHeader("Documents")
            DCard {
                d.documents.forEach { doc ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, tint = DP.Primary500)
                        Text(doc.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        StatusChip(doc.status.label, when (doc.status) { DocStatus.VERIFIED -> Tone.SUCCESS; DocStatus.UNDER_REVIEW -> Tone.WARNING; DocStatus.EXPIRING -> Tone.ACCENT; DocStatus.MISSING -> Tone.DANGER })
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(list: List<AppNotification>, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar("Notifications", onBack) }) { pad ->
        ScreenColumn(pad) {
            list.forEach { n ->
                DCard(color = if (n.unread) DP.Primary100 else Color.White) {
                    Row { Text(n.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(n.time, color = DP.Muted, fontSize = 11.sp) }
                    Text(n.body, color = DP.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val faqs = listOf(
        "How does bidding work?" to "Customers open a request; you bid a rent. The customer picks one bid. If you win, the slot is locked for you.",
        "Why can't I bid on a request?" to "You already have a confirmed trip overlapping that slot (Slot booked).",
        "When do I get paid?" to "Online payments settle to your wallet after the trip. Withdraw any time to bKash, Nagad or bank.",
        "What is the commission?" to "Durpalla keeps ${Commission.RATE_PERCENT}% of each trip's rent.",
    )
    Scaffold(topBar = { DTopBar("Help & support", onBack) }) { pad ->
        ScreenColumn(pad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Call 16XXX", {}, Modifier.weight(1f)); SecondaryButton("Live chat", {}, Modifier.weight(1f))
            }
            SectionHeader("FAQ")
            faqs.forEach { (q, a) ->
                var open by remember { mutableStateOf(false) }
                DCard(onClick = { open = !open }) {
                    Text(q, fontWeight = FontWeight.SemiBold)
                    if (open) { Spacer(Modifier.height(6.dp)); Text(a, color = DP.Muted) }
                }
            }
            SecondaryButton("Report a problem with a trip", {})
        }
    }
}

@PhonePreview @Composable private fun ProfilePreview() = P { ProfileScreen(SampleData.driver, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
@PhonePreview @Composable private fun VehiclesPreview() = P { VehiclesScreen(SampleData.driver) {} }
@PhonePreview @Composable private fun NotificationsPreview() = P { NotificationsScreen(SampleData.notifications) {} }
@PhonePreview @Composable private fun SupportPreview() = P { SupportScreen {} }
