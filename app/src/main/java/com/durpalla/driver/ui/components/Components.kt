package com.durpalla.driver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.theme.DP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DP.Bg),
    )
}

@Composable
fun DCard(modifier: Modifier = Modifier, color: Color = Color.White, border: Color? = DP.Line, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    val shape = RoundedCornerShape(16.dp)
    val b = border?.let { BorderStroke(1.dp, it) }
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, border = b, colors = CardDefaults.cardColors(containerColor = color)) { Column(Modifier.padding(16.dp), content = content) }
    else Card(modifier = m, shape = shape, border = b, colors = CardDefaults.cardColors(containerColor = color)) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Boolean = false, loading: Boolean = false) =
    DurpallaButton(text, onClick, modifier, if (accent) ButtonVariant.Accent else ButtonVariant.Primary, enabled = enabled, loading = loading)

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) =
    DurpallaButton(text, onClick, modifier, if (danger) ButtonVariant.Danger else ButtonVariant.Secondary, enabled = enabled)

/** CTA pinned to the bottom of a screen (use in Scaffold bottomBar). */
@Composable
fun StickyCta(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

enum class Tone { PRIMARY, ACCENT, SUCCESS, WARNING, DANGER, NEUTRAL }

@Composable
fun StatusChip(text: String, tone: Tone) {
    val (bg, fg) = when (tone) {
        Tone.PRIMARY -> DP.Primary100 to DP.Primary600
        Tone.ACCENT -> DP.AccentSoft to Color(0xFFB45309)
        Tone.SUCCESS -> DP.SuccessSoft to DP.Success
        Tone.WARNING -> DP.WarningSoft to Color(0xFF92400E)
        Tone.DANGER -> DP.DangerSoft to DP.Danger
        Tone.NEUTRAL -> DP.Bg to DP.Muted
    }
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

fun BidStatus.tone() = when (this) { BidStatus.PENDING -> Tone.WARNING; BidStatus.WON -> Tone.SUCCESS; BidStatus.LOST -> Tone.DANGER; BidStatus.EXPIRED -> Tone.NEUTRAL }

@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, DP.Line, RoundedCornerShape(12.dp)).padding(4.dp)) {
        options.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) DP.Primary500 else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s, color = if (sel) Color.White else DP.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun FilterChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, s ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(s) }, shape = RoundedCornerShape(50),
                colors = brandChipColors(), border = FilterChipDefaults.filterChipBorder(true, i == selected, borderColor = DP.Line, selectedBorderColor = DP.Primary500))
        }
    }
}

@Composable
fun VehicleBadge(type: VehicleType, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(DP.Primary100), contentAlignment = Alignment.Center) {
        Text(type.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(DP.Primary200), contentAlignment = Alignment.Center) {
        Text(name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""), color = DP.Primary700, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RatingText(rating: Double, extra: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = DP.Warning, modifier = Modifier.size(14.dp))
        Text(" $rating$extra", style = MaterialTheme.typography.bodySmall, color = DP.Muted)
    }
}

@Composable
fun Stars(count: Int, size: Dp = 16.dp) = Row { repeat(5) { Icon(Icons.Filled.Star, null, tint = if (it < count) DP.Warning else DP.Line, modifier = Modifier.size(size)) } }

@Composable
fun RouteText(from: String, to: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) =
    Text("$from → $to", style = style, color = DP.Ink)

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = DP.Ink) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, DP.Line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DP.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = DP.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = DP.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null) Text(action, color = DP.Primary500, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(DP.Primary100), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DP.Primary500) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DP.Muted)
        }
        if (trailing != null) Text(trailing, color = DP.Muted, fontSize = 12.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DP.Muted)
    }
}

/** Grey placeholder where a real map (Google Maps / Mapbox / OSM) will go. */
@Composable
fun MapPlaceholder(from: String, to: String, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(DP.Primary100), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📍 $from", color = DP.Primary700, fontWeight = FontWeight.SemiBold)
            Text("┆\n┆", color = DP.Primary500, textAlign = TextAlign.Center)
            Text("🏁 $to", color = DP.Primary700, fontWeight = FontWeight.SemiBold)
            Text("Map preview", style = MaterialTheme.typography.labelSmall, color = DP.Muted)
        }
    }
}

@Composable
fun DurpallaLogo(modifier: Modifier = Modifier, stacked: Boolean = false) =
    Image(painterResource(if (stacked) R.drawable.durpalla_logo_stacked else R.drawable.durpalla_logo_horizontal), "Durpalla", modifier)

@Composable
fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text(text, color = DP.Muted) }

@Composable
fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxSize().background(DP.Bg).padding(padding).verticalScrollable().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )

@Composable
private fun Modifier.verticalScrollable(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
fun brandChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White, labelColor = DP.Ink, selectedContainerColor = DP.Primary500, selectedLabelColor = Color.White,
    selectedLeadingIconColor = Color.White, iconColor = DP.Primary500,
)

@Composable
fun brandSwitchColors() = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DP.Primary500, uncheckedThumbColor = DP.Muted, uncheckedTrackColor = DP.Line, uncheckedBorderColor = DP.Line)
