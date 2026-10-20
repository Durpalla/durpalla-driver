package com.durpalla.driver.i18n

/**
 * Central digit handling.
 *  - [toLocalDigits] is for DISPLAY only: in Bangla mode ASCII 0-9 become ০-৯.
 *  - [toAsciiDigits] NORMALISES any input (Bangla ০-৯, Arabic-Indic, Eastern Arabic, full-width) to ASCII 0-9.
 * Everything that is validated, stored, sent to the API / Pusher or parsed must go through [toAsciiDigits]
 * and must never be produced by locale formatting.
 */
object DigitFormatter {
    private const val BN_ZERO = '০' // U+09E6

    fun toLocalDigits(s: String, bangla: Boolean = AppLocale.isBangla): String {
        if (!bangla) return s
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') BN_ZERO + (c - '0') else c)
        return sb.toString()
    }

    fun toAsciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val v = asciiValue(c)
            sb.append(if (v >= 0) '0' + v else c)
        }
        return sb.toString()
    }

    /** Normalises then keeps only ASCII digits (for phone, OTP, PIN, money inputs). */
    fun digitsOnly(s: String, max: Int = Int.MAX_VALUE): String = toAsciiDigits(s).filter { it in '0'..'9' }.take(max)

    /** Locale-independent integer parse that also accepts Bangla digits. */
    fun parseInt(s: String): Int? = digitsOnly(s).takeIf { it.isNotEmpty() && it.length <= 9 }?.toInt()

    private fun asciiValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in '০'..'৯' -> c - '০'            // Bengali
        in '٠'..'٩' -> c - '٠'            // Arabic-Indic
        in '۰'..'۹' -> c - '۰'            // Extended Arabic-Indic
        in '०'..'९' -> c - '०'            // Devanagari
        in '０'..'９' -> c - '０'          // Full-width
        else -> -1
    }
}

fun String.toLocalDigits(): String = DigitFormatter.toLocalDigits(this)
fun Number.l(): String = DigitFormatter.toLocalDigits(toString())
