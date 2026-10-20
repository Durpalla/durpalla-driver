package com.durpalla.driver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.domain.BidStatus
import com.durpalla.driver.domain.TripRequest
import com.durpalla.driver.domain.taka
import com.durpalla.driver.realtime.ConnectionState
import com.durpalla.driver.realtime.LiveBid
import com.durpalla.driver.ui.LiveBanner
import com.durpalla.driver.ui.theme.DP

/** Set false (e.g. screenshot tests) to disable the infinite pulse animation. */
val LocalAnimateLive = androidx.compose.runtime.staticCompositionLocalOf { true }

/** "● Live" / "Reconnecting…" pill. Pulses when live. */
@Composable
fun LiveIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val (bg, fg) = when (state) {
        ConnectionState.LIVE -> DP.SuccessSoft to DP.Success
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> DP.WarningSoft to Color(0xFF92400E)
        ConnectionState.OFFLINE -> DP.DangerSoft to DP.Danger
    }
    val pulse = if (LocalAnimateLive.current) {
        val p by rememberInfiniteTransition(label = "live").animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"); p
    } else 1f
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).alpha(if (state == ConnectionState.LIVE) pulse else 1f).clip(CircleShape).background(fg))
        Text(" " + if (state == ConnectionState.RECONNECTING) "Reconnecting…" else state.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Live competition on a request: bids, lowest bid, and where this driver stands. */
@Composable
fun LiveBidPanel(request: TripRequest, live: LiveBid?, connection: ConnectionState) {
    val count = maxOf(live?.bidCount ?: 0, request.bidsCount)
    DCard(color = DP.Primary100, border = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Live bidding", style = MaterialTheme.typography.titleSmall, color = DP.Primary700, modifier = Modifier.weight(1f))
            LiveIndicator(connection)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Bids", "$count", Modifier.weight(1f))
            StatTile("Lowest bid", live?.lowestBid?.let(::taka) ?: "—", Modifier.weight(1f), DP.Accent)
            StatTile("Your bid", live?.myAmount?.let(::taka) ?: "—", Modifier.weight(1f), DP.Primary500)
        }
        val status = when {
            live?.status == BidStatus.WON -> "Customer accepted your bid" to DP.Success
            live?.status == BidStatus.LOST -> "Customer chose another driver" to DP.Danger
            live?.myAmount != null && live.iAmLowest -> "You're the lowest bid right now" to DP.Success
            live?.myAmount != null && live.lowestBid != null -> "Outbid by ${taka(live.myAmount - live.lowestBid)}. Customers also weigh rating." to Color(0xFFB45309)
            else -> null
        }
        status?.let { (t, c) -> Text(t, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** High-priority in-app banner (bid accepted, new request, cancellations). */
@Composable
fun LiveBannerCard(b: LiveBanner, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, icon) = when (b.kind) {
        LiveBanner.Kind.ACCEPTED -> DP.Success to Icons.Filled.CheckCircle
        LiveBanner.Kind.REQUEST -> DP.Primary500 to Icons.Filled.NotificationsActive
        LiveBanner.Kind.WARNING -> DP.Danger to Icons.Filled.Warning
        LiveBanner.Kind.INFO -> DP.Primary600 to Icons.Filled.Info
    }
    Column(modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(b.body, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
            IconButton(onDismiss, Modifier.size(28.dp), colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) { Icon(Icons.Filled.Close, "Dismiss") }
        }
        Spacer(Modifier.height(12.dp))
        DurpallaButton(if (b.kind == LiveBanner.Kind.ACCEPTED) "View trip" else if (b.kind == LiveBanner.Kind.REQUEST) "View & bid" else "Open", onOpen,
            variant = if (b.kind == LiveBanner.Kind.ACCEPTED || b.kind == LiveBanner.Kind.REQUEST) ButtonVariant.Accent else ButtonVariant.OnDark, size = ButtonSize.Medium)
    }
}
