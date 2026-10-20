package com.durpalla.driver.call

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CallContractTest {
    @Test fun startBodyIsTripIdOnly() {
        val o = JsonParser.parseString(CallJson.start("t1")).asJsonObject
        assertEquals(setOf("tripId"), o.keySet()); assertEquals("t1", o["tripId"].asString)
        assertEquals("c9", CallJson.callId("""{"callId":"c9"}"""))
    }

    @Test fun iceServersParseArrayAndObjectForms() {
        val a = CallJson.iceServers("""[{"urls":["turn:t.durpalla.com:3478"],"username":"u","credential":"p"},{"urls":"stun:s.durpalla.com"}]""")
        assertEquals(listOf("turn:t.durpalla.com:3478"), a.servers[0].urls); assertEquals("u", a.servers[0].username); assertEquals("p", a.servers[0].credential)
        assertEquals(listOf("stun:s.durpalla.com"), a.servers[1].urls)
        val o = CallJson.iceServers("""{"iceServers":[{"urls":["turn:x"],"username":"u","credential":"p"}],"ttl":3600}""")
        assertEquals(3600L, o.ttlSeconds)
    }

    @Test fun emptyIceFallsBackToGoogleStun() {
        assertEquals("stun:stun.l.google.com:19302", CallJson.iceServers("[]").servers.single().urls.single())
        assertEquals("stun:stun.l.google.com:19302", IceConfig.FALLBACK.servers.single().urls.single())
    }

    @Test fun errorCodesMap() {
        assertEquals(CallError.BUSY, CallJson.error(409, """{"error":{"code":"CALL_BUSY","message":"x"}}"""))
        assertEquals(CallError.TRIP_NOT_ACTIVE, CallJson.error(422, """{"error":{"code":"CALL_TRIP_NOT_ACTIVE"}}"""))
        assertEquals(CallError.UNAUTHORIZED, CallJson.error(401, "nope"))
        assertEquals(CallError.RATE_LIMITED, CallJson.error(429, null))
        assertEquals(CallError.UNKNOWN, CallJson.error(500, "{}"))
    }

    @Test fun callDocUsesContractFieldNames() {
        val m = mapOf<String, Any?>("callId" to "c1", "tripId" to "t1", "callerId" to "d1", "callerRole" to "driver", "calleeId" to "u1", "calleeRole" to "customer",
            "offer" to mapOf("type" to "offer", "sdp" to "v=0"), "answer" to mapOf("type" to "answer", "sdp" to "v=1"), "status" to "accepted",
            "createdAt" to 1L, "answeredAt" to 2L, "endedAt" to 3L, "endedBy" to "u1", "reason" to "hangup", "provider" to "agora")
        assertEquals(CallDoc.FIELDS.toSet(), m.keys)
        val d = CallDoc.from(m)
        assertEquals(CallRole.DRIVER, d.callerRole); assertEquals(CallRole.CUSTOMER, d.calleeRole); assertEquals(CallStatus.ACCEPTED, d.status)
        assertEquals("v=0", d.offer?.sdp); assertEquals("answer", d.answer?.type); assertEquals(3L, d.endedAt); assertEquals("hangup", d.reason)
        assertEquals(listOf("ringing", "accepted", "declined", "ended", "missed"), CallStatus.entries.map { it.wire })
        assertEquals("callerCandidates", CallDoc.CALLER_CANDIDATES); assertEquals("calleeCandidates", CallDoc.CALLEE_CANDIDATES)
    }

    @Test fun candidateRoundTrip() {
        val c = IceCandidateDto("candidate:1 1 udp 1 1.2.3.4 5 typ host", "0", 0)
        assertEquals(setOf("candidate", "sdpMid", "sdpMLineIndex"), c.toMap().keys)
        assertEquals(c, IceCandidateDto.from(c.toMap()))
        assertNull(IceCandidateDto.from(mapOf("sdpMid" to "0")))
    }

    @Test fun fcmIncomingFlatAndPayload() {
        assertEquals(IncomingCall("c1", "t1", "Rahim"), IncomingCall.fromFcm(mapOf("event" to "call.incoming", "callId" to "c1", "tripId" to "t1", "callerName" to "Rahim")))
        assertEquals(IncomingCall("c2", "t2", "করিম"), IncomingCall.fromFcm(mapOf("event" to "call.incoming", "payload" to """{"callId":"c2","tripId":"t2","callerName":"করিম"}""")))
        assertNull(IncomingCall.fromFcm(mapOf("event" to "bid.accepted", "callId" to "c1", "tripId" to "t")))
        assertNull(IncomingCall.fromFcm(mapOf("event" to "call.incoming", "tripId" to "t")))
    }

    @Test fun missedTimeoutIs30s() {
        val s = CallState.Outgoing("t", "R", "c", since = 1_000)
        assertFalse(CallMachine.ringTimedOut(s, 30_999)); assertTrue(CallMachine.ringTimedOut(s, 31_000))
        assertFalse(CallMachine.ringTimedOut(CallState.Active("c", "t", "R", 0), 99_999))
    }

    @Test fun remoteStatusTransitions() {
        val active = CallState.Active("c", "t", "R", startedAt = 0)
        assertEquals(CallState.Ended("R", EndReason.REMOTE_HANGUP, 65), CallMachine.onRemoteStatus(active, CallStatus.ENDED, 65_000, true))
        val out = CallState.Outgoing("t", "R", "c", 0)
        assertEquals(EndReason.DECLINED, (CallMachine.onRemoteStatus(out, CallStatus.DECLINED, 1, true) as CallState.Ended).reason)
        assertEquals(EndReason.MISSED, (CallMachine.onRemoteStatus(out, CallStatus.MISSED, 1, true) as CallState.Ended).reason)
        assertEquals(out, CallMachine.onRemoteStatus(out, CallStatus.RINGING, 1, true))
        assertEquals(CallState.Idle, CallMachine.ended(CallState.Idle, EndReason.HANGUP, 1))
    }
}
