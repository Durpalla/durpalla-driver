package com.durpalla.driver.realtime

import com.durpalla.driver.domain.Customer
import com.durpalla.driver.domain.PaymentMethod
import com.durpalla.driver.domain.TimeSlot
import com.durpalla.driver.domain.TripRequest
import com.durpalla.driver.domain.VehicleType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDateTime

/** Events delivered on `private-driver.{driverId}` and `private-trip.{tripId}` (Pusher) or via FCM data messages. */
sealed interface RealtimeEvent {
    data class TripRequested(val request: TripRequest) : RealtimeEvent
    /** Another driver bid. Identity is never sent, only aggregate competition. */
    data class BidPlaced(val requestId: String, val bidCount: Int, val lowestBid: Int) : RealtimeEvent
    data class BidUpdated(val bidId: String, val requestId: String, val amount: Int, val bidCount: Int, val lowestBid: Int) : RealtimeEvent
    data class BidAccepted(val bidId: String, val requestId: String, val tripId: String, val tripCode: String, val advancePaid: Int) : RealtimeEvent
    data class BidRejected(val bidId: String, val requestId: String, val reason: String) : RealtimeEvent
    data class TripCancelled(val tripId: String, val reason: String) : RealtimeEvent
    data class SlotLocked(val tripId: String, val slot: TimeSlot) : RealtimeEvent
    data class SlotReleased(val tripId: String) : RealtimeEvent
    data class PaymentReceived(val tripId: String, val amount: Int, val method: PaymentMethod) : RealtimeEvent
}

enum class ConnectionState(val label: String) { CONNECTING("Connecting"), LIVE("Live"), RECONNECTING("Reconnecting"), OFFLINE("Offline") }

object EventNames {
    const val TRIP_REQUESTED = "trip.requested"; const val BID_PLACED = "bid.placed"; const val BID_UPDATED = "bid.updated"
    const val BID_ACCEPTED = "bid.accepted"; const val BID_REJECTED = "bid.rejected"; const val TRIP_CANCELLED = "trip.cancelled"
    const val SLOT_LOCKED = "slot.locked"; const val SLOT_RELEASED = "slot.released"; const val PAYMENT_RECEIVED = "payment.received"
    val ALL = listOf(TRIP_REQUESTED, BID_PLACED, BID_UPDATED, BID_ACCEPTED, BID_REJECTED, TRIP_CANCELLED, SLOT_LOCKED, SLOT_RELEASED, PAYMENT_RECEIVED)
}

/** Parses `(eventName, jsonPayload)` into a [RealtimeEvent]. Returns null for unknown/malformed events (never throws). */
object EventParser {
    fun parse(name: String, json: String): RealtimeEvent? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        when (name) {
            EventNames.TRIP_REQUESTED -> RealtimeEvent.TripRequested(com.durpalla.driver.data.api.ApiMappers.request(o.obj("request")))
            EventNames.BID_PLACED -> RealtimeEvent.BidPlaced(o.str("requestId"), o.int("bidCount"), o.intOr("lowestBid", 0))
            EventNames.BID_UPDATED -> RealtimeEvent.BidUpdated(o.str("bidId"), o.str("requestId"), o.int("amount"), o.int("bidCount"), o.intOr("lowestBid", 0))
            EventNames.BID_ACCEPTED -> RealtimeEvent.BidAccepted(o.str("bidId"), o.str("requestId"), o.str("tripId"), o.str("tripCode"), o.intOr("advancePaid", 0))
            EventNames.BID_REJECTED -> RealtimeEvent.BidRejected(o.str("bidId"), o.str("requestId"), o.strOr("reason", "Customer chose another driver"))
            EventNames.TRIP_CANCELLED -> RealtimeEvent.TripCancelled(o.str("tripId"), o.strOr("reason", "Cancelled by customer"))
            EventNames.SLOT_LOCKED -> RealtimeEvent.SlotLocked(o.str("tripId"), TimeSlot(time(o.str("start")), time(o.str("end"))))
            EventNames.SLOT_RELEASED -> RealtimeEvent.SlotReleased(o.str("tripId"))
            EventNames.PAYMENT_RECEIVED -> RealtimeEvent.PaymentReceived(o.str("tripId"), o.int("amount"), PaymentMethod.valueOf(o.strOr("method", "CASH").uppercase()))
            else -> null
        }
    }.getOrNull()

    /** FCM data messages carry `event` + `payload` (JSON string). */
    fun parseFcm(data: Map<String, String>): RealtimeEvent? = data["event"]?.let { e -> data["payload"]?.let { parse(e, it) } }

    private fun time(s: String): LocalDateTime = com.durpalla.driver.data.api.ApiMappers.time(s) ?: error("bad time $s")
    private fun JsonObject.obj(k: String) = getAsJsonObject(k) ?: error("missing $k")
    private fun JsonObject.str(k: String): String = get(k)?.takeUnless { it.isJsonNull }?.asString ?: error("missing $k")
    private fun JsonObject.strOr(k: String, d: String) = get(k)?.takeUnless { it.isJsonNull }?.asString ?: d
    private fun JsonObject.int(k: String): Int = get(k)?.takeUnless { it.isJsonNull }?.asInt ?: error("missing $k")
    private fun JsonObject.intOr(k: String, d: Int) = get(k)?.takeUnless { it.isJsonNull }?.asInt ?: d
}
