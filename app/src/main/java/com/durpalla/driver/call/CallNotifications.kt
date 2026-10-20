package com.durpalla.driver.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.durpalla.driver.MainActivity
import com.durpalla.driver.R
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.i18n.L10n

/** CallStyle notifications: full-screen incoming ring, and the ongoing-call notification of [CallService]. */
object CallNotifications {
    const val CHANNEL_INCOMING = "calls_incoming"; const val CHANNEL_ONGOING = "calls_ongoing"
    const val ID = 4401
    const val ACTION_ACCEPT = "com.durpalla.driver.call.ACCEPT"; const val ACTION_DECLINE = "com.durpalla.driver.call.DECLINE"; const val ACTION_HANGUP = "com.durpalla.driver.call.HANGUP"
    const val EXTRA_OPEN_CALL = "durpalla_open_call"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val c = L10n.localized(ctx); val nm = ctx.getSystemService(NotificationManager::class.java)
        val ring = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_INCOMING, c.getString(R.string.ch_calls_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                description = c.getString(R.string.ch_calls_incoming_desc); enableVibration(true); setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ring)
            },
            NotificationChannel(CHANNEL_ONGOING, c.getString(R.string.ch_calls_ongoing), NotificationManager.IMPORTANCE_LOW).apply { description = c.getString(R.string.ch_calls_ongoing_desc) },
        ))
    }

    private fun action(ctx: Context, a: String) = PendingIntent.getBroadcast(ctx, a.hashCode(), Intent(ctx, CallActionReceiver::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_CALL, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun incoming(ctx: Context, call: IncomingCall): Notification {
        val c = L10n.localized(ctx)
        val caller = Person.Builder().setName(call.callerName.ifBlank { c.getString(R.string.call_customer) }).setImportant(true).build()
        return NotificationCompat.Builder(ctx, CHANNEL_INCOMING).setSmallIcon(R.drawable.ic_stat_durpalla)
            .setContentTitle(caller.name).setContentText(c.getString(R.string.call_incoming))
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setFullScreenIntent(open(ctx), true).setContentIntent(open(ctx)).setTimeoutAfter(CallMachine.RING_TIMEOUT_MS)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, action(ctx, ACTION_DECLINE), action(ctx, ACTION_ACCEPT)))
            .build()
    }

    fun ongoing(ctx: Context, peer: String, startedAt: Long?): Notification {
        val c = L10n.localized(ctx)
        val p = Person.Builder().setName(peer.ifBlank { c.getString(R.string.call_customer) }).build()
        return NotificationCompat.Builder(ctx, CHANNEL_ONGOING).setSmallIcon(R.drawable.ic_stat_durpalla)
            .setContentTitle(p.name).setContentText(c.getString(if (startedAt != null) R.string.call_in_progress else R.string.call_calling))
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setContentIntent(open(ctx))
            .apply { if (startedAt != null) { setUsesChronometer(true); setWhen(startedAt) } }
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(p, action(ctx, ACTION_HANGUP)))
            .build()
    }

    fun showIncoming(ctx: Context, call: IncomingCall) {
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled())
            runCatching { NotificationManagerCompat.from(ctx).notify(ID, incoming(ctx, call)) }
    }
    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

/** Accept / decline / hang up from the notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val calls = ServiceLocator.calls
        when (i.action) {
            CallNotifications.ACTION_ACCEPT -> {
                CallNotifications.cancel(ctx)
                // Mic permission is checked in-app; open the call screen, which accepts once RECORD_AUDIO is granted.
                ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra(CallNotifications.EXTRA_OPEN_CALL, true).putExtra(EXTRA_ACCEPT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            CallNotifications.ACTION_DECLINE -> { calls.decline(); CallNotifications.cancel(ctx) }
            CallNotifications.ACTION_HANGUP -> calls.hangUp()
        }
    }
    companion object { const val EXTRA_ACCEPT = "durpalla_accept_call" }
}

/** Keeps the process and microphone alive during a call (foregroundServiceType=microphone). */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
        val started = intent?.getLongExtra(EXTRA_STARTED, 0L)?.takeIf { it > 0 }
        val n = CallNotifications.ongoing(this, peer, started)
        runCatching {
            ServiceCompat.startForeground(this, CallNotifications.ID + 1, n,
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_PEER = "peer"; private const val EXTRA_STARTED = "started"
        /** Called on every state change: runs the service while a call is outgoing/connecting/active. */
        fun sync(ctx: Context, s: CallState) {
            val app = ctx.applicationContext
            when (s) {
                is CallState.Outgoing, is CallState.Connecting, is CallState.Active -> {
                    CallNotifications.cancel(app)
                    val i = Intent(app, CallService::class.java).putExtra(EXTRA_PEER, CallMachine.peer(s)).putExtra(EXTRA_STARTED, (s as? CallState.Active)?.startedAt ?: 0L)
                    runCatching { ContextCompat.startForegroundService(app, i) }
                }
                is CallState.Incoming -> Unit
                CallState.Idle, is CallState.Ended -> { CallNotifications.cancel(app); app.stopService(Intent(app, CallService::class.java)) }
            }
        }
    }
}
