package com.durpalla.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.durpalla.driver.data.SampleData
import com.durpalla.driver.domain.BidStatus
import com.durpalla.driver.realtime.ConnectionState
import com.durpalla.driver.realtime.LiveBid
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.screens.*
import com.durpalla.driver.ui.theme.DP
import com.durpalla.driver.ui.theme.DurpallaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RealtimeScreenshotTest {
    private val r = SampleData.requests[0]
    private fun shot(n: String, c: @androidx.compose.runtime.Composable () -> Unit) = captureRoboImage("build/shots/driver_rt_$n.png") { androidx.compose.runtime.CompositionLocalProvider(LocalAnimateLive provides false) { DurpallaTheme { c() } } }

    @Test fun placeBidLive() = shot("bid_live") {
        PlaceBidScreen(r, BidFormState(amount = "3100"), {}, {}, {}, {}, {}, LiveBid(r.id, 5, 2950, "b1", 3100, BidStatus.PENDING), ConnectionState.LIVE)
    }
    @Test fun incomingReconnecting() = shot("request_reconnecting") {
        IncomingRequestScreen(r, null, {}, {}, {}, {}, LiveBid(r.id, 4, 2900), ConnectionState.RECONNECTING)
    }
    @Test fun acceptedBanner() = shot("accepted_banner") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(SampleData.driver, true, {}, SampleData.summary, 2, SampleData.trips[0], SampleData.requests, { it.id == "r3" }, {}, {}, {}, {}, {}, ConnectionState.LIVE)
            Box(Modifier.fillMaxSize().background(DP.Ink.copy(alpha = 0.35f)))
            LiveBannerCard(LiveBanner(LiveBanner.Kind.ACCEPTED, "Customer accepted your bid!", "Sadarghat → Narayanganj · ৳3,100 · slot locked", "won/t1"), {}, {},
                Modifier.align(Alignment.TopCenter).padding(12.dp).padding(top = 24.dp))
        }
    }
    @Test fun requestsLive() = shot("requests_live") { RequestsScreen(SampleData.requests, emptyList(), { it.id == "r3" }, 0, {}, {}, {}, ConnectionState.LIVE) }
    @Test fun buttons() = shot("buttons") {
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Durpalla buttons")
            DurpallaButton("Place bid ৳3,100", {}, variant = ButtonVariant.Accent, icon = Icons.Filled.Gavel)
            DurpallaButton("View trip", {}, variant = ButtonVariant.Primary)
            DurpallaButton("Skip", {}, variant = ButtonVariant.Secondary)
            DurpallaButton("Cancel", {}, variant = ButtonVariant.Text)
            DurpallaButton("Cancel trip", {}, variant = ButtonVariant.Danger)
            DurpallaButton("Submitting…", {}, variant = ButtonVariant.Accent, loading = true)
            DurpallaButton("Disabled", {}, variant = ButtonVariant.Primary, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurpallaButton("৳2,800", {}, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Small)
                DurpallaButton("৳3,150", {}, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Small)
                DurpallaButton("Medium", {}, Modifier.weight(1f), ButtonVariant.Primary, ButtonSize.Small)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LiveIndicator(ConnectionState.LIVE); LiveIndicator(ConnectionState.RECONNECTING); LiveIndicator(ConnectionState.OFFLINE) }
        }
    }
}
