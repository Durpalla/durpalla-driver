package com.durpalla.driver.call

/** What the call UI shows. One call at a time. */
sealed interface CallState {
    data object Idle : CallState
    data class Outgoing(val tripId: String, val peerName: String, val callId: String? = null, val since: Long) : CallState
    data class Incoming(val call: IncomingCall, val since: Long) : CallState
    data class Connecting(val callId: String, val tripId: String, val peerName: String) : CallState
    data class Active(val callId: String, val tripId: String, val peerName: String, val startedAt: Long, val muted: Boolean = false, val speaker: Boolean = false, val weak: Boolean = false) : CallState
    data class Ended(val peerName: String, val reason: EndReason, val durationSec: Int = 0, val error: CallError? = null) : CallState
}

enum class EndReason(val wire: String) { HANGUP("hangup"), REMOTE_HANGUP("remote_hangup"), DECLINED("declined"), MISSED("missed"), FAILED("failed"), BUSY("busy") }

/** Pure transitions + the 30 s missed timeout, shared by Fake and WebRTC providers (and unit-tested). */
object CallMachine {
    const val RING_TIMEOUT_MS = 30_000L

    fun ringTimedOut(s: CallState, now: Long): Boolean = when (s) {
        is CallState.Outgoing -> now - s.since >= RING_TIMEOUT_MS
        is CallState.Incoming -> now - s.since >= RING_TIMEOUT_MS
        else -> false
    }

    fun peer(s: CallState) = when (s) {
        is CallState.Outgoing -> s.peerName; is CallState.Incoming -> s.call.callerName
        is CallState.Connecting -> s.peerName; is CallState.Active -> s.peerName; is CallState.Ended -> s.peerName; CallState.Idle -> ""
    }

    fun ended(s: CallState, reason: EndReason, now: Long, error: CallError? = null): CallState =
        if (s is CallState.Idle || s is CallState.Ended) s
        else CallState.Ended(peer(s), reason, if (s is CallState.Active) ((now - s.startedAt) / 1000).toInt() else 0, error)

    /** Remote doc status → local state. */
    fun onRemoteStatus(s: CallState, status: CallStatus, now: Long, iAmCaller: Boolean): CallState = when (status) {
        CallStatus.RINGING -> s
        CallStatus.ACCEPTED -> s // media connection decides Active
        CallStatus.DECLINED -> ended(s, EndReason.DECLINED, now)
        CallStatus.MISSED -> ended(s, EndReason.MISSED, now)
        CallStatus.ENDED -> ended(s, if (s is CallState.Active) EndReason.REMOTE_HANGUP else if (iAmCaller) EndReason.DECLINED else EndReason.MISSED, now)
    }
}
