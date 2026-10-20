package com.durpalla.driver.call

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Agora voice (audio only, COMMUNICATION profile, SPEECH_STANDARD + CHATROOM). Ringing/status/missed/callLog are the
 * shared REST + Firestore calls/{callId} flow; there is no SDP/ICE. Token, App ID, channel (call_{callId}) and uid
 * come in `agora` of the POST /calls (caller) / accept (callee) response; renewed via POST /calls/{callId}/token on onTokenPrivilegeWillExpire.
 */
class AgoraCallProvider(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val api: CallApi,
    private val signaling: FirestoreSignaling,
) : CallProvider {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    private var engine: RtcEngine? = null
    private var engineAppId: String? = null
    private var listener: ListenerRegistration? = null
    private var timeout: Job? = null
    private var callId: String? = null
    private var asCaller = false
    private var joined = false
    private val quality = AgoraQuality()

    private fun set(s: CallState) { _state.value = s }
    private val busy get() = _state.value !is CallState.Idle && _state.value !is CallState.Ended

    override fun startCall(tripId: String, peerName: String) {
        if (busy) return
        set(CallState.Outgoing(tripId, peerName, null, System.currentTimeMillis()))
        scope.launch { api.start(tripId).onSuccess { begin(tripId, peerName, it) }.onFailure { fail(it) } }
    }

    override fun continueOutgoing(tripId: String, peerName: String, started: StartedCall) {
        if (busy) return
        set(CallState.Outgoing(tripId, peerName, started.callId, System.currentTimeMillis()))
        begin(tripId, peerName, started)
    }

    /** Caller joins the channel right away (so audio is ready on answer); the callee joins on Accept. */
    private fun begin(tripId: String, peerName: String, started: StartedCall) {
        val id = started.callId
        asCaller = true; callId = id
        (_state.value as? CallState.Outgoing)?.let { set(it.copy(callId = id)) }
        scope.launch {
            runCatching {
                signaling.ensureSignedIn()
                listener = signaling.listen(id) { d -> onDoc(d) }
                join(started.agora ?: throw CallException(CallError.PROVIDER_UNAVAILABLE, "no agora in start response"))
            }.onFailure { fail(it) }
        }
        armTimeout()
    }

    override fun onIncoming(call: IncomingCall) {
        if (busy) { scope.launch { api.decline(call.callId) }; return }
        asCaller = false; callId = call.callId
        set(CallState.Incoming(call, System.currentTimeMillis()))
        scope.launch { runCatching { signaling.ensureSignedIn(); listener = signaling.listen(call.callId) { d -> onDoc(d) } } }
        armTimeout()
    }

    override fun accept() {
        val s = _state.value as? CallState.Incoming ?: return
        timeout?.cancel()
        set(CallState.Connecting(s.call.callId, s.call.tripId, s.call.callerName))
        scope.launch { runCatching { val a = api.accept(s.call.callId).getOrThrow()
            join(a.agora ?: throw CallException(CallError.PROVIDER_UNAVAILABLE, "no agora in accept response")) }.onFailure { fail(it) } }
    }

    override fun decline() { val id = callId ?: return; scope.launch { api.decline(id) }; finish(EndReason.DECLINED) }

    override fun hangUp() {
        val id = callId; val ringing = _state.value is CallState.Outgoing
        if (id != null) scope.launch { api.end(id, if (ringing) "cancelled" else EndReason.HANGUP.wire) }
        finish(EndReason.HANGUP)
    }

    override fun setMuted(muted: Boolean) { engine?.muteLocalAudioStream(muted); (_state.value as? CallState.Active)?.let { set(it.copy(muted = muted)) } }
    override fun setSpeaker(on: Boolean) { engine?.setEnableSpeakerphone(on); (_state.value as? CallState.Active)?.let { set(it.copy(speaker = on)) } }
    override fun onRemoteEnded(callId: String, status: CallStatus) { if (callId == this.callId) remoteStatus(status) }
    override fun dismissEnded() { if (_state.value is CallState.Ended) set(CallState.Idle) }

    // --- internals

    private fun onDoc(d: CallDoc) {
        if (asCaller && d.status == CallStatus.ACCEPTED) (_state.value as? CallState.Outgoing)?.let {
            timeout?.cancel(); set(CallState.Connecting(d.callId, it.tripId, it.peerName))
        }
        if (d.status.terminal) remoteStatus(d.status)
    }

    private fun remoteStatus(status: CallStatus) {
        val next = CallMachine.onRemoteStatus(_state.value, status, System.currentTimeMillis(), asCaller)
        if (next is CallState.Ended) finish(next.reason)
    }

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

    private fun join(t: AgoraToken) {
        val e = engine(t.appId)
        val opts = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true; autoSubscribeAudio = true
            publishCameraTrack = false; autoSubscribeVideo = false
        }
        val rc = e.joinChannel(t.token, t.channel, t.uid, opts)
        if (rc < 0) throw CallException(CallError.UNKNOWN, "agora join $rc")
        joined = true
    }

    private fun engine(appId: String): RtcEngine {
        engine?.let { if (engineAppId == appId) return it }
        engine?.let { runCatching { RtcEngine.destroy() } }
        val cfg = RtcEngineConfig().apply {
            mContext = ctx.applicationContext; mAppId = appId; mEventHandler = handler
            mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            mAudioScenario = Constants.AUDIO_SCENARIO_CHATROOM
        }
        return RtcEngine.create(cfg).also {
            it.disableVideo(); it.enableAudio()
            it.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
            it.setDefaultAudioRoutetoSpeakerphone(false)
            engine = it; engineAppId = appId
        }
    }

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) { joined = true }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            val id = callId ?: return
            val (trip, name) = when (val cur = _state.value) { is CallState.Connecting -> cur.tripId to cur.peerName; is CallState.Outgoing -> cur.tripId to cur.peerName; else -> return }
            timeout?.cancel(); set(CallState.Active(id, trip, name, System.currentTimeMillis()))
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            // Network drop (reason 1) may come back; a real hang-up also flips calls/{callId}.status to ended.
            if (reason == Constants.USER_OFFLINE_QUIT) remoteStatus(CallStatus.ENDED)
        }
        override fun onTokenPrivilegeWillExpire(token: String?) {
            val id = callId ?: return
            scope.launch { api.renewToken(id).onSuccess { engine?.renewToken(it.token) }.onFailure { Log.w(TAG, "token renew failed", it) } }
        }
        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) { if (uid == 0) { quality.onNetworkQuality(txQuality, rxQuality); publishWeak() } }
        override fun onRtcStats(stats: RtcStats?) { stats ?: return; quality.onRtcStats(stats.txPacketLossRate, stats.rxPacketLossRate, stats.lastmileDelay); publishWeak() }
        override fun onError(err: Int) { Log.w(TAG, "agora error $err") }
    }

    private fun publishWeak() = scope.launch { (_state.value as? CallState.Active)?.let { if (it.weak != quality.weak) set(it.copy(weak = quality.weak)) } }

    private fun fail(t: Throwable) {
        Log.w(TAG, "call failed", t)
        val err = (t as? CallException)?.error ?: CallError.NETWORK
        callId?.let { id -> scope.launch { api.end(id, EndReason.FAILED.wire) } }
        finish(if (err == CallError.BUSY) EndReason.BUSY else EndReason.FAILED, err)
    }

    private fun finish(reason: EndReason, error: CallError? = null) {
        timeout?.cancel(); quality.reset()
        listener?.remove(); listener = null
        if (joined) runCatching { engine?.leaveChannel() }; joined = false
        runCatching { engine?.setEnableSpeakerphone(false) }
        set(CallMachine.ended(_state.value, reason, System.currentTimeMillis(), error))
        callId = null
    }

    private companion object { const val TAG = "DurpallaCall" }
}
