package com.durpalla.driver.realtime

import com.durpalla.driver.domain.*
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpChannelAuthorizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import com.pusher.client.connection.ConnectionState as PState

interface RealtimeClient {
    val connection: StateFlow<ConnectionState>
    val events: SharedFlow<RealtimeEvent>
    fun connect(driverId: String, authToken: () -> String?)
    fun watchTrip(tripId: String)
    fun unwatchTrip(tripId: String)
    fun disconnect()
    /** Lets FCM-delivered events join the same stream (app in foreground). */
    fun inject(event: RealtimeEvent)
}

/** Pusher Channels client. Private channels are authorised by the Durpalla backend ([authUrl]). */
class PusherRealtimeClient(private val key: String, private val cluster: String, private val authUrl: String) : RealtimeClient {
    private val _conn = MutableStateFlow(ConnectionState.OFFLINE); override val connection: StateFlow<ConnectionState> = _conn
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64); override val events: SharedFlow<RealtimeEvent> = _events
    private var pusher: Pusher? = null
    private var tokenProvider: () -> String? = { null }

    private val listener = object : PrivateChannelEventListener {
        override fun onEvent(event: PusherEvent) { EventParser.parse(event.eventName, event.data)?.let { _events.tryEmit(it) } }
        override fun onSubscriptionSucceeded(channelName: String) {}
        override fun onAuthenticationFailure(message: String?, e: Exception?) { _conn.value = ConnectionState.RECONNECTING }
    }

    override fun connect(driverId: String, authToken: () -> String?) {
        if (pusher != null) disconnect()
        tokenProvider = authToken
        // Sanctum bearer for POST {API}/realtime/auth (socket_id, channel_name); the token is never logged.
        val authorizer = HttpChannelAuthorizer(authUrl).apply { setHeaders(mapOf("Authorization" to "Bearer ${authToken().orEmpty()}", "Accept" to "application/json")) }
        val p = Pusher(key, PusherOptions().setCluster(cluster).setUseTLS(true).setChannelAuthorizer(authorizer))
        p.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                _conn.value = when (change.currentState) {
                    PState.CONNECTED -> ConnectionState.LIVE
                    PState.CONNECTING -> if (change.previousState == PState.DISCONNECTED) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
                    PState.RECONNECTING, PState.DISCONNECTING -> ConnectionState.RECONNECTING
                    else -> ConnectionState.OFFLINE
                }
            }
            override fun onError(message: String?, code: String?, e: Exception?) { _conn.value = ConnectionState.RECONNECTING }
        }, PState.ALL)
        p.subscribePrivate("private-driver.$driverId", listener, *EventNames.ALL.toTypedArray())
        pusher = p
    }
    override fun watchTrip(tripId: String) { pusher?.takeIf { it.getPrivateChannel("private-trip.$tripId") == null }?.subscribePrivate("private-trip.$tripId", listener, *EventNames.ALL.toTypedArray()) }
    override fun unwatchTrip(tripId: String) { pusher?.unsubscribe("private-trip.$tripId") }
    override fun disconnect() { pusher?.disconnect(); pusher = null; _conn.value = ConnectionState.OFFLINE }
    override fun inject(event: RealtimeEvent) { _events.tryEmit(event) }
}

/** Used when PUSHER_KEY isn't configured: no live channel (lists still load from the API; FCM events are injected). */
class DisabledRealtimeClient : RealtimeClient {
    override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.OFFLINE)
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64); override val events: SharedFlow<RealtimeEvent> = _events
    override fun connect(driverId: String, authToken: () -> String?) {}
    override fun watchTrip(tripId: String) {}
    override fun unwatchTrip(tripId: String) {}
    override fun disconnect() {}
    override fun inject(event: RealtimeEvent) { _events.tryEmit(event) }
}
