package com.durpalla.driver.realtime

import com.durpalla.driver.domain.PaymentMethod
import com.durpalla.driver.domain.VehicleType
import com.durpalla.driver.notifications.Channels
import com.durpalla.driver.notifications.NotificationFactory
import org.junit.Assert.*
import org.junit.Test

class EventParserTest {
    @Test fun tripRequested() {
        val json = """{"request":{"id":"r9","vehicleType":"speedboat","from":"Sadarghat","to":"Narayanganj","start":"2026-10-24T08:00:00","end":"2026-10-24T09:30:00",
            "passengers":8,"customer":{"name":"Tanvir","rating":4.7,"trips":23},"suggestedMin":2800,"suggestedMax":3500,"expiresInSec":90,"distanceKm":18.5}}"""
        val e = EventParser.parse("trip.requested", json) as RealtimeEvent.TripRequested
        assertEquals("r9", e.request.id); assertEquals(VehicleType.SPEEDBOAT, e.request.vehicleType); assertEquals(8, e.request.passengers)
        assertEquals(90, e.request.expiresInSec); assertEquals(4.7, e.request.customer.rating, 0.0); assertEquals(0, e.request.bidsCount)
        assertEquals(30, java.time.Duration.between(e.request.slot.start, e.request.slot.end).toMinutes() - 60)
    }
    @Test fun bidEvents() {
        assertEquals(RealtimeEvent.BidPlaced("r1", 4, 2900), EventParser.parse("bid.placed", """{"requestId":"r1","bidCount":4,"lowestBid":2900}"""))
        assertEquals(RealtimeEvent.BidUpdated("b1", "r1", 3000, 5, 2900), EventParser.parse("bid.updated", """{"bidId":"b1","requestId":"r1","amount":3000,"bidCount":5,"lowestBid":2900}"""))
        assertEquals(RealtimeEvent.BidAccepted("b1", "r1", "t7", "DP-1", 800), EventParser.parse("bid.accepted", """{"bidId":"b1","requestId":"r1","tripId":"t7","tripCode":"DP-1","advancePaid":800}"""))
        val rej = EventParser.parse("bid.rejected", """{"bidId":"b1","requestId":"r1"}""") as RealtimeEvent.BidRejected
        assertEquals("Customer chose another driver", rej.reason)
    }
    @Test fun tripAndPaymentEvents() {
        assertTrue(EventParser.parse("trip.cancelled", """{"tripId":"t1"}""") is RealtimeEvent.TripCancelled)
        assertTrue(EventParser.parse("slot.locked", """{"tripId":"t1","start":"2026-10-24T08:00:00Z","end":"2026-10-24T10:00:00Z"}""") is RealtimeEvent.SlotLocked)
        assertEquals(RealtimeEvent.SlotReleased("t1"), EventParser.parse("slot.released", """{"tripId":"t1"}"""))
        assertEquals(RealtimeEvent.PaymentReceived("t1", 3200, PaymentMethod.BKASH), EventParser.parse("payment.received", """{"tripId":"t1","amount":3200,"method":"bkash"}"""))
    }
    @Test fun malformedOrUnknownReturnsNull() {
        assertNull(EventParser.parse("bid.placed", """{"requestId":"r1"}"""))
        assertNull(EventParser.parse("bid.placed", "not json"))
        assertNull(EventParser.parse("pusher:ping", "{}"))
        assertNull(EventParser.parse("payment.received", """{"tripId":"t","amount":1,"method":"paypal"}"""))
    }
    @Test fun fcmEnvelope() {
        val e = EventParser.parseFcm(mapOf("event" to "bid.accepted", "payload" to """{"bidId":"b","requestId":"r","tripId":"t","tripCode":"DP-9"}"""))
        assertEquals(RealtimeEvent.BidAccepted("b", "r", "t", "DP-9", 0), e)
        assertNull(EventParser.parseFcm(mapOf("title" to "hi")))
    }
    @Test fun notificationRouting() {
        val acc = NotificationFactory.spec(RealtimeEvent.BidAccepted("b", "r", "t9", "DP-9", 0))!!
        assertEquals(Channels.BIDS, acc.channel); assertEquals("won/t9", acc.route); assertTrue(acc.urgent)
        assertEquals("wallet", NotificationFactory.spec(RealtimeEvent.PaymentReceived("t", 100, PaymentMethod.CASH))!!.route)
        assertNull(NotificationFactory.spec(RealtimeEvent.BidPlaced("r", 1, 100)))
    }
}
