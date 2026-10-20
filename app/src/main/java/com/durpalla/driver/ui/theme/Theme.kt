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
import androidx.compose.ui.unit.em
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import com.durpalla.driver.R
import com.durpalla.driver.i18n.AppLocale

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

/** SolaimanLipi (Ekushey / OmicronLab, SIL OFL 1.0) for Bangla. Single regular cut; bold is synthesized. */
val SolaimanLipi = FontFamily(Font(R.font.solaiman_lipi, FontWeight.Normal))

private fun typography(bangla: Boolean): Typography {
    val family = if (bangla) SolaimanLipi else FontFamily.Default
    // Bangla needs more vertical room so matras (e.g. ি ী ু ূ) and conjuncts are not clipped.
    val lh = if (bangla) 1.5f else 1.3f
    fun st(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = family, fontSize = size.sp, fontWeight = w, lineHeight = lh.em, // em: scales with any fontSize override
        platformStyle = PlatformTextStyle(includeFontPadding = bangla),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    return Typography(
        displaySmall = st(34, FontWeight.Bold), headlineLarge = st(30, FontWeight.Bold),
        headlineMedium = st(26, FontWeight.Bold), headlineSmall = st(22, FontWeight.Bold),
        titleLarge = st(20, FontWeight.Bold), titleMedium = st(16, FontWeight.SemiBold), titleSmall = st(14, FontWeight.SemiBold),
        bodyLarge = st(15), bodyMedium = st(14), bodySmall = st(12),
        labelLarge = st(14, FontWeight.SemiBold), labelMedium = st(12, FontWeight.Medium), labelSmall = st(11, FontWeight.Medium),
    )
}
private val typographyEn = typography(false)
private val typographyBn = typography(true)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
)

@Composable
fun DurpallaTheme(bangla: Boolean = AppLocale.isBangla, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = if (bangla) typographyBn else typographyEn, shapes = shapes) {
        // Text() calls that set only fontSize still inherit the family + line height from LocalTextStyle.
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium, content = content)
    }
