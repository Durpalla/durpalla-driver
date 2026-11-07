package com.durpalla.driver.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsOtpParserTest {
    @Test fun extractsStandaloneSixDigits() {
        assertEquals("482913", SmsOtpParser.codeFromText("Your Durpalla code is 482913. Do not share."))
        assertEquals("111111", SmsOtpParser.codeFromText("OTP: 111111"))
    }

    @Test fun acceptsBanglaDigits() {
        assertEquals("482913", SmsOtpParser.codeFromText("কোড ৪৮২৯১৩ ব্যবহার করুন"))
    }

    @Test fun rejectsWrongLengthOrEmbedded() {
        assertNull(SmsOtpParser.codeFromText(null))
        assertNull(SmsOtpParser.codeFromText(""))
        assertNull(SmsOtpParser.codeFromText("code 12345 only"))
        assertNull(SmsOtpParser.codeFromText("ref 1234567 too long"))
    }
}
