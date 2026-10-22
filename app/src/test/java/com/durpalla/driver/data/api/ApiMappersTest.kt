package com.durpalla.driver.data.api

import com.durpalla.driver.R
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.UiText
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ApiMappersTest {
    private fun o(s: String) = JsonParser.parseString(s).asJsonObject

    @Test fun requestToleratesNulls() {
        val r = ApiMappers.request(o("""{"id":"5","vehicleType":null,"from":"Mawa","to":"Janjira","start":"2026-11-01T10:00:00+06:00","end":"2026-11-01T13:00:00+06:00",
            "passengers":4,"customer":{"name":"Rahim","rating":null,"trips":2},"suggestedMin":null,"suggestedMax":null,"bidsCount":1,"lowestBid":null,"expiresInSec":null,"distanceKm":12.5}"""))
        assertEquals("5", r.id); assertEquals(VehicleType.BOAT, r.vehicleType); assertEquals(12.5, r.distanceKm, 0.0)
        assertTrue(r.slot.end.isAfter(r.slot.start))
    }

    @Test fun driverHasAreaAndRegistration() {
        val d = ApiMappers.driver(o("""{"id":"3","name":"Rafiq","phone":"01711","vehicleType":"SPEEDBOAT","area":"Munshiganj","online":false,
            "vehicle":{"id":"9","title":"Sea Hawk","capacity":12,"registrationNo":"DHK-1"},"kycComplete":true,"trips":5}"""), null)
        assertEquals("Munshiganj", d.area); assertEquals("DHK-1", d.vehicles.single().reg); assertFalse(d.online)
    }

    @Test fun ratingUsesServerCustomerAndCode() {
        val r = ApiMappers.rating(o("""{"id":"1","tripId":"4","tripCode":"DP-4","customerName":"Rahim","stars":5,"comment":"","createdAt":"2026-10-01T10:00:00Z"}"""))
        assertEquals("Rahim", r.customer); assertEquals("DP-4", r.tripCode)
    }

    @Test fun predefinedRoundTrip() {
        val p = ApiMappers.predefined(o("""{"id":"2","vehicleType":"MICROBUS","from":"Dhaka","to":"Cox","departure":"07:30","days":["SAT","MON"],"seats":9,"rent":3500,"active":true}"""))
        assertEquals(setOf("SAT", "MON"), p.days)
        val body = ApiMappers.predefinedBody(p)
        assertEquals("07:30", body["departure"]); assertEquals(3500, body["rent"]); assertEquals("MICROBUS", body["vehicleType"])
    }

    @Test fun notificationTextFromEvent() {
        val n = ApiMappers.notification(o("""{"id":"u1","event":"bid.accepted","route":"trip/4","payload":{"tripCode":"DP-4"},"read":false,"createdAt":"2026-10-01T10:00:00Z"}"""))
        assertEquals(UiText.res(R.string.notif_accepted_title), n.title)
        assertEquals(UiText.res(R.string.notif_accepted_body, "DP-4"), n.body)
        assertTrue(n.unread); assertEquals("trip/4", n.route)
    }

    @Test fun payoutAccountAndBlockedDays() {
        assertEquals(WithdrawAccount(PaymentMethod.NAGAD, "018", "8"), ApiMappers.payoutAccount(o("""{"id":"8","method":"NAGAD","account":"018","isDefault":true}""")))
        assertEquals(setOf(java.time.LocalDate.of(2026, 11, 1)), ApiMappers.blockedDays(o("""{"blockedDays":["2026-11-01","bad"]}""")))
    }

    @Test fun reconciliationWithdrawals() {
        val r = ApiMappers.reconciliation(o("""{"period":"month","trips":3,"grossFare":3000,"commission":300,"withdrawn":1000,"withdrawals":2,"cashCollected":500}"""), emptyList())
        assertEquals(2, r.withdrawals); assertEquals(2700, r.net)
    }
}
