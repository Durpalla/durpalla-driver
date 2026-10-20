package com.durpalla.driver.notifications

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
        nm.createNotificationChannels(listOf(
            NotificationChannel(TRIP_REQUESTS, "Trip requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New customer trip requests near you"; enableVibration(true); setSound(sound, attrs)
            },
            NotificationChannel(BIDS, "Bids", NotificationManager.IMPORTANCE_HIGH).apply { description = "Bid accepted, outbid, rejected" },
            NotificationChannel(PAYMENTS, "Payments", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Payments and payouts" },
            NotificationChannel(GENERAL, "General", NotificationManager.IMPORTANCE_LOW),
        ))
    }
}

data class NotificationSpec(val channel: String, val title: String, val body: String, val route: String, val urgent: Boolean = false)

object NotificationFactory {
    /** Maps an event to what (if anything) should be shown. Pure; unit-testable. */
    fun spec(e: RealtimeEvent): NotificationSpec? = when (e) {
        is RealtimeEvent.TripRequested -> NotificationSpec(Channels.TRIP_REQUESTS, "New ${e.request.vehicleType.label} request",
            "${e.request.from} → ${e.request.to} · ${e.request.passengers} pax · ${taka(e.request.suggestedMin)}–${taka(e.request.suggestedMax)}", DeepLink.request(e.request.id), urgent = true)
        is RealtimeEvent.BidAccepted -> NotificationSpec(Channels.BIDS, "🎉 Customer accepted your bid!", "Trip ${e.tripCode} confirmed. Slot locked.", DeepLink.won(e.tripId), urgent = true)
        is RealtimeEvent.BidRejected -> NotificationSpec(Channels.BIDS, "Bid not selected", e.reason, DeepLink.BIDS)
        is RealtimeEvent.TripCancelled -> NotificationSpec(Channels.GENERAL, "Trip cancelled", e.reason, DeepLink.trip(e.tripId))
        is RealtimeEvent.PaymentReceived -> NotificationSpec(Channels.PAYMENTS, "Payment received", "${taka(e.amount)} via ${e.method.label}", DeepLink.WALLET)
        else -> null
    }
}

object Notifier {
    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, spec: NotificationSpec) {
        if (!canPost(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DeepLink.EXTRA, spec.route)
        }
        val pi = PendingIntent.getActivity(ctx, spec.route.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, spec.channel)
            .setSmallIcon(R.drawable.ic_stat_durpalla).setColor(0xFF0E5F7E.toInt())
            .setContentTitle(spec.title).setContentText(spec.body).setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setAutoCancel(true).setContentIntent(pi)
            .setPriority(if (spec.urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (spec.urgent) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .apply { if (spec.urgent) setFullScreenIntent(pi, true) }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(spec.route.hashCode(), n)
    }
}
