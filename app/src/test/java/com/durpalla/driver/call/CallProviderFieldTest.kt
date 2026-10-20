package com.durpalla.driver.call

import org.junit.Assert.*
import org.junit.Test

class CallProviderFieldTest {
    @Test fun providerParsing() {
        assertEquals(CallProviderKind.AGORA, CallProviderKind.of("agora"))
        assertEquals(CallProviderKind.AGORA, CallProviderKind.of(" AGORA "))
        assertEquals(CallProviderKind.WEBRTC, CallProviderKind.of("webrtc"))
        assertEquals(CallProviderKind.WEBRTC, CallProviderKind.of(null))
        assertEquals(CallProviderKind.WEBRTC, CallProviderKind.of("zoom"))
    }

    @Test fun startResponse() {
        assertEquals(StartedCall("9", CallProviderKind.AGORA), CallJson.started("""{"callId":"9","provider":"agora"}"""))
        assertEquals(StartedCall("9", CallProviderKind.AGORA), CallJson.started("""{"success":true,"data":{"callId":"9","provider":"agora"}}"""))
        assertEquals(StartedCall("9", CallProviderKind.WEBRTC), CallJson.started("""{"callId":"9"}"""))
    }

    @Test fun agoraTokenResponse() {
        val t = CallJson.agoraToken("""{"data":{"appId":"a1","channel":"call_9","token":"007abc","uid":2000000007,"expiresAt":1790000000}}""")
        assertEquals(AgoraToken("a1", "call_9", "007abc", 2000000007, 1790000000), t)
    }

    @Test fun fcmAndDocCarryProvider() {
        assertEquals(CallProviderKind.AGORA, IncomingCall.fromFcm(mapOf("type" to "call.incoming", "callId" to "c", "tripId" to "1", "provider" to "agora"))!!.provider)
        assertEquals(CallProviderKind.AGORA, IncomingCall.fromFcm(mapOf("event" to "call.incoming", "payload" to """{"callId":"c","tripId":"1","provider":"agora"}"""))!!.provider)
        assertEquals(CallProviderKind.WEBRTC, IncomingCall.fromFcm(mapOf("event" to "call.incoming", "callId" to "c", "tripId" to "1"))!!.provider)
        assertEquals(CallProviderKind.AGORA, CallDoc.from(mapOf("callId" to "c", "provider" to "agora")).provider)
        assertEquals(CallProviderKind.WEBRTC, CallDoc.from(mapOf("callId" to "c")).provider)
        assertTrue("provider" in CallDoc.FIELDS)
    }

    @Test fun agoraWeakHint() {
        val q = AgoraQuality()
        q.onNetworkQuality(1, 2); assertFalse(q.weak)
        q.onNetworkQuality(4, 1); assertTrue(q.weak)
        repeat(2) { q.onRtcStats(0, 1, 100) }; assertTrue(q.weak)
        q.onRtcStats(0, 1, 100); assertFalse(q.weak)
        q.onRtcStats(12, 0, 100); assertTrue(q.weak)
        q.onNetworkQuality(0, 0); assertTrue(q.weak) // unknown is ignored
    }
}
