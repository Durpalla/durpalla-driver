package com.durpalla.driver.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DP {
    // Driver-app primary (deeper teal-blue, distinct from customer app)
    val Primary500 = Color(0xFF0E5F7E)
    val Primary600 = Color(0xFF0A4A63)
    val Primary700 = Color(0xFF073547)
    val Primary200 = Color(0xFFCCE2EA)
    val Primary100 = Color(0xFFE6F1F5)
    val Logo = Color(0xFF106E9D)
    val Accent = Color(0xFFF28C28)
    val AccentSoft = Color(0xFFFFF1E3)
    val Success = Color(0xFF16A34A)
    val SuccessSoft = Color(0xFFE7F6EC)
    val Warning = Color(0xFFEAB308)
    val WarningSoft = Color(0xFFFEF7DC)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFDECEC)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Bg = Color(0xFFF5F8FB)
}

private val scheme = lightColorScheme(
    primary = DP.Primary500, onPrimary = Color.White,
    primaryContainer = DP.Primary100, onPrimaryContainer = DP.Primary700,
    secondary = DP.Accent, onSecondary = Color.White,
    secondaryContainer = DP.Primary100, onSecondaryContainer = DP.Primary700,
    tertiary = DP.Logo,
    error = DP.Danger, errorContainer = DP.DangerSoft,
    background = DP.Bg, onBackground = DP.Ink,
    surface = Color.White, onSurface = DP.Ink,
    surfaceVariant = DP.Bg, onSurfaceVariant = DP.Muted,
    outline = DP.Line, outlineVariant = DP.Line,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
)

@Composable
fun DurpallaTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
