package com.durpalla.driver.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Calling backend: [CallProviderRouter] over [AgoraCallProvider] + [WebRtcCallProvider]; [UnavailableCallProvider] when Firebase isn't configured. */
interface CallProvider {
    val state: StateFlow<CallState>
    fun startCall(tripId: String, peerName: String)
    /** POST /calls already ran (by [CallProviderRouter]); continue the outgoing call on this provider. */
    fun continueOutgoing(tripId: String, peerName: String, started: StartedCall)
    /** FCM `call.incoming` (or the Pusher secondary path). Ignored when already in a call (server gets busy via decline). */
    fun onIncoming(call: IncomingCall)
    fun accept()
    fun decline()
    fun hangUp()
    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    /** Remote side ended/missed (push `call.ended`), when no Firestore listener is attached yet. */
    fun onRemoteEnded(callId: String, status: CallStatus)
    fun dismissEnded()
}

/**
 * Firebase (ringing + call doc) isn't configured in this build: outgoing calls end at once with an error,
 * incoming ones are declined on the server so the caller isn't left ringing. No simulated audio.
 */
class UnavailableCallProvider(private val api: CallApi, private val scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis) : CallProvider {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()
    override fun startCall(tripId: String, peerName: String) { _state.value = CallState.Ended(peerName, EndReason.FAILED, 0, CallError.UNKNOWN) }
    override fun continueOutgoing(tripId: String, peerName: String, started: StartedCall) { scope.launch { api.end(started.callId, EndReason.FAILED.wire) }; startCall(tripId, peerName) }
    override fun onIncoming(call: IncomingCall) { scope.launch { api.decline(call.callId) } }
    override fun accept() {}
    override fun decline() {}
    override fun hangUp() { if (_state.value !is CallState.Idle) _state.value = CallMachine.ended(_state.value, EndReason.HANGUP, now()) }
    override fun setMuted(muted: Boolean) {}
    override fun setSpeaker(on: Boolean) {}
    override fun onRemoteEnded(callId: String, status: CallStatus) {}
    override fun dismissEnded() { if (_state.value is CallState.Ended) _state.value = CallState.Idle }
}
