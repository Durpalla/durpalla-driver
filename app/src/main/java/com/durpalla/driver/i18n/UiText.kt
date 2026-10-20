package com.durpalla.driver.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Text produced outside Compose (ViewModel, repositories, realtime) and resolved in the current language at display time. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
    /** Money, formatted for the language at resolve time. */
    data class Taka(val amount: Int) : UiText

    fun resolve(ctx: Context): String = when (this) {
        is Raw -> text.toLocalDigits()
        is Taka -> com.durpalla.driver.domain.taka(amount)
        is Res -> ctx.getString(id, *args.map { arg(ctx, it) }.toTypedArray())
        is Plural -> ctx.resources.getQuantityString(id, count, *args.map { arg(ctx, it) }.toTypedArray())
    }

    companion object {
        /** Numbers are localized here (strings.xml uses %s for them) so digits never depend on Formatter locale data. */
        internal fun arg(ctx: Context, a: Any): Any = when (a) { is UiText -> a.resolve(ctx); is Number -> a.l(); is String -> a.toLocalDigits(); else -> a }
        fun res(@StringRes id: Int, vararg args: Any) = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any) = Plural(id, count, args.toList())
    }
}

@Composable fun UiText.asString(): String = resolve(LocalContext.current)

/** Error carrying a translatable message (thrown by repositories / validation). */
class UserFacingException(val text: UiText) : RuntimeException(text.toString())

fun Throwable.toUiText(@StringRes fallback: Int): UiText = (this as? UserFacingException)?.text ?: UiText.res(fallback)

/** Server reasons are free text; empty or the English defaults map to translated strings. */
fun reasonText(reason: String, @StringRes fallback: Int): UiText =
    if (reason.isBlank() || reason in setOf("Customer chose another driver", "Cancelled by customer", "Customer chose a lower bid")) UiText.res(fallback) else UiText.Raw(reason)

object L10n {
    /** A context whose resources use the app language (needed for services / notifications on Android < 13). */
    fun localized(ctx: Context): Context {
        val want = AppLocale.locale
        if (ctx.resources.configuration.locales[0].language == want.language) return ctx
        val conf = android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(want) }
        return ctx.createConfigurationContext(conf)
    }
}
