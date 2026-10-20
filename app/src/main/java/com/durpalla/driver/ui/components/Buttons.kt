package com.durpalla.driver.ui.components

import com.durpalla.driver.i18n.toLocalDigits
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.ui.theme.DP

/** Brand button variants. Use [ButtonVariant.Accent] only for the key CTA of a screen (Place bid, Send OTP, Withdraw…). */
enum class ButtonVariant { Primary, Accent, Secondary, Text, Danger, OnDark }
enum class ButtonSize(val height: Dp, val font: Int) { Large(54.dp, 16), Medium(46.dp, 15), Small(36.dp, 13) }

val DurpallaButtonShape = RoundedCornerShape(14.dp)

@Composable
fun DurpallaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fillWidth: Boolean = true,
) {
    val m = (if (fillWidth) modifier.fillMaxWidth() else modifier).height(size.height)
    val active = enabled && !loading
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = contentFor(variant))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.toLocalDigits(), fontWeight = FontWeight.SemiBold, fontSize = size.font.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    val pad = PaddingValues(horizontal = if (size == ButtonSize.Small) 14.dp else 20.dp)
    when (variant) {
        ButtonVariant.Primary, ButtonVariant.Accent, ButtonVariant.Danger, ButtonVariant.OnDark -> {
            val bg = when (variant) { ButtonVariant.Accent -> DP.Accent; ButtonVariant.Danger -> DP.Danger; ButtonVariant.OnDark -> Color.White.copy(alpha = 0.16f); else -> DP.Primary500 }
            Button(onClick, m, enabled = active || loading, shape = DurpallaButtonShape, contentPadding = pad,
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Color.White, disabledContainerColor = if (loading) bg else bg.copy(alpha = 0.4f), disabledContentColor = Color.White.copy(alpha = 0.9f)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)) {
                if (loading) Row(verticalAlignment = Alignment.CenterVertically, content = content) else content()
            }
        }
        ButtonVariant.Secondary -> OutlinedButton(onClick, m, enabled = active, shape = DurpallaButtonShape, contentPadding = pad,
            border = BorderStroke(1.5.dp, if (active) DP.Primary500 else DP.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DP.Primary500, disabledContentColor = DP.Muted), content = content)
        ButtonVariant.Text -> TextButton(onClick, if (fillWidth) modifier.fillMaxWidth() else modifier, enabled = active, shape = DurpallaButtonShape,
            colors = ButtonDefaults.textButtonColors(contentColor = DP.Primary500, disabledContentColor = DP.Muted), content = content)
    }
}

private fun contentFor(v: ButtonVariant) = when (v) { ButtonVariant.Secondary, ButtonVariant.Text -> DP.Primary500; else -> Color.White }

/** Brand icon button (round, tinted). */
@Composable
fun DurpallaIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) =
    FilledIconButton(onClick, modifier, colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = if (danger) DP.DangerSoft else DP.Primary100, contentColor = if (danger) DP.Danger else DP.Primary500)) { Icon(icon, contentDescription) }

/** Brand FAB (accent). */
@Composable
fun DurpallaFab(text: String, icon: ImageVector, onClick: () -> Unit) =
    ExtendedFloatingActionButton(onClick = onClick, containerColor = DP.Accent, contentColor = Color.White, shape = DurpallaButtonShape,
        icon = { Icon(icon, null) }, text = { Text(text.toLocalDigits(), fontWeight = FontWeight.SemiBold) })
