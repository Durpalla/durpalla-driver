package com.durpalla.driver.call

import androidx.annotation.StringRes
import com.durpalla.driver.R

/**
 * Wire model for in-app calling (WebRTC audio + Firestore signaling). Field names are the contract in
 * docs/calling-contract.md and are shared with the customer apps; do not rename.
 */
enum class CallStatus(val wire: String) {
    RINGING("ringing"), ACCEPTED("accepted"), DECLINED("declined"), ENDED("ended"), MISSED("missed");
    val terminal get() = this == DECLINED || this == ENDED || this == MISSED
    companion object { fun of(s: String?) = entries.firstOrNull { it.wire == s } }
}

enum class CallRole(val wire: String) { DRIVER("driver"), CUSTOMER("customer"); companion object { fun of(s: String?) = entries.firstOrNull { it.wire == s } } }

/** {type, sdp}; type is "offer" or "answer". */
data class Sdp(val type: String, val sdp: String) {
    fun toMap() = mapOf("type" to type, "sdp" to sdp)
    companion object { fun from(m: Any?): Sdp? { val x = m as? Map<*, *> ?: return null; return Sdp(x["type"] as? String ?: return null, x["sdp"] as? String ?: return null) } }
}

/** Docs in calls/{callId}/callerCandidates and calls/{callId}/calleeCandidates. */
data class IceCandidateDto(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) {
    fun toMap() = mapOf("candidate" to candidate, "sdpMid" to sdpMid, "sdpMLineIndex" to sdpMLineIndex)
    companion object {
        fun from(m: Map<String, Any?>): IceCandidateDto? {
            val c = m["candidate"] as? String ?: return null
            return IceCandidateDto(c, m["sdpMid"] as? String, (m["sdpMLineIndex"] as? Number)?.toInt() ?: 0)
        }
    }
}

/** Firestore doc calls/{callId}. Timestamps are epoch millis on the client (Firestore Timestamp on the wire). */
data class CallDoc(
    val callId: String,
    val tripId: String,
    val callerId: String,
    val callerRole: CallRole?,
    val calleeId: String,
    val calleeRole: CallRole?,
    val offer: Sdp? = null,
    val answer: Sdp? = null,
    val status: CallStatus = CallStatus.RINGING,
    val createdAt: Long? = null,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val endedBy: String? = null,
    val reason: String? = null,
) {
    companion object {
        const val COLLECTION = "calls"
        const val CALLER_CANDIDATES = "callerCandidates"
        const val CALLEE_CANDIDATES = "calleeCandidates"
        val FIELDS = listOf("callId", "tripId", "callerId", "callerRole", "calleeId", "calleeRole", "offer", "answer", "status", "createdAt", "answeredAt", "endedAt", "endedBy", "reason")

        /** [ts] converts a Firestore Timestamp/Date/Number to millis (kept injectable so this parses in plain JVM tests). */
        fun from(m: Map<String, Any?>, ts: (Any?) -> Long? = { (it as? Number)?.toLong() }) = CallDoc(
            callId = m["callId"] as? String ?: "", tripId = m["tripId"] as? String ?: "",
            callerId = m["callerId"] as? String ?: "", callerRole = CallRole.of(m["callerRole"] as? String),
            calleeId = m["calleeId"] as? String ?: "", calleeRole = CallRole.of(m["calleeRole"] as? String),
            offer = Sdp.from(m["offer"]), answer = Sdp.from(m["answer"]),
            status = CallStatus.of(m["status"] as? String) ?: CallStatus.RINGING,
            createdAt = ts(m["createdAt"]), answeredAt = ts(m["answeredAt"]), endedAt = ts(m["endedAt"]),
            endedBy = m["endedBy"] as? String, reason = m["reason"] as? String,
        )
    }
}

/** GET /calls/ice-servers item: {urls, username, credential}. */
data class IceServerDto(val urls: List<String>, val username: String? = null, val credential: String? = null)
data class IceConfig(val servers: List<IceServerDto>, val ttlSeconds: Long) {
    companion object {
        const val FALLBACK_STUN = "stun:stun.l.google.com:19302"
        val FALLBACK = IceConfig(listOf(IceServerDto(listOf(FALLBACK_STUN))), 0)
    }
}

/** FCM data message `call.incoming`. */
data class IncomingCall(val callId: String, val tripId: String, val callerName: String) {
    companion object {
        const val EVENT = "call.incoming"
        fun fromFcm(data: Map<String, String>): IncomingCall? {
            if (data["event"] != EVENT && data["type"] != EVENT) return null
            val payload = data["payload"]?.let { runCatching { com.google.gson.JsonParser.parseString(it).asJsonObject }.getOrNull() }
            fun f(k: String) = data[k] ?: payload?.get(k)?.takeIf { !it.isJsonNull }?.asString
            return IncomingCall(f("callId") ?: return null, f("tripId") ?: return null, f("callerName").orEmpty())
        }
    }
}

/** Error codes returned as {"error":{"code","message"}} by the calls endpoints. */
enum class CallError(val code: String, @StringRes val res: Int) {
    UNAUTHORIZED("UNAUTHORIZED", R.string.call_err_unauthorized),
    TRIP_NOT_FOUND("CALL_TRIP_NOT_FOUND", R.string.call_err_trip),
    NOT_PARTICIPANT("CALL_NOT_PARTICIPANT", R.string.call_err_trip),
    TRIP_NOT_ACTIVE("CALL_TRIP_NOT_ACTIVE", R.string.call_err_trip_inactive),
    BUSY("CALL_BUSY", R.string.call_err_busy),
    NOT_FOUND("CALL_NOT_FOUND", R.string.call_err_gone),
    ALREADY_ENDED("CALL_ALREADY_ENDED", R.string.call_err_gone),
    RATE_LIMITED("RATE_LIMITED", R.string.call_err_rate),
    NETWORK("NETWORK", R.string.call_err_network),
    UNKNOWN("UNKNOWN", R.string.call_err_generic);
    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: UNKNOWN }
}

class CallException(val error: CallError, message: String? = null) : Exception(message ?: error.code)

/** Call-log entry the backend attaches to a trip (trip.callLog[]). */
data class CallLogEntry(
    val callId: String, val callerRole: CallRole?, val status: CallStatus,
    val createdAt: Long, val answeredAt: Long? = null, val endedAt: Long? = null,
    val durationSec: Int = 0, val endedBy: String? = null, val reason: String? = null,
)
