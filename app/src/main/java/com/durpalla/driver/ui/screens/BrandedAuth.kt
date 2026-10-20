package com.durpalla.driver.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.ui.AuthState
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.delay

/** Full-screen brand background: teal gradient, river waves and a dashed route. */
@Composable
fun BrandBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DP.Primary500, DP.Primary600, DP.Primary700)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Waves near the bottom (river).
            for (i in 0 until 4) {
                val y = h * (0.78f + i * 0.06f); val amp = 14f + i * 4
                val p = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x < w) { relativeQuadraticTo(w / 8, -amp, w / 4, 0f); relativeQuadraticTo(w / 8, amp, w / 4, 0f); x += w / 2 }
                }
                drawPath(p, Color.White.copy(alpha = 0.05f + i * 0.015f), style = Stroke(width = 3f))
            }
            // Dashed route with stops (top-right to left).
            val route = Path().apply { moveTo(w * 1.05f, h * 0.10f); cubicTo(w * 0.7f, h * 0.18f, w * 0.85f, h * 0.38f, w * 0.45f, h * 0.42f); cubicTo(w * 0.1f, h * 0.46f, w * 0.2f, h * 0.62f, -w * 0.05f, h * 0.68f) }
            drawPath(route, Color.White.copy(alpha = 0.10f), style = Stroke(width = 4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f))))
            listOf(Offset(w * 0.45f, h * 0.42f), Offset(w * 0.86f, h * 0.17f)).forEach {
                drawCircle(DP.Accent.copy(alpha = 0.35f), 10f, it); drawCircle(Color.White.copy(alpha = 0.25f), 4f, it)
            }
            // Soft glow.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset(w * 0.15f, h * 0.05f), w * 0.6f), w * 0.6f, Offset(w * 0.15f, h * 0.05f))
        }
        content()
    }
}

/**
 * Header + centered card + footer layout. Scrolls and pads for system bars and the IME so the card
 * stays reachable on small screens with the keyboard open.
 */
@Composable
private fun BrandedAuthLayout(onBack: (() -> Unit)? = null, card: @Composable ColumnScope.() -> Unit) {
    BrandBackground {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            val minH = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = minH).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(40.dp)) {
                    if (onBack != null) IconButton(onBack, Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                }
                // Header
                Box(Modifier.size(84.dp).shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.durpalla_icon), "Durpalla", Modifier.size(64.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Durpalla Driver", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Get trips. Bid smart. Earn more.", color = DP.Primary200, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Motif(Icons.Filled.DirectionsBoat, "Boat"); Motif(Icons.Filled.LocalTaxi, "Taxi"); Motif(Icons.Filled.DirectionsBus, "Micro")
                }
                Spacer(Modifier.height(24.dp))
              }
                // Centered card
                Column(
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = DP.Primary700).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = card,
                )
                Text("Powered by Durpalla Limited", modifier = Modifier.padding(top = 24.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Motif(icon: ImageVector, label: String) = Row(
    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" $label", color = Color.White, fontSize = 12.sp) }

@Composable
private fun AccentCta(text: String, enabled: Boolean, onClick: () -> Unit) = Button(
    onClick, Modifier.fillMaxWidth().height(54.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(containerColor = DP.Accent, contentColor = Color.White, disabledContainerColor = DP.Accent.copy(alpha = 0.45f), disabledContentColor = Color.White),
) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

/** Phone login. The VM keeps the 11-digit local number (01XXXXXXXXX); the field shows +880 and the 10 digits after 0. */
@Composable
fun PhoneLoginScreen(state: AuthState, onPhone: (String) -> Unit, onSend: () -> Unit) {
    val local = state.phone.removePrefix("0")
    BrandedAuthLayout {
        Text("Log in to start driving", style = MaterialTheme.typography.titleLarge, color = DP.Ink)
        Text("Enter your mobile number. We'll send a 6-digit code.", color = DP.Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = local, onValueChange = { v -> val d = v.filter(Char::isDigit).removePrefix("0").take(10); onPhone(if (d.isEmpty()) "" else "0$d") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🇧🇩", fontSize = 18.sp); Text(" +880", fontWeight = FontWeight.SemiBold, color = DP.Ink)
                    Box(Modifier.padding(start = 10.dp).width(1.dp).height(24.dp).background(DP.Line))
                }
            },
            placeholder = { Text("1XXX-XXXXXX") }, label = { Text("Mobile number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = state.error != null, supportingText = state.error?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DP.Primary500, focusedLabelColor = DP.Primary500),
        )
        AccentCta(if (state.loading) "Sending code…" else "Send OTP", !state.loading && local.length == 10, onSend)
        Text(buildAnnotatedString {
            append("By continuing you agree to Durpalla's ")
            withStyle(SpanStyle(color = DP.Primary500, fontWeight = FontWeight.SemiBold)) { append("Driver Terms") }
            append(" and ")
            withStyle(SpanStyle(color = DP.Primary500, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
            append(".")
        }, fontSize = 12.sp, color = DP.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Six separate digit boxes backed by one BasicTextField (paste & IME friendly). */
@Composable
fun OtpBoxes(code: String, onChange: (String) -> Unit, isError: Boolean) {
    BasicTextField(
        value = code, onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true,
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = code.getOrNull(i)?.toString() ?: ""
                    val active = i == code.length.coerceAtMost(5) && code.length < 6
                    val border = when { isError -> DP.Danger; active -> DP.Primary500; ch.isNotEmpty() -> DP.Primary200; else -> DP.Line }
                    Box(
                        Modifier.weight(1f).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp))
                            .background(if (ch.isNotEmpty()) DP.Primary100 else DP.Bg).border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DP.Ink) }
                }
            }
        },
    )
}

@Composable
fun OtpScreen(phone: String, error: String?, onBack: () -> Unit, onVerify: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(30) }
    LaunchedEffect(seconds) { if (seconds > 0) { delay(1000); seconds-- } }
    val shown = phone.removePrefix("0").let { if (it.length == 10) "+880 ${it.take(4)}-${it.drop(4)}" else "+880 $it" }
    BrandedAuthLayout(onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(DP.Primary100), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = DP.Primary500, modifier = Modifier.size(18.dp)) }
            Text("  Verify your number", style = MaterialTheme.typography.titleLarge, color = DP.Ink)
        }
        Text(buildAnnotatedString { append("Enter the 6-digit code sent to "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = DP.Ink)) { append(shown) } }, color = DP.Muted, fontSize = 13.sp)
        OtpBoxes(code, { code = it }, error != null)
        if (error != null) Text(error, color = DP.Danger, fontSize = 12.sp)
        AccentCta("Verify & continue", code.length == 6) { onVerify(code) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Didn't get it? ", color = DP.Muted, fontSize = 13.sp)
            Text(if (seconds > 0) "Resend in 0:${seconds.toString().padStart(2, '0')}" else "Resend code", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (seconds > 0) DP.Muted else DP.Primary500, modifier = Modifier.clickable(enabled = seconds == 0) { seconds = 30 })
        }
        Text("Wrong number? Change", color = DP.Primary500, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack))
    }
}
