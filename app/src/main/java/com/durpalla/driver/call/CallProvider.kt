package com.durpalla.driver.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Calling backend. [FakeCallProvider] when Firebase isn't configured, [WebRtcCallProvider] otherwise. */
interface CallProvider {
    val state: StateFlow<CallState>
    fun startCall(tripId: String, peerName: String)
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

/** Offline simulator: outgoing rings 2 s then connects; incoming rings until accept/decline or the 30 s missed timeout. */
class FakeCallProvider(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val answerAfterMs: Long = 2_000,
) : CallProvider {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()
    private var job: Job? = null
    private var n = 0

    private fun ringTimeout() { job?.cancel(); job = scope.launch { delay(CallMachine.RING_TIMEOUT_MS); if (CallMachine.ringTimedOut(_state.value, now())) _state.value = CallMachine.ended(_state.value, EndReason.MISSED, now()) } }

    override fun startCall(tripId: String, peerName: String) {
        if (_state.value !is CallState.Idle && _state.value !is CallState.Ended) return
        val id = "fake-call-${++n}"
        _state.value = CallState.Outgoing(tripId, peerName, id, now())
        job?.cancel()
        job = scope.launch {
            if (answerAfterMs in 0 until CallMachine.RING_TIMEOUT_MS) { delay(answerAfterMs); val s = _state.value
                if (s is CallState.Outgoing) _state.value = CallState.Active(id, tripId, peerName, now()) }
            else { delay(CallMachine.RING_TIMEOUT_MS); _state.value = CallMachine.ended(_state.value, EndReason.MISSED, now()) }
        }
    }
    override fun onIncoming(call: IncomingCall) {
        if (_state.value !is CallState.Idle && _state.value !is CallState.Ended) return
        _state.value = CallState.Incoming(call, now()); ringTimeout()
    }
    override fun accept() { val s = _state.value as? CallState.Incoming ?: return; job?.cancel()
        _state.value = CallState.Active(s.call.callId, s.call.tripId, s.call.callerName, now()) }
    override fun decline() { job?.cancel(); _state.value = CallMachine.ended(_state.value, EndReason.DECLINED, now()) }
    override fun hangUp() { job?.cancel(); _state.value = CallMachine.ended(_state.value, EndReason.HANGUP, now()) }
    override fun setMuted(muted: Boolean) { (_state.value as? CallState.Active)?.let { _state.value = it.copy(muted = muted) } }
    override fun setSpeaker(on: Boolean) { (_state.value as? CallState.Active)?.let { _state.value = it.copy(speaker = on) } }
    override fun onRemoteEnded(callId: String, status: CallStatus) { job?.cancel(); _state.value = CallMachine.onRemoteStatus(_state.value, status, now(), _state.value is CallState.Outgoing) }
    override fun dismissEnded() { if (_state.value is CallState.Ended) _state.value = CallState.Idle }
}
