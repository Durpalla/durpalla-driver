package com.durpalla.driver.data

import com.durpalla.driver.domain.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EarningsCalcTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun trip(id: String, daysAgo: Long, fare: Int, status: TripStatus = TripStatus.COMPLETED): Trip {
        val end = today.minusDays(daysAgo).atTime(12, 0)
        return SampleData.trips[0].copy(id = id, code = "DP-$id", slot = TimeSlot(end.minusHours(2), end), fare = fare, commission = fare / 10, status = status)
    }

    @Test fun weekFromCompletedTripsOnly() {
        val api = JsonParser.parseString("""{"balance":900,"today":0,"tripsToday":1,"pendingWithdrawals":0,"nextPayout":"2026-10-25"}""").asJsonObject
        val s = EarningsCalc.summary(api, listOf(trip("1", 0, 1000), trip("2", 3, 2000), trip("3", 1, 5000, TripStatus.UPCOMING), trip("4", 9, 4000)), today)
        assertEquals(7, s.daily.size)
        assertEquals(2700, s.weekTotal); assertEquals(2, s.weekTrips); assertEquals(300, s.weekCommission)
        assertEquals(-25, s.weekChangePct) // 2700 vs 3600 previous week
        assertEquals(900, s.walletBalance); assertEquals("Sun 25 Oct", s.nextPayout)
    }

    @Test fun emptyTripsGiveZeroes() {
        val s = EarningsCalc.summary(JsonParser.parseString("{}").asJsonObject, emptyList(), today)
        assertEquals(0, s.weekTotal); assertEquals("", s.nextPayout)
    }

    @Test fun linesWithinPeriod() {
        val l = EarningsCalc.lines(listOf(trip("1", 0, 1000), trip("2", 40, 2000)), "2026-10-01", "2026-10-31")
        assertEquals(listOf("DP-1"), l.map { it.code })
    }
}
