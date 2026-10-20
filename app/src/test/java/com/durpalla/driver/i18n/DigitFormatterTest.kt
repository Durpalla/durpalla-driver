package com.durpalla.driver.i18n

import com.durpalla.driver.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DigitFormatterTest {
    @After fun reset() { AppLocale.language = AppLanguage.ENGLISH; Locale.setDefault(Locale.US) }

    @Test fun toLocalDigitsInBangla() {
        assertEquals("৳৩,১০০ · ০১৭১১", DigitFormatter.toLocalDigits("৳3,100 · 01711", bangla = true))
        assertEquals("৳3,100", DigitFormatter.toLocalDigits("৳3,100", bangla = false))
        assertEquals("০১২৩৪৫৬৭৮৯", DigitFormatter.toLocalDigits("0123456789", true))
    }

    @Test fun toAsciiDigitsNormalisesEveryScript() {
        assertEquals("0123456789", DigitFormatter.toAsciiDigits("০১২৩৪৫৬৭৮৯"))
        assertEquals("01711-234567", DigitFormatter.toAsciiDigits("০১৭১১-২৩৪৫৬৭"))
        assertEquals("123", DigitFormatter.toAsciiDigits("١٢٣"))      // Arabic-Indic
        assertEquals("123", DigitFormatter.toAsciiDigits("１２３"))     // full-width
        assertEquals("abc", DigitFormatter.toAsciiDigits("abc"))
    }

    @Test fun roundTrip() {
        val s = "DP-25814 · 3,100 · 8:30"
        assertEquals(s, DigitFormatter.toAsciiDigits(DigitFormatter.toLocalDigits(s, true)))
    }

    @Test fun digitsOnlyAndParse() {
        assertEquals("01711234567", DigitFormatter.digitsOnly("০১৭১১ ২৩৪-৫৬৭"))
        assertEquals("1234", DigitFormatter.digitsOnly("১২৩৪৫৬", 4))
        assertEquals(3100, DigitFormatter.parseInt("৩১০০"))
        assertEquals(3100, DigitFormatter.parseInt("৩,১০০"))
        assertEquals(3100, DigitFormatter.parseInt("3100"))
        assertNull(DigitFormatter.parseInt(""))
        assertNull(DigitFormatter.parseInt("abc"))
    }

    @Test fun takaFollowsLanguage() {
        assertEquals("৳3,100", taka(3100, bangla = false))
        assertEquals("৳৩,১০০", taka(3100, bangla = true))
        assertEquals("৳১,২৩,৪৫৬", taka(123456, bangla = true))   // lakh grouping
        assertEquals("৳123,456", taka(123456, bangla = false))
        assertEquals("-৳৫০০", taka(-500, bangla = true))
        AppLocale.language = AppLanguage.BANGLA
        assertEquals("৳৯৯৯", taka(999))
        assertEquals("৪.৮", num(4.8))
    }

    @Test fun banglaDatesAndTimes() {
        val t = LocalDateTime.of(2026, 10, 23, 15, 30)
        assertEquals("বিকাল ৩:৩০", timeText(t, bangla = true))
        assertEquals("সকাল ৮:০৫", timeText(t.withHour(8).withMinute(5), bangla = true))
        assertEquals("রাত ১২:০০", timeText(t.withHour(0).withMinute(0), bangla = true))
        assertEquals("শুক্র, ২৩ অক্টোবর", dayText(LocalDate.of(2026, 10, 23), bangla = true))
        assertEquals("Fri 23 Oct", dayText(LocalDate.of(2026, 10, 23), bangla = false))
        assertEquals("3:30 PM", timeText(t, bangla = false))
    }

    @Test fun localeFormattingNeverReachesApiValues() {
        // Even with a Bangla default locale, ROOT/US formatting and Int.toString stay ASCII.
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        assertEquals("3100", 3100.toString())
        assertEquals("2026-10", "%04d-%02d".format(Locale.ROOT, 2026, 10))
        assertTrue(com.durpalla.driver.data.ApiJson.placeBid("r1", 3100, "").contains("\"amount\":3100"))
    }
}

class SlotLabelTest {
    @Test fun samePeriodIsNotRepeated() {
        val d = LocalDateTime.of(2026, 10, 21, 8, 0)
        assertEquals("সকাল ৮:০০–১০:০০", TimeSlot(d, d.plusHours(2)).timeLabel(bangla = true))
        assertEquals("দুপুর ১২:৩০–বিকাল ৩:০০", TimeSlot(d.withHour(12).withMinute(30), d.withHour(15)).timeLabel(bangla = true))
        assertEquals("8:00 AM–10:00 AM", TimeSlot(d, d.plusHours(2)).timeLabel(bangla = false))
    }
}
