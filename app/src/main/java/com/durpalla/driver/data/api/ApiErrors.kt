package com.durpalla.driver.data.api

import com.durpalla.driver.R
import com.durpalla.driver.i18n.UiText

/** apigw error codes → translated UI text (Bangla/English). Unknown codes fall back to the screen's own message. */
object ApiErrors {
    fun text(e: ApiException): UiText? = when (e.code) {
        ApiException.NETWORK -> UiText.res(R.string.err_network)
        ApiException.UNAUTHORIZED -> UiText.res(R.string.err_session_expired)
        "RATE_LIMITED" -> UiText.res(R.string.err_rate_limited)
        "OTP_INVALID" -> UiText.res(R.string.err_otp)
        "OTP_EXPIRED" -> UiText.res(R.string.err_otp_expired)
        "ACCOUNT_SUSPENDED" -> UiText.res(R.string.err_account_suspended)
        "RECAPTCHA_FAILED", "RECAPTCHA_REQUIRED", "RECAPTCHA_UNAVAILABLE" -> UiText.res(R.string.err_recaptcha)
        "KYC_REQUIRED" -> UiText.res(R.string.err_kyc_required)
        "VEHICLE_NOT_ASSIGNED" -> UiText.res(R.string.err_vehicle_not_assigned)
        "SLOT_CONFLICT" -> UiText.res(R.string.err_slot_conflict)
        "REQUEST_CLOSED", "REQUEST_NOT_FOUND", "NOT_FOUND" -> UiText.res(R.string.err_request_gone)
        "BID_NOT_PENDING" -> UiText.res(R.string.err_bid_not_pending)
        "INVALID_TRANSITION" -> UiText.res(R.string.err_invalid_transition)
        "PIN_NOT_SET" -> UiText.res(R.string.err_pin_not_set)
        "PIN_INVALID" -> UiText.res(R.string.err_pin)
        "AMOUNT_TOO_LOW" -> UiText.res(R.string.err_min_withdraw, UiText.Taka(500))
        "INSUFFICIENT_BALANCE" -> UiText.res(R.string.err_exceeds)
        "KYC_DOCUMENTS_REQUIRED" -> UiText.res(R.string.err_kyc_docs)
        else -> null
    }
}
