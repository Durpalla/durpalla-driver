package com.durpalla.driver.data

import com.durpalla.driver.i18n.DigitFormatter
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Request bodies for the Durpalla API. Gson writes numbers with Java's locale-independent toString(),
 * and every free-form numeric string is normalised with [DigitFormatter.toAsciiDigits] here, so a Bangla
 * UI can never leak ০-৯ (or locale grouping) to the server. Never build these with String.format(Locale.getDefault()).
 */
object ApiJson {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    data class PlaceBid(val requestId: String, val amount: Int, val note: String)
    data class RequestOtp(val phone: String, val recaptchaToken: String?)
    data class VerifyOtp(val phone: String, val code: String)
    data class Withdraw(val method: String, val account: String, val amount: Int, val pin: String)

    fun placeBid(requestId: String, amount: Int, note: String): String = gson.toJson(PlaceBid(requestId, amount, note))
    fun requestOtp(phone: String, token: String?): String = gson.toJson(RequestOtp("+880" + DigitFormatter.digitsOnly(phone).removePrefix("0"), token))
    fun verifyOtp(phone: String, code: String): String = gson.toJson(VerifyOtp("+880" + DigitFormatter.digitsOnly(phone).removePrefix("0"), DigitFormatter.digitsOnly(code, 6)))
    fun withdraw(method: String, account: String, amount: Int, pin: String): String =
        gson.toJson(Withdraw(method, DigitFormatter.toAsciiDigits(account), amount, DigitFormatter.digitsOnly(pin, 4)))
}
