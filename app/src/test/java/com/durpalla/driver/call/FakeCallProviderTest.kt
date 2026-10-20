package com.durpalla.driver.call

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeCallProviderTest {
    private fun TestScope.provider(answer: Long = 2_000) = FakeCallProvider(backgroundScope, { testScheduler.currentTime }, answer)

    @Test fun outgoingConnectsThenHangupReportsDuration() = runTest {
        val p = provider()
        p.startCall("t1", "Rahim"); assertTrue(p.state.value is CallState.Outgoing)
        advanceTimeBy(2_001); runCurrent(); assertTrue(p.state.value is CallState.Active)
        advanceTimeBy(42_000); p.hangUp()
        assertEquals(CallState.Ended("Rahim", EndReason.HANGUP, 42), p.state.value)
        p.dismissEnded(); assertEquals(CallState.Idle, p.state.value)
    }

    @Test fun unansweredOutgoingIsMissedAfter30s() = runTest {
        val p = provider(answer = -1)
        p.startCall("t1", "Rahim"); advanceTimeBy(29_999); runCurrent(); assertTrue(p.state.value is CallState.Outgoing)
        advanceTimeBy(2); runCurrent(); assertEquals(EndReason.MISSED, (p.state.value as CallState.Ended).reason)
    }

    @Test fun incomingMissedAcceptDecline() = runTest {
        val p = provider()
        p.onIncoming(IncomingCall("c1", "t1", "Karim")); advanceTimeBy(30_001); runCurrent()
        assertEquals(EndReason.MISSED, (p.state.value as CallState.Ended).reason)
        p.onIncoming(IncomingCall("c2", "t1", "Karim")); p.accept()
        val a = p.state.value as CallState.Active; assertEquals("c2", a.callId)
        p.setMuted(true); p.setSpeaker(true); assertTrue((p.state.value as CallState.Active).muted && (p.state.value as CallState.Active).speaker)
        // A second ring while busy is ignored.
        p.onIncoming(IncomingCall("c3", "t2", "X")); assertEquals("c2", (p.state.value as CallState.Active).callId)
        p.onRemoteEnded("c2", CallStatus.ENDED); assertEquals(EndReason.REMOTE_HANGUP, (p.state.value as CallState.Ended).reason)
        p.onIncoming(IncomingCall("c4", "t1", "Karim")); p.decline(); assertEquals(EndReason.DECLINED, (p.state.value as CallState.Ended).reason)
    }
}
