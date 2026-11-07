package com.durpalla.driver.auth

import com.durpalla.driver.i18n.DigitFormatter

/** Pulls a standalone 6-digit OTP from an SMS (or clipboard) body. */
object SmsOtpParser {
    private val sixDigit = Regex("""(?<!\d)(\d{6})(?!\d)""")

    fun codeFromText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return sixDigit.find(DigitFormatter.toAsciiDigits(raw))?.groupValues?.get(1)
    }
}
