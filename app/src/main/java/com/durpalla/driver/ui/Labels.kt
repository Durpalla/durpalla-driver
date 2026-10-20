package com.durpalla.driver.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.durpalla.driver.R
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.l
import com.durpalla.driver.realtime.ConnectionState

@get:StringRes val VehicleType.res: Int get() = when (this) {
    VehicleType.SPEEDBOAT -> R.string.vt_speedboat; VehicleType.BOAT -> R.string.vt_boat; VehicleType.TAXI -> R.string.vt_taxi; VehicleType.MICROBUS -> R.string.vt_microbus
}
@get:StringRes val BidStatus.res: Int get() = when (this) { BidStatus.PENDING -> R.string.bs_pending; BidStatus.WON -> R.string.bs_won; BidStatus.LOST -> R.string.bs_lost; BidStatus.EXPIRED -> R.string.bs_expired }
@get:StringRes val PaymentMethod.res: Int get() = when (this) { PaymentMethod.CASH -> R.string.pm_cash; PaymentMethod.BKASH -> R.string.pm_bkash; PaymentMethod.NAGAD -> R.string.pm_nagad; PaymentMethod.BANK -> R.string.pm_bank }
@get:StringRes val DocStatus.res: Int get() = when (this) { DocStatus.VERIFIED -> R.string.ds_verified; DocStatus.UNDER_REVIEW -> R.string.ds_review; DocStatus.MISSING -> R.string.ds_missing; DocStatus.EXPIRING -> R.string.ds_expiring }
@get:StringRes val TripStatus.res: Int get() = when (this) {
    TripStatus.UPCOMING -> R.string.ts_upcoming; TripStatus.ON_THE_WAY -> R.string.ts_on_the_way; TripStatus.ARRIVED -> R.string.ts_arrived
    TripStatus.IN_PROGRESS -> R.string.ts_in_progress; TripStatus.COMPLETED -> R.string.ts_completed; TripStatus.CANCELLED -> R.string.ts_cancelled
}
@get:StringRes val ConnectionState.res: Int get() = when (this) {
    ConnectionState.CONNECTING -> R.string.cs_connecting; ConnectionState.LIVE -> R.string.cs_live; ConnectionState.RECONNECTING -> R.string.cs_reconnecting; ConnectionState.OFFLINE -> R.string.cs_offline
}

@Composable fun VehicleType.title() = stringResource(res)
@Composable fun BidStatus.title() = stringResource(res)
@Composable fun PaymentMethod.title() = stringResource(res)
@Composable fun DocStatus.title() = stringResource(res)
@Composable fun TripStatus.title() = stringResource(res)
@Composable fun ConnectionState.title() = stringResource(res)

/** stringResource that localizes digits of every argument (strings.xml uses %s for numbers). */
@Composable fun str(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args.map(::localArg).toTypedArray())
@Composable fun plural(id: Int, count: Int): String = pluralStringResource(id, count, count.l())
@Composable fun pax(n: Int) = plural(R.plurals.pax, n)

internal fun localArg(a: Any): Any = when (a) { is Number -> a.l(); is String -> com.durpalla.driver.i18n.DigitFormatter.toLocalDigits(a); else -> a }
