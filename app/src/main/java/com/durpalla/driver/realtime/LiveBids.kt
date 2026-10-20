package com.durpalla.driver.realtime

import com.durpalla.driver.domain.BidStatus

/** Live competition for one request, as seen by this driver. */
data class LiveBid(
    val requestId: String,
    val bidCount: Int = 0,
    val lowestBid: Int? = null,
    val myBidId: String? = null,
    val myAmount: Int? = null,
    val status: BidStatus? = null,
    val tripId: String? = null,
    val closed: Boolean = false,
) {
    val iAmLowest: Boolean get() = myAmount != null && lowestBid != null && myAmount <= lowestBid
}

/** Pure reducer: (state, event) -> state. Unit-tested; used by the ViewModel for live bid UI. */
object BidReducer {
    fun reduce(state: Map<String, LiveBid>, e: RealtimeEvent): Map<String, LiveBid> = when (e) {
        is RealtimeEvent.TripRequested -> if (e.request.id in state) state else state + (e.request.id to LiveBid(e.request.id, e.request.bidsCount))
        is RealtimeEvent.BidPlaced -> state.upd(e.requestId) { if (it.closed) it else it.copy(bidCount = maxOf(it.bidCount, e.bidCount), lowestBid = minOrNew(it.lowestBid, e.lowestBid)) }
        is RealtimeEvent.BidUpdated -> state.upd(e.requestId) {
            val mine = it.myBidId == e.bidId
            it.copy(bidCount = maxOf(it.bidCount, e.bidCount), lowestBid = e.lowestBid, myAmount = if (mine) e.amount else it.myAmount)
        }
        is RealtimeEvent.BidAccepted -> state.upd(e.requestId) { it.copy(status = if (it.myBidId == null || it.myBidId == e.bidId) BidStatus.WON else BidStatus.LOST, tripId = e.tripId, closed = true) }
        is RealtimeEvent.BidRejected -> state.upd(e.requestId) { if (it.myBidId == null || it.myBidId == e.bidId) it.copy(status = BidStatus.LOST, closed = true) else it }
        else -> state
    }

    /** Record this driver's own bid (from the REST response). */
    fun myBid(state: Map<String, LiveBid>, requestId: String, bidId: String, amount: Int) = state.upd(requestId) {
        it.copy(myBidId = bidId, myAmount = amount, status = BidStatus.PENDING, bidCount = it.bidCount + 1, lowestBid = minOrNew(it.lowestBid, amount))
    }

    private fun minOrNew(a: Int?, b: Int) = if (a == null) b else minOf(a, b)
    private inline fun Map<String, LiveBid>.upd(id: String, f: (LiveBid) -> LiveBid) = this + (id to f(this[id] ?: LiveBid(id)))
}
