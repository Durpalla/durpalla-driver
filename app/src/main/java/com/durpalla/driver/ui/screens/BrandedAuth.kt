package com.durpalla.driver.ui.screens

import androidx.compose.ui.res.stringResource
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.title
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.i18n.DigitFormatter
import com.durpalla.driver.i18n.asString
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
import com.durpalla.driver.ui.components.ButtonVariant
import com.durpalla.driver.ui.components.DurpallaButton
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
internal fun BrandedAuthLayout(onBack: (() -> Unit)? = null, card: @Composable ColumnScope.() -> Unit) {
    BrandBackground {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            val minH = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = minH).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(40.dp)) {
                    if (onBack != null) IconButton(onBack, Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
                // Header
                Box(Modifier.size(84.dp).shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.durpalla_icon), stringResource(R.string.brand_durpalla), Modifier.size(64.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.auth_tagline), color = DP.Primary200, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Motif(Icons.Filled.DirectionsBoat, stringResource(R.string.motif_boat)); Motif(Icons.Filled.LocalTaxi, stringResource(R.string.motif_taxi)); Motif(Icons.Filled.DirectionsBus, stringResource(R.string.motif_micro))
                }
                Spacer(Modifier.height(24.dp))
              }
                // Centered card
                Column(
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = DP.Primary700).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = card,
                )
                Text(stringResource(R.string.powered_by), modifier = Modifier.padding(top = 24.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
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
private fun AccentCta(text: String, enabled: Boolean, loading: Boolean = false, onClick: () -> Unit) =
    DurpallaButton(text, onClick, variant = ButtonVariant.Accent, enabled = enabled, loading = loading)

/** Phone login. The VM keeps the 11-digit local number (01XXXXXXXXX); the field shows +880 and the 10 digits after 0. */
@Composable
fun PhoneLoginScreen(state: AuthState, onPhone: (String) -> Unit, onSend: () -> Unit) {
    val local = state.phone.removePrefix("0")
    BrandedAuthLayout {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge, color = DP.Ink)
        Text(stringResource(R.string.login_sub), color = DP.Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = local.toLocalDigits(), onValueChange = { v -> val d = DigitFormatter.digitsOnly(v).removePrefix("0").take(10); onPhone(if (d.isEmpty()) "" else "0$d") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🇧🇩", fontSize = 18.sp); Text(" +880".toLocalDigits(), fontWeight = FontWeight.SemiBold, color = DP.Ink)
                    Box(Modifier.padding(start = 10.dp).width(1.dp).height(24.dp).background(DP.Line))
                }
            },
            placeholder = { Text(stringResource(R.string.phone_placeholder)) }, label = { Text(stringResource(R.string.mobile_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = state.error != null, supportingText = state.error?.let { { Text(it.asString()) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DP.Primary500, focusedLabelColor = DP.Primary500),
        )
        AccentCta(stringResource(if (state.loading) R.string.sending_code else R.string.send_otp), local.length == 10, state.loading, onSend)
        val tPre = stringResource(R.string.terms_prefix); val tDrv = stringResource(R.string.terms_driver); val tAnd = stringResource(R.string.terms_and)
        val tPriv = stringResource(R.string.terms_privacy); val tSuf = stringResource(R.string.terms_suffix)
        Text(buildAnnotatedString {
            append(tPre)
            withStyle(SpanStyle(color = DP.Primary500, fontWeight = FontWeight.SemiBold)) { append(tDrv) }
            append(tAnd)
            withStyle(SpanStyle(color = DP.Primary500, fontWeight = FontWeight.SemiBold)) { append(tPriv) }
            append(tSuf)
        }, fontSize = 12.sp, color = DP.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Six separate digit boxes backed by one BasicTextField (paste & IME friendly). */
@Composable
fun OtpBoxes(code: String, onChange: (String) -> Unit, isError: Boolean) {
    BasicTextField(
        value = code, onValueChange = { onChange(DigitFormatter.digitsOnly(it, 6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true,
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = code.getOrNull(i)?.toString()?.toLocalDigits() ?: ""
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
fun OtpScreen(phone: String, error: com.durpalla.driver.i18n.UiText?, onBack: () -> Unit, onVerify: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(30) }
    LaunchedEffect(seconds) { if (seconds > 0) { delay(1000); seconds-- } }
    val shown = phone.removePrefix("0").let { if (it.length == 10) "+880 ${it.take(4)}-${it.drop(4)}" else "+880 $it" }
    BrandedAuthLayout(onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(DP.Primary100), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = DP.Primary500, modifier = Modifier.size(18.dp)) }
            Text("  " + stringResource(R.string.otp_title), style = MaterialTheme.typography.titleLarge, color = DP.Ink)
        }
        val sentTo = stringResource(R.string.otp_sent_to)
        Text(buildAnnotatedString { append(sentTo); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = DP.Ink)) { append(shown.toLocalDigits()) } }, color = DP.Muted, fontSize = 13.sp)
        OtpBoxes(code, { code = it }, error != null)
        if (error != null) Text(error.asString(), color = DP.Danger, fontSize = 12.sp)
        AccentCta(stringResource(R.string.otp_verify), code.length == 6) { onVerify(code) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.otp_didnt_get), color = DP.Muted, fontSize = 13.sp)
            Text(if (seconds > 0) str(R.string.otp_resend_in, "0:${seconds.toString().padStart(2, '0')}") else stringResource(R.string.otp_resend), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (seconds > 0) DP.Muted else DP.Primary500, modifier = Modifier.clickable(enabled = seconds == 0) { seconds = 30 })
        }
        Text(stringResource(R.string.otp_wrong_number), color = DP.Primary500, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack))
    }
}
