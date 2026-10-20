package com.durpalla.driver.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.net.ConnectivityManager
import android.net.Network
import org.webrtc.AudioTrack
import org.webrtc.RtpSender
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Audio-only WebRTC (unified plan) with Firestore signaling. Media never touches our servers except via TURN. */
class WebRtcCallProvider(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val api: CallApi,
    private val signaling: FirestoreSignaling,
    private val onStateChanged: (CallState) -> Unit = {},
) : CallProvider {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(ctx)
                // Hardware AEC/NS when the device has them; otherwise WebRTC's software APM (see audioConstraints).
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setUseStereoInput(false).setUseStereoOutput(false)
                .createAudioDeviceModule())
            .createPeerConnectionFactory()
    }
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bitrate = BitrateController()
    private var sender: RtpSender? = null
    private var statsJob: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastRemoteOffer: String? = null
    private var lastRemoteAnswer: String? = null

    private var pc: PeerConnection? = null
    private var track: AudioTrack? = null
    private var listeners = mutableListOf<ListenerRegistration>()
    private var timeout: Job? = null
    private var callId: String? = null
    private var asCaller = false
    private var remoteSet = false
    private val pendingRemote = mutableListOf<IceCandidateDto>()

    private fun set(s: CallState) { _state.value = s; onStateChanged(s) }
    private val busy get() = _state.value !is CallState.Idle && _state.value !is CallState.Ended

    override fun startCall(tripId: String, peerName: String) = begin(tripId, peerName, null)
    override fun continueOutgoing(tripId: String, peerName: String, callId: String) = begin(tripId, peerName, callId)

    private fun begin(tripId: String, peerName: String, started: String?) {
        if (busy) return
        asCaller = true
        set(CallState.Outgoing(tripId, peerName, null, System.currentTimeMillis()))
        scope.launch {
            runCatching {
                signaling.ensureSignedIn()
                val id = started ?: api.start(tripId).getOrThrow().callId; callId = id
                (_state.value as? CallState.Outgoing)?.let { set(it.copy(callId = id)) } ?: return@launch
                val peer = createPeer(id)
                val offer = peer.createOffer().munged()
                peer.setLocal(offer)
                signaling.writeOffer(id, Sdp("offer", offer.description))
                listeners += signaling.listenCandidates(id, asCaller = true) { addRemote(it) }
                listeners += signaling.listen(id) { d -> onDoc(d) }
                armTimeout()
            }.onFailure { fail(it) }
        }
    }

    override fun onIncoming(call: IncomingCall) {
        if (busy) { scope.launch { api.decline(call.callId) }; return }
        asCaller = false; callId = call.callId
        set(CallState.Incoming(call, System.currentTimeMillis()))
        scope.launch { runCatching { signaling.ensureSignedIn(); listeners += signaling.listen(call.callId) { d -> onDoc(d) } } }
        armTimeout()
    }

    override fun accept() {
        val s = _state.value as? CallState.Incoming ?: return
        timeout?.cancel()
        set(CallState.Connecting(s.call.callId, s.call.tripId, s.call.callerName))
        scope.launch {
            runCatching {
                signaling.ensureSignedIn()
                api.accept(s.call.callId).getOrThrow()
                val doc = signaling.read(s.call.callId) ?: throw CallException(CallError.NOT_FOUND)
                val offer = doc.offer ?: waitForOffer(s.call.callId)
                val peer = createPeer(s.call.callId)
                lastRemoteOffer = offer.sdp
                peer.setRemote(SessionDescription(SessionDescription.Type.OFFER, offer.sdp)); remoteSet = true; flushRemote()
                val answer = peer.createAnswer().munged()
                peer.setLocal(answer)
                signaling.writeAnswer(s.call.callId, Sdp("answer", answer.description))
                listeners += signaling.listenCandidates(s.call.callId, asCaller = false) { addRemote(it) }
            }.onFailure { fail(it) }
        }
    }

    override fun decline() {
        val id = callId ?: return
        scope.launch { api.decline(id) }
        finish(EndReason.DECLINED)
    }

    override fun hangUp() {
        val id = callId
        val wasRinging = _state.value is CallState.Outgoing
        if (id != null) scope.launch { api.end(id, if (wasRinging) "cancelled" else EndReason.HANGUP.wire) }
        finish(EndReason.HANGUP)
    }

    override fun setMuted(muted: Boolean) { track?.setEnabled(!muted); (_state.value as? CallState.Active)?.let { set(it.copy(muted = muted)) } }
    override fun setSpeaker(on: Boolean) { audio.isSpeakerphoneOn = on; (_state.value as? CallState.Active)?.let { set(it.copy(speaker = on)) } }
    override fun onRemoteEnded(callId: String, status: CallStatus) { if (callId == this.callId) remoteStatus(status) }
    override fun dismissEnded() { if (_state.value is CallState.Ended) set(CallState.Idle) }

    // --- internals

    private fun onDoc(d: CallDoc) {
        // ICE restart: the caller re-offers; the callee answers the new offer.
        if (!asCaller && remoteSet && d.offer != null && d.offer.sdp != lastRemoteOffer) scope.launch {
            runCatching { val peer = pc ?: return@launch; lastRemoteOffer = d.offer.sdp
                peer.setRemote(SessionDescription(SessionDescription.Type.OFFER, d.offer.sdp))
                val ans = peer.createAnswer().munged(); peer.setLocal(ans); signaling.writeAnswer(d.callId, Sdp("answer", ans.description)) }
        }
        if (asCaller && remoteSet && d.answer != null && d.answer.sdp != lastRemoteAnswer) scope.launch {
            runCatching { lastRemoteAnswer = d.answer.sdp; pc?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, d.answer.sdp)) }
        }
        if (asCaller && d.answer != null && !remoteSet) scope.launch {
            runCatching { lastRemoteAnswer = d.answer.sdp; pc?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, d.answer.sdp)); remoteSet = true; flushRemote()
                timeout?.cancel(); (_state.value as? CallState.Outgoing)?.let { set(CallState.Connecting(d.callId, it.tripId, it.peerName)) } }.onFailure { fail(it) }
        }
        if (d.status.terminal) remoteStatus(d.status)
    }

    private fun remoteStatus(status: CallStatus) {
        val next = CallMachine.onRemoteStatus(_state.value, status, System.currentTimeMillis(), asCaller)
        if (next is CallState.Ended) finish(next.reason)
    }

    /** 30 s without answer: caller ends with reason missed; callee stops ringing. Server applies the same timeout. */
    private fun armTimeout() {
        timeout?.cancel()
        timeout = scope.launch {
            delay(CallMachine.RING_TIMEOUT_MS)
            if (CallMachine.ringTimedOut(_state.value, System.currentTimeMillis())) {
                callId?.let { id -> if (asCaller) api.end(id, EndReason.MISSED.wire) }
                finish(EndReason.MISSED)
            }
        }
    }

    private fun fail(t: Throwable) {
        Log.w("DurpallaCall", "call failed", t)
        val err = (t as? CallException)?.error ?: CallError.NETWORK
        callId?.let { id -> scope.launch { api.end(id, EndReason.FAILED.wire) } }
        finish(if (err == CallError.BUSY) EndReason.BUSY else EndReason.FAILED, err)
    }

    private fun finish(reason: EndReason, error: CallError? = null) {
        timeout?.cancel(); statsJob?.cancel(); bitrate.reset(); sender = null
        netCallback?.let { runCatching { (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } }; netCallback = null
        lastRemoteOffer = null; lastRemoteAnswer = null
        listeners.forEach { it.remove() }; listeners.clear()
        runCatching { track?.dispose() }; track = null
        runCatching { pc?.close(); pc?.dispose() }; pc = null
        remoteSet = false; pendingRemote.clear()
        audio.mode = AudioManager.MODE_NORMAL; audio.isSpeakerphoneOn = false
        set(CallMachine.ended(_state.value, reason, System.currentTimeMillis(), error))
        callId = null
    }

    private fun addRemote(c: IceCandidateDto) {
        if (!remoteSet) { pendingRemote += c; return }
        pc?.addIceCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate))
    }
    private fun flushRemote() { val l = pendingRemote.toList(); pendingRemote.clear(); l.forEach(::addRemote) }

    private suspend fun waitForOffer(id: String): Sdp {
        repeat(20) { delay(250); signaling.read(id)?.offer?.let { return it } }
        throw CallException(CallError.NOT_FOUND, "no offer")
    }

    private suspend fun createPeer(id: String): PeerConnection {
        val ice = api.iceServers().getOrDefault(IceConfig.FALLBACK).servers.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply { s.username?.let(::setUsername); s.credential?.let(::setPassword) }.createIceServer()
        }
        val cfg = PeerConnection.RTCConfiguration(ice).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN; continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY }
        val peer = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) = signaling.addCandidate(id, asCaller, IceCandidateDto(c.sdp, c.sdpMid, c.sdpMLineIndex))
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                when (s) {
                    PeerConnection.IceConnectionState.CONNECTED -> { val cur = _state.value
                        val (trip, name) = when (cur) { is CallState.Connecting -> cur.tripId to cur.peerName; is CallState.Outgoing -> cur.tripId to cur.peerName; else -> return }
                        timeout?.cancel(); set(CallState.Active(id, trip, name, System.currentTimeMillis())); startStats(); watchNetwork() }
                    PeerConnection.IceConnectionState.DISCONNECTED -> scope.launch { restartIce() }
                    PeerConnection.IceConnectionState.FAILED -> scope.launch { fail(CallException(CallError.NETWORK, "ice failed")) }
                    else -> Unit
                }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(b: Boolean) = Unit
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) = Unit
            override fun onAddStream(s: MediaStream) = Unit
            override fun onRemoveStream(s: MediaStream) = Unit
            override fun onDataChannel(d: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }) ?: throw CallException(CallError.UNKNOWN, "peer connection")
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val src = factory.createAudioSource(audioConstraints)
        track = factory.createAudioTrack("durpalla-audio", src).also { sender = peer.addTrack(it, listOf("durpalla")) }
        applyBitrate(bitrate.bitrateBps)
        pc = peer
        return peer
    }

    /** WebRTC software APM: always requested; the hardware effects above take over where the device supports them. */
    private val audioConstraints = MediaConstraints().apply {
        listOf("googEchoCancellation", "googNoiseSuppression", "googAutoGainControl", "googHighpassFilter")
            .forEach { mandatory += MediaConstraints.KeyValuePair(it, "true") }
    }

    private fun SessionDescription.munged() = SessionDescription(type, SdpMunger.opusOnly(description))

    /** Caps the Opus encoder via RtpSender parameters (matches maxaveragebitrate). */
    private fun applyBitrate(bps: Int) {
        val s = sender ?: return
        runCatching { val p = s.parameters; p.encodings.forEach { it.maxBitrateBps = bps }; s.parameters = p }
    }

    /** getStats every 2 s → [BitrateController]; RTT from remote-inbound-rtp (fallback candidate-pair). */
    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                delay(AudioQuality.STATS_INTERVAL_MS)
                val peer = pc ?: break
                val (loss, rtt) = suspendCancellableCoroutine<Pair<Double?, Double?>> { k ->
                    peer.getStats { r ->
                        var l: Double? = null; var t: Double? = null
                        r.statsMap.values.forEach { st ->
                            if (st.type == "remote-inbound-rtp" && st.members["kind"] == "audio") {
                                (st.members["fractionLost"] as? Number)?.let { l = it.toDouble() }
                                (st.members["roundTripTime"] as? Number)?.let { t = it.toDouble() * 1000 }
                            }
                            if (t == null && st.type == "candidate-pair" && st.members["nominated"] == true)
                                (st.members["currentRoundTripTime"] as? Number)?.let { t = it.toDouble() * 1000 }
                        }
                        if (k.isActive) k.resume(l to t)
                    }
                }
                val before = bitrate.bitrateBps
                val now = bitrate.onSample(loss, rtt)
                if (now != before) applyBitrate(now)
                (_state.value as? CallState.Active)?.let { if (it.weak != bitrate.weak) set(it.copy(weak = bitrate.weak)) }
            }
        }
    }

    /** New network (Wi-Fi ↔ mobile): ICE restart. */
    private fun watchNetwork() {
        if (netCallback != null) return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var first = true
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (first) { first = false; return }; scope.launch { restartIce() } }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }

    /** The caller drives ICE restarts (new offer in calls/{callId}); the callee answers it in [onDoc]. */
    private suspend fun restartIce() {
        val peer = pc ?: return; val id = callId ?: return
        if (!asCaller) return // caller sees DISCONNECTED too and restarts
        runCatching {
            peer.restartIce()
            val o = suspendCancellableCoroutine<SessionDescription> { k -> peer.createOffer(sdp(k), MediaConstraints().apply {
                mandatory += MediaConstraints.KeyValuePair("IceRestart", "true"); mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true") }) }.munged()
            peer.setLocal(o); signaling.writeOffer(id, Sdp("offer", o.description))
        }.onFailure { Log.w("DurpallaCall", "ice restart failed", it) }
    }

    private val audioOnly = MediaConstraints().apply {
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
    }
    private suspend fun PeerConnection.createOffer(): SessionDescription = suspendCancellableCoroutine { k -> createOffer(sdp(k), audioOnly) }
    private suspend fun PeerConnection.createAnswer(): SessionDescription = suspendCancellableCoroutine { k -> createAnswer(sdp(k), audioOnly) }
    private suspend fun PeerConnection.setLocal(d: SessionDescription): Unit = suspendCancellableCoroutine { k -> setLocalDescription(set(k), d) }
    private suspend fun PeerConnection.setRemote(d: SessionDescription): Unit = suspendCancellableCoroutine { k -> setRemoteDescription(set(k), d) }

    private fun sdp(k: kotlinx.coroutines.CancellableContinuation<SessionDescription>) = object : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) { if (k.isActive) k.resume(d) }
        override fun onCreateFailure(e: String?) { if (k.isActive) k.resumeWithException(CallException(CallError.UNKNOWN, e)) }
        override fun onSetSuccess() = Unit
        override fun onSetFailure(e: String?) = Unit
    }
    private fun set(k: kotlinx.coroutines.CancellableContinuation<Unit>) = object : SdpObserver {
        override fun onSetSuccess() { if (k.isActive) k.resume(Unit) }
        override fun onSetFailure(e: String?) { if (k.isActive) k.resumeWithException(CallException(CallError.UNKNOWN, e)) }
        override fun onCreateSuccess(d: SessionDescription) = Unit
        override fun onCreateFailure(e: String?) = Unit
    }
}
