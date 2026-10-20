package com.durpalla.driver.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Picks the media provider per call from the server's `provider` field (missing → webrtc), then delegates.
 * Outgoing: POST /calls first (returns {callId, provider, agora|iceServers}), then [CallProvider.continueOutgoing] on that provider.
 * Incoming: `provider` from the FCM/Pusher `call.incoming` payload. [state] mirrors the active provider.
 */
class CallProviderRouter(
    private val scope: CoroutineScope,
    private val api: CallApi,
    private val providers: Map<CallProviderKind, CallProvider>,
    private val now: () -> Long = System::currentTimeMillis,
) : CallProvider {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()
    private var active: CallProvider? = null
    private var starting = false

    init {
        require(providers.isNotEmpty())
        providers.values.forEach { p -> scope.launch { p.state.collect { s -> if (p === active) _state.value = s } } }
    }

    private val busy get() = _state.value !is CallState.Idle && _state.value !is CallState.Ended

    private fun use(kind: CallProviderKind): CallProvider = pick(kind, providers).also { active = it; _state.value = it.state.value }

    override fun startCall(tripId: String, peerName: String) {
        if (busy || starting) return
        starting = true; active = null
        _state.value = CallState.Outgoing(tripId, peerName, null, now())
        scope.launch {
            val r = api.start(tripId)
            starting = false
            if (_state.value !is CallState.Outgoing) { // hung up while POST /calls was in flight
                r.getOrNull()?.let { api.end(it.callId, "cancelled") }; return@launch
            }
            r.onSuccess { s -> use(s.provider).continueOutgoing(tripId, peerName, s) }
                .onFailure { t -> val err = (t as? CallException)?.error ?: CallError.NETWORK
                    _state.value = CallMachine.ended(_state.value, if (err == CallError.BUSY) EndReason.BUSY else EndReason.FAILED, now(), err) }
        }
    }

    override fun continueOutgoing(tripId: String, peerName: String, started: StartedCall) = use(started.provider).continueOutgoing(tripId, peerName, started)

    override fun onIncoming(call: IncomingCall) {
        if (busy) { scope.launch { api.decline(call.callId) }; return }
        use(call.provider).onIncoming(call)
    }

    override fun accept() { active?.accept() }
    override fun decline() { active?.decline() }
    override fun hangUp() {
        val a = active
        if (a == null) { _state.value = CallMachine.ended(_state.value, EndReason.HANGUP, now()); return }
        a.hangUp()
    }
    override fun setMuted(muted: Boolean) { active?.setMuted(muted) }
    override fun setSpeaker(on: Boolean) { active?.setSpeaker(on) }
    override fun onRemoteEnded(callId: String, status: CallStatus) { active?.onRemoteEnded(callId, status) }
    override fun dismissEnded() { active?.dismissEnded() ?: run { if (_state.value is CallState.Ended) _state.value = CallState.Idle } }

    companion object {
        /** Requested provider, else webrtc, else whatever is installed. */
        fun pick(kind: CallProviderKind, providers: Map<CallProviderKind, CallProvider>): CallProvider =
            providers[kind] ?: providers[CallProviderKind.WEBRTC] ?: providers.values.first()
    }
}
