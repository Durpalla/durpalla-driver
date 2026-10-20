package com.durpalla.driver.notifications

import com.durpalla.driver.i18n.UiText
import com.durpalla.driver.i18n.L10n
import com.durpalla.driver.i18n.reasonText
import com.durpalla.driver.ui.res
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.durpalla.driver.MainActivity
import com.durpalla.driver.R
import com.durpalla.driver.domain.taka
import com.durpalla.driver.realtime.RealtimeEvent

/** Where a notification tap should land. Carried as the `route` extra / FCM `route` data key. */
object DeepLink {
    const val EXTRA = "durpalla_route"
    fun request(id: String) = "request/$id"
    fun trip(id: String) = "trip/$id"
    fun won(tripId: String) = "won/$tripId"
    const val BIDS = "requests?tab=1"
    const val WALLET = "wallet"
}

object Channels {
    const val TRIP_REQUESTS = "trip_requests"; const val BIDS = "bids"; const val PAYMENTS = "payments"; const val GENERAL = "general"

    fun create(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        // Names/descriptions follow the app language; re-creating with the same id just renames the channel.
        val c = L10n.localized(ctx)
        nm.createNotificationChannels(listOf(
            NotificationChannel(TRIP_REQUESTS, c.getString(R.string.ch_trip_requests), NotificationManager.IMPORTANCE_HIGH).apply {
                description = c.getString(R.string.ch_trip_requests_desc); enableVibration(true); setSound(sound, attrs)
            },
            NotificationChannel(BIDS, c.getString(R.string.ch_bids), NotificationManager.IMPORTANCE_HIGH).apply { description = c.getString(R.string.ch_bids_desc) },
            NotificationChannel(PAYMENTS, c.getString(R.string.ch_payments), NotificationManager.IMPORTANCE_DEFAULT).apply { description = c.getString(R.string.ch_payments_desc) },
            NotificationChannel(GENERAL, c.getString(R.string.ch_general), NotificationManager.IMPORTANCE_LOW).apply { description = c.getString(R.string.ch_general_desc) },
        ))
    }
}

data class NotificationSpec(val channel: String, val title: UiText, val body: UiText, val route: String, val urgent: Boolean = false)

object NotificationFactory {
    /** Maps an event to what (if anything) should be shown. Pure; unit-testable. */
    fun spec(e: RealtimeEvent): NotificationSpec? = when (e) {
        is RealtimeEvent.TripRequested -> NotificationSpec(Channels.TRIP_REQUESTS, UiText.res(R.string.banner_new_request, UiText.res(e.request.vehicleType.res)),
            UiText.res(R.string.n_request_body, e.request.from, e.request.to, UiText.plural(R.plurals.pax, e.request.passengers, e.request.passengers),
                UiText.Taka(e.request.suggestedMin), UiText.Taka(e.request.suggestedMax)), DeepLink.request(e.request.id), urgent = true)
        is RealtimeEvent.BidAccepted -> NotificationSpec(Channels.BIDS, UiText.res(R.string.n_accepted_title), UiText.res(R.string.n_accepted_body, e.tripCode), DeepLink.won(e.tripId), urgent = true)
        is RealtimeEvent.BidRejected -> NotificationSpec(Channels.BIDS, UiText.res(R.string.banner_not_selected), reasonText(e.reason, R.string.reason_other_driver), DeepLink.BIDS)
        is RealtimeEvent.TripCancelled -> NotificationSpec(Channels.GENERAL, UiText.res(R.string.banner_cancelled), reasonText(e.reason, R.string.reason_cancelled_customer), DeepLink.trip(e.tripId))
        is RealtimeEvent.PaymentReceived -> NotificationSpec(Channels.PAYMENTS, UiText.res(R.string.banner_payment), UiText.res(R.string.banner_payment_body, UiText.Taka(e.amount), UiText.res(e.method.res)), DeepLink.WALLET)
        else -> null
    }
}

object Notifier {
    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(base: Context, spec: NotificationSpec) {
        if (!canPost(base)) return
        val ctx = L10n.localized(base)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DeepLink.EXTRA, spec.route)
        }
        val pi = PendingIntent.getActivity(ctx, spec.route.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, spec.channel)
            .setSmallIcon(R.drawable.ic_stat_durpalla).setColor(0xFF0E5F7E.toInt())
            .setContentTitle(spec.title.resolve(ctx)).setContentText(spec.body.resolve(ctx)).setStyle(NotificationCompat.BigTextStyle().bigText(spec.body.resolve(ctx)))
            .setAutoCancel(true).setContentIntent(pi)
            .setPriority(if (spec.urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (spec.urgent) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .apply { if (spec.urgent) setFullScreenIntent(pi, true) }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(spec.route.hashCode(), n)
    }
}
