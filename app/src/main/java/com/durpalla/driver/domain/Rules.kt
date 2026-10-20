package com.durpalla.driver.domain

/** Durpalla platform commission. */
object Commission {
    const val RATE_PERCENT = 10
    fun of(amount: Int): Int = Math.round(amount * RATE_PERCENT / 100.0).toInt()
    fun netOf(amount: Int): Int = amount - of(amount)
}

sealed interface SlotCheck {
    data object Available : SlotCheck
    data class Conflict(val withTripId: String, val withSlot: TimeSlot) : SlotCheck
}

/**
 * Slot-lock rule: once a driver is confirmed for a trip (won bid or booked pre-defined trip),
 * that time window is locked. He cannot bid on / accept any request whose slot overlaps.
 * An optional buffer (minutes) is applied around locked slots for turnaround.
 */
class SlotLockPolicy(private val bufferMinutes: Long = 0) {

    private val lockingStatuses = setOf(TripStatus.UPCOMING, TripStatus.ON_THE_WAY, TripStatus.ARRIVED, TripStatus.IN_PROGRESS)

    fun check(candidate: TimeSlot, confirmedTrips: List<Trip>): SlotCheck {
        val hit = confirmedTrips.firstOrNull { it.status in lockingStatuses && padded(it.slot).overlaps(candidate) }
        return if (hit == null) SlotCheck.Available else SlotCheck.Conflict(hit.id, hit.slot)
    }

    fun canBid(request: TripRequest, confirmedTrips: List<Trip>): Boolean =
        request.status in setOf(RequestStatus.OPEN, RequestStatus.BIDDING) &&
            check(request.slot, confirmedTrips) is SlotCheck.Available

    /**
     * When a driver wins a bid, his other pending bids that overlap the won slot are auto-withdrawn
     * (returned as EXPIRED) so the customer can't pick him twice for the same slot.
     */
    fun resolveAfterWin(won: Bid, others: List<Bid>): List<Bid> = others.map {
        if (it.id != won.id && it.status == BidStatus.PENDING && padded(won.request.slot).overlaps(it.request.slot))
            it.copy(status = BidStatus.EXPIRED) else it
    }

    private fun padded(s: TimeSlot) =
        if (bufferMinutes == 0L) s else TimeSlot(s.start.minusMinutes(bufferMinutes), s.end.plusMinutes(bufferMinutes))
}

sealed interface BidValidation {
    data object Ok : BidValidation
    data class Error(val message: String) : BidValidation
}

object BidRules {
    const val MIN_BID = 100
    fun validate(amount: Int?, request: TripRequest, confirmedTrips: List<Trip>, policy: SlotLockPolicy): BidValidation = when {
        amount == null -> BidValidation.Error("Enter a rent amount")
        amount < MIN_BID -> BidValidation.Error("Minimum bid is ৳$MIN_BID")
        amount > request.suggestedMax * 3 -> BidValidation.Error("Amount is far above the suggested range")
        policy.check(request.slot, confirmedTrips) is SlotCheck.Conflict -> BidValidation.Error("Slot booked: you already have a trip in this time")
        else -> BidValidation.Ok
    }
}
