package com.durpalla.driver.call

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallProviderRouterTest {
    private class StubApi(var provider: CallProviderKind? = CallProviderKind.AGORA, var fail: CallError? = null) : CallApi {
        val ended = mutableListOf<Pair<String, String>>(); val declined = mutableListOf<String>()
        override suspend fun start(tripId: String) = fail?.let { Result.failure<StartedCall>(CallException(it)) }
            ?: Result.success(StartedCall("c-$tripId", provider ?: CallProviderKind.WEBRTC))
        override suspend fun accept(callId: String) = Result.success(StartedCall(callId, CallProviderKind.AGORA))
        override suspend fun decline(callId: String) = Result.success(Unit).also { declined += callId }
        override suspend fun end(callId: String, reason: String) = Result.success(Unit).also { ended += callId to reason }
        override suspend fun iceServers() = Result.success(IceConfig.FALLBACK)
        override suspend fun renewToken(callId: String) = Result.success(RenewedToken("007x", 0))
    }

    private fun TestScope.setup(api: StubApi): Triple<CallProviderRouter, FakeCallProvider, FakeCallProvider> {
        val agora = FakeCallProvider(backgroundScope, { testScheduler.currentTime }, 1_000)
        val webrtc = FakeCallProvider(backgroundScope, { testScheduler.currentTime }, 1_000)
        val r = CallProviderRouter(backgroundScope, api, mapOf(CallProviderKind.AGORA to agora, CallProviderKind.WEBRTC to webrtc), { testScheduler.currentTime })
        runCurrent()
        return Triple(r, agora, webrtc)
    }

    @Test fun outgoingUsesServerProvider() = runTest {
        val (r, agora, webrtc) = setup(StubApi(CallProviderKind.AGORA))
        r.startCall("t1", "Rahim"); assertTrue(r.state.value is CallState.Outgoing)
        runCurrent()
        assertEquals("c-t1", (agora.state.value as CallState.Outgoing).callId)
        assertEquals(CallState.Idle, webrtc.state.value)
        advanceTimeBy(1_001); runCurrent()
        assertEquals("c-t1", (r.state.value as CallState.Active).callId)
        r.hangUp(); runCurrent(); assertTrue(r.state.value is CallState.Ended)
    }

    @Test fun webrtcWhenServerSaysSo() = runTest {
        val (r, agora, webrtc) = setup(StubApi(CallProviderKind.WEBRTC))
        r.startCall("t2", "Karim"); runCurrent()
        assertTrue(webrtc.state.value is CallState.Outgoing); assertEquals(CallState.Idle, agora.state.value)
    }

    @Test fun incomingRoutesByPayloadAndMissingMeansWebrtc() = runTest {
        val (r, agora, webrtc) = setup(StubApi())
        r.onIncoming(IncomingCall("c1", "t1", "Rahim", CallProviderKind.AGORA)); runCurrent()
        assertTrue(agora.state.value is CallState.Incoming); assertTrue(r.state.value is CallState.Incoming)
        r.decline(); runCurrent(); r.dismissEnded(); runCurrent()
        r.onIncoming(IncomingCall("c2", "t1", "Rahim")); runCurrent()
        assertTrue(webrtc.state.value is CallState.Incoming)
    }

    @Test fun busyIncomingIsDeclined() = runTest {
        val api = StubApi(); val (r, _, _) = setup(api)
        r.onIncoming(IncomingCall("c1", "t1", "A", CallProviderKind.AGORA)); runCurrent()
        r.onIncoming(IncomingCall("c2", "t1", "B", CallProviderKind.WEBRTC)); runCurrent()
        assertEquals(listOf("c2"), api.declined)
    }

    @Test fun startFailureEndsWithError() = runTest {
        val (r, _, _) = setup(StubApi(fail = CallError.BUSY))
        r.startCall("t1", "Rahim"); runCurrent()
        assertEquals(EndReason.BUSY, (r.state.value as CallState.Ended).reason)
        r.dismissEnded(); assertEquals(CallState.Idle, r.state.value)
    }

    @Test fun pickFallsBackToWebrtc() {
        val w = FakeCallProvider(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        assertSame(w, CallProviderRouter.pick(CallProviderKind.AGORA, mapOf(CallProviderKind.WEBRTC to w)))
    }
}
