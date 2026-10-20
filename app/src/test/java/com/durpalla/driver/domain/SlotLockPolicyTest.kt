package com.durpalla.driver.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SlotLockPolicyTest {
    private val base = LocalDateTime.of(2026, 10, 24, 0, 0)
    private fun slot(h1: Int, h2: Int) = TimeSlot(base.plusHours(h1.toLong()), base.plusHours(h2.toLong()))
    private val customer = Customer("C", 4.5, 1)
    private fun trip(id: String, s: TimeSlot, status: TripStatus = TripStatus.UPCOMING) =
        Trip(id, id, VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", s, 4, customer, 3000, 0, status)
    private fun req(id: String, s: TimeSlot, status: RequestStatus = RequestStatus.OPEN) =
        TripRequest(id, VehicleType.SPEEDBOAT, "Sadarghat", "Narayanganj", s, 4, customer, 2800, 3500, 0, 60, status = status)

    @Test fun overlappingRequestIsBlocked() {
        val p = SlotLockPolicy()
        val result = p.check(slot(9, 11), listOf(trip("t1", slot(8, 10))))
        assertTrue(result is SlotCheck.Conflict)
        assertEquals("t1", (result as SlotCheck.Conflict).withTripId)
        assertFalse(p.canBid(req("r", slot(9, 11)), listOf(trip("t1", slot(8, 10)))))
    }

    @Test fun backToBackSlotsAreAllowedWithoutBuffer() {
        assertTrue(SlotLockPolicy().canBid(req("r", slot(10, 12)), listOf(trip("t1", slot(8, 10)))))
    }

    @Test fun bufferBlocksBackToBack() {
        assertFalse(SlotLockPolicy(bufferMinutes = 30).canBid(req("r", slot(10, 12)), listOf(trip("t1", slot(8, 10)))))
    }

    @Test fun completedOrCancelledTripsDoNotLock() {
        val p = SlotLockPolicy()
        assertTrue(p.canBid(req("r", slot(8, 10)), listOf(trip("a", slot(8, 10), TripStatus.COMPLETED), trip("b", slot(8, 10), TripStatus.CANCELLED))))
    }

    @Test fun inProgressTripLocks() {
        assertFalse(SlotLockPolicy().canBid(req("r", slot(9, 10)), listOf(trip("a", slot(8, 12), TripStatus.IN_PROGRESS))))
    }

    @Test fun closedRequestCannotBeBid() {
        assertFalse(SlotLockPolicy().canBid(req("r", slot(1, 2), RequestStatus.ASSIGNED), emptyList()))
    }

    @Test fun winningExpiresOverlappingPendingBidsOnly() {
        val p = SlotLockPolicy()
        val won = Bid("w", "r1", req("r1", slot(8, 10)), 3000, "", BidStatus.WON)
        val overlap = Bid("o", "r2", req("r2", slot(9, 11)), 3000, "", BidStatus.PENDING)
        val free = Bid("f", "r3", req("r3", slot(12, 13)), 3000, "", BidStatus.PENDING)
        val lost = Bid("l", "r4", req("r4", slot(9, 10)), 3000, "", BidStatus.LOST)
        val out = p.resolveAfterWin(won, listOf(won, overlap, free, lost)).associateBy { it.id }
        assertEquals(BidStatus.WON, out["w"]!!.status)
        assertEquals(BidStatus.EXPIRED, out["o"]!!.status)
        assertEquals(BidStatus.PENDING, out["f"]!!.status)
        assertEquals(BidStatus.LOST, out["l"]!!.status)
    }

    @Test fun bidValidationRejectsConflictsAndLowAmounts() {
        val p = SlotLockPolicy()
        val r = req("r", slot(9, 11))
        assertTrue(BidRules.validate(null, r, emptyList(), p) is BidValidation.Error)
        assertTrue(BidRules.validate(50, r, emptyList(), p) is BidValidation.Error)
        assertTrue(BidRules.validate(3000, r, listOf(trip("t", slot(8, 10))), p) is BidValidation.Error)
        assertEquals(BidValidation.Ok, BidRules.validate(3000, r, emptyList(), p))
    }

    @Test fun commissionAndReconciliationMath() {
        assertEquals(320, Commission.of(3200))
        assertEquals(2880, Commission.netOf(3200))
        val rec = Reconciliation("Oct", 42, 96400, 9640, 62000, 4, 6120, emptyList())
        assertEquals(86760, rec.net)
        assertEquals(86760 - 62000 - 6120, rec.pendingSettlement)
    }
}
