package com.durpalla.driver.realtime

import com.durpalla.driver.domain.BidStatus
import org.junit.Assert.*
import org.junit.Test

class BidReducerTest {
    private val empty = emptyMap<String, LiveBid>()

    @Test fun competitorBidsTrackCountAndLowest() {
        var s = BidReducer.reduce(empty, RealtimeEvent.BidPlaced("r1", 2, 3200))
        s = BidReducer.reduce(s, RealtimeEvent.BidPlaced("r1", 3, 3500))
        s = BidReducer.reduce(s, RealtimeEvent.BidPlaced("r1", 1, 3000)) // out-of-order count never goes down
        assertEquals(3, s["r1"]!!.bidCount); assertEquals(3000, s["r1"]!!.lowestBid)
    }
    @Test fun myBidMakesMeLowestUntilOutbid() {
        var s = BidReducer.reduce(empty, RealtimeEvent.BidPlaced("r1", 2, 3200))
        s = BidReducer.myBid(s, "r1", "b1", 3100)
        assertTrue(s["r1"]!!.iAmLowest); assertEquals(3, s["r1"]!!.bidCount); assertEquals(BidStatus.PENDING, s["r1"]!!.status)
        s = BidReducer.reduce(s, RealtimeEvent.BidPlaced("r1", 4, 2900))
        assertFalse(s["r1"]!!.iAmLowest)
    }
    @Test fun updatedAmountForMyBid() {
        var s = BidReducer.myBid(empty, "r1", "b1", 3100)
        s = BidReducer.reduce(s, RealtimeEvent.BidUpdated("b1", "r1", 2950, 3, 2950))
        assertEquals(2950, s["r1"]!!.myAmount); assertTrue(s["r1"]!!.iAmLowest)
    }
    @Test fun acceptanceWinsAndCloses() {
        var s = BidReducer.myBid(empty, "r1", "b1", 3100)
        s = BidReducer.reduce(s, RealtimeEvent.BidAccepted("b1", "r1", "t1", "DP-1", 0))
        assertEquals(BidStatus.WON, s["r1"]!!.status); assertEquals("t1", s["r1"]!!.tripId); assertTrue(s["r1"]!!.closed)
        val after = BidReducer.reduce(s, RealtimeEvent.BidPlaced("r1", 9, 100)) // closed: ignore late competitor bids
        assertEquals(s["r1"], after["r1"])
    }
    @Test fun someoneElseAcceptedMeansLost() {
        var s = BidReducer.myBid(empty, "r1", "b1", 3100)
        s = BidReducer.reduce(s, RealtimeEvent.BidAccepted("b-other", "r1", "t1", "DP-1", 0))
        assertEquals(BidStatus.LOST, s["r1"]!!.status)
    }
    @Test fun rejectionOnlyAffectsMyBid() {
        var s = BidReducer.myBid(empty, "r1", "b1", 3100)
        assertEquals(BidStatus.PENDING, BidReducer.reduce(s, RealtimeEvent.BidRejected("bX", "r1", "x"))["r1"]!!.status)
        s = BidReducer.reduce(s, RealtimeEvent.BidRejected("b1", "r1", "lower bid chosen"))
        assertEquals(BidStatus.LOST, s["r1"]!!.status)
    }
    @Test fun newRequestSeedsState() {
        val r = com.durpalla.driver.data.SampleData.requests[0]
        val s = BidReducer.reduce(empty, RealtimeEvent.TripRequested(r))
        assertEquals(r.bidsCount, s[r.id]!!.bidCount)
    }
}
