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
    /** Hook so the fake can simulate the customer's reaction to the driver's own bid. No-op for Pusher. */
    fun onOwnBidPlaced(bid: Bid) {}
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
        if (pusher != null) return
        tokenProvider = authToken
        val authorizer = HttpChannelAuthorizer(authUrl).apply { setHeaders(mapOf("Authorization" to "Bearer ${authToken().orEmpty()}")) }
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

/**
 * Default client: simulates the backend so the app feels live without credentials.
 * Competitor bids arrive every few seconds, a new request after ~20s, and ~8s after the driver bids the
 * customer accepts it (or rejects when the driver isn't the lowest).
 */
class FakeRealtimeClient(private val scope: CoroutineScope, private val openRequests: () -> List<TripRequest>, private val speed: Long = 1) : RealtimeClient {
    private val _conn = MutableStateFlow(ConnectionState.OFFLINE); override val connection: StateFlow<ConnectionState> = _conn
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64); override val events: SharedFlow<RealtimeEvent> = _events
    private var job: Job? = null
    private val counts = mutableMapOf<String, Int>(); private val lowest = mutableMapOf<String, Int>()

    override fun connect(driverId: String, authToken: () -> String?) {
        if (job != null) return
        job = scope.launch {
            _conn.value = ConnectionState.CONNECTING; delay(800 / speed); _conn.value = ConnectionState.LIVE
            var tick = 0
            while (isActive) {
                delay(6000 / speed); tick++
                openRequests().randomOrNull()?.let { r ->
                    val n = (counts[r.id] ?: r.bidsCount) + 1; counts[r.id] = n
                    val bid = (r.suggestedMin..r.suggestedMax).random() / 50 * 50
                    val low = minOf(lowest[r.id] ?: bid, bid); lowest[r.id] = low
                    _events.emit(RealtimeEvent.BidPlaced(r.id, n, low))
                }
                if (tick == 3) _events.emit(RealtimeEvent.TripRequested(sampleRequest()))
                if (tick % 10 == 7) { _conn.value = ConnectionState.RECONNECTING; delay(1500 / speed); _conn.value = ConnectionState.LIVE }
            }
        }
    }

    override fun onOwnBidPlaced(bid: Bid) {
        scope.launch {
            delay(8000 / speed)
            val low = lowest[bid.requestId]
            if (low == null || bid.amount <= low + 200) {
                _events.emit(RealtimeEvent.BidAccepted(bid.id, bid.requestId, "t-live-${bid.requestId}", "DP-${(25900..25999).random()}", bid.amount / 4))
            } else _events.emit(RealtimeEvent.BidRejected(bid.id, bid.requestId, ""))
        }
    }

    private fun sampleRequest(): TripRequest {
        val start = LocalDateTime.now().plusHours(3).withMinute(0).withSecond(0).withNano(0)
        return TripRequest("r-live-${System.currentTimeMillis() % 10000}", VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", TimeSlot(start, start.plusMinutes(90)),
            6, Customer("Farhana Akter", 4.9, 12), 2600, 3300, 0, 120, "Live request (simulated)", 18.5)
    }

    override fun watchTrip(tripId: String) {}
    override fun unwatchTrip(tripId: String) {}
    override fun disconnect() { job?.cancel(); job = null; _conn.value = ConnectionState.OFFLINE }
    override fun inject(event: RealtimeEvent) { _events.tryEmit(event) }
}
